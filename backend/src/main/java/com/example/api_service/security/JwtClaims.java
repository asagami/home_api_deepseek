package com.example.api_service.security;

import java.time.Instant;

/**
 * JWT 解析后的身份信息。
 */
public record JwtClaims(
        Long userId,
        String username,
        String role,
        /** 会话版本，旧版本令牌为 null（按 0 处理） */
        Integer tokenVersion,
        Instant issuedAt,
        Instant expiresAt
) {
}
