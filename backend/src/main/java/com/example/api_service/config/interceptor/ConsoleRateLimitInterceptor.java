package com.example.api_service.config.interceptor;

import com.example.api_service.common.ErrorCode;
import com.example.api_service.security.AuthResponseWriter;
import com.example.api_service.security.ClientIpUtils;
import com.example.api_service.security.RedisRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Set;

/**
 * 控制台轨道（{@code /api/console/**}）分布式限流拦截器，基于 Redis 固定窗口计数。
 *
 * <p>对匿名认证接口（登录/注册/发码）施以更严格的每 IP 频率限制，
 * 阻止凭据填充、验证码爆破与邮件轰炸；对其余控制台接口施以宽松限制，
 * 防止脚本打满 CPU/DB/Redis。</p>
 *
 * <p>限流器跨实例共享（Redis），横向扩容后仍生效。</p>
 */
@Slf4j
@Component("consoleRateLimitInterceptor")
public class ConsoleRateLimitInterceptor implements HandlerInterceptor {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/console/auth/login",
            "/api/console/auth/register",
            "/api/console/auth/send-email-code",
            "/api/console/auth/forgot-password",
            "/api/console/auth/reset-password",
            "/api/console/auth/reset-password/verify");

    private final RedisRateLimiter rateLimiter;
    private final AuthResponseWriter responseWriter;
    private final ClientIpUtils clientIpUtils;
    private final int authIpLimitPerMinute;
    private final int ipLimitPerMinute;

    public ConsoleRateLimitInterceptor(
            RedisRateLimiter rateLimiter,
            AuthResponseWriter responseWriter,
            ClientIpUtils clientIpUtils,
            @Value("${app.ratelimit.console.auth-ip-per-minute:20}") int authIpLimitPerMinute,
            @Value("${app.ratelimit.console.ip-per-minute:120}") int ipLimitPerMinute) {
        this.rateLimiter = rateLimiter;
        this.responseWriter = responseWriter;
        this.clientIpUtils = clientIpUtils;
        this.authIpLimitPerMinute = authIpLimitPerMinute;
        this.ipLimitPerMinute = ipLimitPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String ip = clientIpUtils.resolve(request);
        boolean isAuthPath = AUTH_PATHS.contains(request.getRequestURI());
        String bucket = isAuthPath
                ? "console:auth:ip:" + ip
                : "console:ip:" + ip;
        int limit = isAuthPath ? authIpLimitPerMinute : ipLimitPerMinute;

        if (!rateLimiter.allowFixedWindow(bucket, limit, WINDOW)) {
            log.debug("控制台限流触发: ip={}, path={}, limit={}/min", ip, request.getRequestURI(), limit);
            responseWriter.write(response, ErrorCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，请稍后再试");
            return false;
        }
        return true;
    }
}
