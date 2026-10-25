package com.example.api_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与解析工具，使用 HS256（密钥长度至少 32 字节）。
 */
@Component
public class JwtUtils {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtils(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("app.jwt.secret 未配置");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 长度不足，HS256 要求至少 32 字节，当前仅 "
                            + secretBytes.length + " 字节");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException("app.jwt.expiration-ms 必须大于 0");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * 生成访问令牌。
     */
    public String generateToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验签名与过期时间。
     *
     * @throws io.jsonwebtoken.JwtException 签名非法或令牌过期
     * @throws IllegalArgumentException     载荷格式非法
     */
    public JwtClaims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new JwtClaims(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_ROLE, String.class),
                claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant()
        );
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
