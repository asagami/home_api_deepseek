package com.example.api_service.controller;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.auth.LoginRequest;
import com.example.api_service.model.dto.auth.RegisterRequest;
import com.example.api_service.model.dto.auth.SendEmailCodeRequest;
import com.example.api_service.model.vo.auth.LoginVO;
import com.example.api_service.model.vo.auth.UserProfileVO;
import com.example.api_service.security.ClientIpUtils;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.security.TurnstileVerifier;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.AuthService;
import com.example.api_service.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/console/auth")
@RequiredArgsConstructor
@Tag(name = "Console - Auth", description = "开发者账号注册、登录与个人信息")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;
    private final TurnstileVerifier turnstileVerifier;
    private final RedisRateLimiter rateLimiter;
    private final ClientIpUtils clientIpUtils;

    /** 发码接口独立的每 IP 分钟级上限，与控制台全局限流叠加，收紧邮件轰炸空间。 */
    @Value("${app.ratelimit.email-code.ip-per-minute:5}")
    private int emailCodeIpLimitPerMinute;

    @PostMapping("/send-email-code")
    @Operation(summary = "发送邮箱验证码",
            description = "人机校验通过后发送；验证码 5 分钟内有效，同一邮箱 60 秒内不可重复发送，"
                    + "校验错误累计 5 次验证码作废")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request,
                                      HttpServletRequest httpRequest) {
        String ip = clientIpUtils.resolve(httpRequest);

        // 独立 per-IP 频控先于人机校验执行：先挡量，再花钱调 siteverify
        if (!rateLimiter.allowFixedWindow("console:email-code:ip:" + ip,
                emailCodeIpLimitPerMinute, Duration.ofMinutes(1))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }

        turnstileVerifier.verify(request.captchaToken(), ip);
        emailService.sendVerifyCode(request.email());
        return Result.success();
    }

    @PostMapping("/register")
    @Operation(summary = "注册", description = "校验邮箱验证码后使用 BCrypt 保存密码，并初始化 1000 次免费额度")
    public Result<UserProfileVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "校验用户名密码后签发 JWT")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户信息", description = "返回当前登录用户信息与剩余额度")
    public Result<UserProfileVO> me() {
        return Result.success(authService.getProfile(UserContext.getUserId()));
    }
}
