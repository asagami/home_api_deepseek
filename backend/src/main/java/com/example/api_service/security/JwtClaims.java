package com.example.api_service.security;

import java.time.Instant;

/**
 * JWT 解析后的身份信息。
 */
public record JwtClaims(
        Long userId,
        String username,
        String role,
        Instant issuedAt,
        Instant expiresAt
) {
}
