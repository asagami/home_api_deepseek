package com.example.api_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端 IP 解析工具，供各限流拦截器共用。
 *
 * <p>优先取 X-Forwarded-For 首段（信任前置反向代理已正确设置），
 * 回退到 {@link HttpServletRequest#getRemoteAddr()}。</p>
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
