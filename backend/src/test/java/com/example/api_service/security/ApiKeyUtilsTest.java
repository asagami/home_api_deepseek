package com.example.api_service.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyUtilsTest {

    @Test
    void shouldGenerateFiftyOneCharacterKeyWithSkPrefix() {
        String apiKey = ApiKeyUtils.generateApiKey();

        assertEquals(51, apiKey.length());
        assertTrue(apiKey.startsWith("sk-"));
        assertTrue(apiKey.substring(3).matches("[A-Za-z0-9]{48}"));
        assertNotEquals(apiKey, ApiKeyUtils.generateApiKey());
    }

    @Test
    void shouldMaskMiddlePartWhenExtractingPrefix() {
        assertEquals("sk-abcdefghi...wxyz",
                ApiKeyUtils.extractPrefix("sk-abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    void shouldReturnShortKeyAsIs() {
        assertEquals("sk-short", ApiKeyUtils.extractPrefix("sk-short"));
    }

    @Test
    void shouldCalculateLowercaseHexSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApiKeyUtils.sha256("abc"));
        assertEquals(64, ApiKeyUtils.sha256("any-api-key").length());
    }
}
