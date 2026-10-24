package com.example.api_service.security;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;

/**
 * 当前请求的控制台用户上下文，由 {@link JwtAuthInterceptor} 写入，
 * 请求结束后必须调用 {@link #clear()} 防止线程复用导致身份串号。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    public static UserPrincipal getRequired() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static Long getUserId() {
        return getRequired().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
