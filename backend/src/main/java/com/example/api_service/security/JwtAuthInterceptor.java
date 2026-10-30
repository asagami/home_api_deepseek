package com.example.api_service.security;

import com.example.api_service.common.ErrorCode;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.service.SysUserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 控制台接口 JWT 鉴权拦截器，仅拦截 /api/console/**。
 * 校验通过后将 {@link UserPrincipal} 写入 {@link UserContext}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final SysUserService sysUserService;
    private final AuthResponseWriter responseWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            responseWriter.write(response, ErrorCode.TOKEN_MISSING,
                    "缺少 Authorization 请求头，格式应为 Bearer <token>");
            return false;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            responseWriter.write(response, ErrorCode.TOKEN_MISSING,
                    "缺少 Authorization 请求头，格式应为 Bearer <token>");
            return false;
        }

        JwtClaims claims;
        try {
            claims = jwtUtils.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败: {}", ex.getMessage());
            responseWriter.write(response, ErrorCode.TOKEN_INVALID, "访问令牌无效或已过期");
            return false;
        }

        SysUser user = sysUserService.getById(claims.userId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            responseWriter.write(response, ErrorCode.ACCOUNT_DISABLED, "账户不存在或已被封禁");
            return false;
        }

        // 会话版本不一致说明密码已被重置，历史令牌全部作废
        int tokenVersion = claims.tokenVersion() == null ? 0 : claims.tokenVersion();
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion != currentVersion) {
            responseWriter.write(response, ErrorCode.TOKEN_INVALID, "访问令牌已失效，请重新登录");
            return false;
        }

        UserContext.set(new UserPrincipal(user.getId(), user.getUsername(), user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
