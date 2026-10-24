package com.example.api_service.security;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;

/**
 * 当前请求的 API Key 调用方上下文，由 {@link ApiKeyAuthInterceptor} 写入。
 */
public final class ApiKeyContext {

    private static final ThreadLocal<ApiKeyPrincipal> HOLDER = new ThreadLocal<>();

    private ApiKeyContext() {
    }

    public static void set(ApiKeyPrincipal principal) {
        HOLDER.set(principal);
    }

    public static ApiKeyPrincipal get() {
        return HOLDER.get();
    }

    public static ApiKeyPrincipal getRequired() {
        ApiKeyPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static Long getUserId() {
        return getRequired().userId();
    }

    public static Long getApiKeyId() {
        return getRequired().apiKeyId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
