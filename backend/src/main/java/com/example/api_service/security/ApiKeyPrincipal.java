package com.example.api_service.security;

/**
 * 通过 API Key 鉴权的调用方身份。
 *
 * @param endpointId   本次请求匹配到的服务目录 ID
 * @param costPoints   本次调用按专属折扣计算出的实际扣费点数
 */
public record ApiKeyPrincipal(
        Long userId,
        Long apiKeyId,
        String keyPrefix,
        int rateLimitQps,
        Long endpointId,
        int costPoints
) {
}
