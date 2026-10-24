package com.example.api_service.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-bytes-long-for-hs256";

    private final JwtUtils jwtUtils = new JwtUtils(SECRET, 3_600_000L);

    @Test
    void shouldGenerateAndParseToken() {
        String token = jwtUtils.generateToken(42L, "alice", "USER");

        JwtClaims claims = jwtUtils.parseToken(token);

        assertEquals(42L, claims.userId());
        assertEquals("alice", claims.username());
        assertEquals("USER", claims.role());
        assertNotNull(claims.issuedAt());
        assertNotNull(claims.expiresAt());
        assertTrue(claims.expiresAt().isAfter(claims.issuedAt()));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtUtils.generateToken(42L, "alice", "USER");

        assertThrows(JwtException.class, () -> jwtUtils.parseToken(token + "tampered"));
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() {
        JwtUtils another = new JwtUtils("another-secret-at-least-32-bytes-long-for-hs256", 3_600_000L);
        String token = another.generateToken(42L, "alice", "USER");

        assertThrows(JwtException.class, () -> jwtUtils.parseToken(token));
    }
}
