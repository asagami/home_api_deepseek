package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.dto.auth.LoginRequest;
import com.example.api_service.model.dto.auth.RegisterRequest;
import com.example.api_service.model.dto.auth.SendEmailCodeRequest;
import com.example.api_service.model.vo.auth.LoginVO;
import com.example.api_service.model.vo.auth.UserProfileVO;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.AuthService;
import com.example.api_service.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/auth")
@RequiredArgsConstructor
@Tag(name = "Console - Auth", description = "开发者账号注册、登录与个人信息")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @PostMapping("/send-email-code")
    @Operation(summary = "发送邮箱验证码", description = "验证码 5 分钟内有效，同一邮箱在有效期内不可重复发送")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
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
