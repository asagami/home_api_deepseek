package com.example.api_service.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * API Key 工具：生成、脱敏前缀、SHA-256 摘要。
 */
public final class ApiKeyUtils {

    private static final String KEY_PREFIX = "sk-";
    private static final int KEY_LENGTH = 51;
    private static final int RANDOM_LENGTH = KEY_LENGTH - KEY_PREFIX.length();
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ApiKeyUtils() {
    }

    /**
     * 生成 51 位、以 {@code sk-} 开头的随机 API Key。
     */
    public static String generateApiKey() {
        char[] randomPart = new char[RANDOM_LENGTH];
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            randomPart[i] = ALPHABET[SECURE_RANDOM.nextInt(ALPHABET.length)];
        }
        return KEY_PREFIX + new String(randomPart);
    }

    /**
     * 生成用于展示的脱敏前缀，例如 {@code sk-AbCdEfgh...8f9a}。
     */
    public static String extractPrefix(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (apiKey.length() <= 12) {
            return apiKey;
        }
        return apiKey.substring(0, 12) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 计算 SHA-256 摘要，返回小写十六进制字符串（64 位）。
     */
    public static String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("待摘要内容不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }
}
