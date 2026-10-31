package com.example.api_service.security;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 密码强度策略：注册、找回密码、修改密码共用同一套规则。
 *
 * <p>规则参考 NIST SP 800-63B：以长度为主要强度来源，不强制特殊符号；同时拦截
 * 常见弱口令、连续/重复字符以及包含用户名的密码，避免「满足复杂度但一眼可猜」。</p>
 *
 * <ul>
 *   <li>长度 8-64；</li>
 *   <li>UTF-8 编码后不超过 72 字节（BCrypt 的硬上限，一个汉字约占 3 字节）；</li>
 *   <li>必须同时包含大写字母、小写字母和数字（符号可选，用于提升强度）；</li>
 *   <li>不能是常见弱口令，也不能是「常见词 + 纯数字后缀」；</li>
 *   <li>不能出现 4 位及以上的顺序字符或 3 个及以上连续重复字符；</li>
 *   <li>不能包含用户名（用户名不少于 3 位时）。</li>
 * </ul>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    /**
     * BCrypt 对超过 72 字节的明文直接抛 {@code IllegalArgumentException}（不截断），
     * 64 个汉字可达 192 字节，必须在策略层按 UTF-8 字节数拦截，避免注册/重置/改密落入 500。
     */
    public static final int MAX_UTF8_BYTES = 72;

    /** 常见弱口令与键盘序列（小写），精确匹配。 */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "87654321", "11111111",
            "00000000", "88888888", "66666666", "12341234", "abcd1234",
            "abc12345", "a1234567", "a1b2c3d4", "1q2w3e4r", "1qaz2wsx",
            "qazwsxedc", "zaq12wsx", "qwer1234", "qwerty123", "qwertyuiop",
            "asdfghjkl", "zxcvbnm1", "iloveyou", "sunshine", "princess",
            "football", "baseball", "superman", "batman", "trustno1",
            "whatever", "starwars", "michael123", "jennifer", "hunter2",
            "freedom1", "shadow123", "master123", "changeme", "test1234",
            "guest123", "user1234", "login123", "hello123", "secret123");

    /** 常见词：命中后若后缀只有数字/符号，同样视为弱口令（如 password2026!）。 */
    private static final Set<String> COMMON_WORDS = Set.of(
            "password", "passw0rd", "qwerty", "asdfgh", "zxcvbn", "admin",
            "administrator", "welcome", "letmein", "iloveyou", "monkey",
            "dragon", "sunshine", "princess", "football", "baseball",
            "superman", "batman", "master", "shadow", "michael", "jennifer",
            "hunter", "freedom", "whatever", "starwars", "secret", "changeme",
            "guest", "root", "login", "hello", "test", "abc123");

    private PasswordPolicy() {
    }

    /**
     * 校验密码是否符合策略，不符合时抛出 {@link ErrorCode#WEAK_PASSWORD}。
     *
     * @param username 用户名，用于「密码不能包含用户名」检查，可为 {@code null}
     * @param password 明文密码
     */
    public static void validate(String username, String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD, "密码长度需在 8-64 之间");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD,
                    "密码编码后不能超过 72 字节（一个汉字约占 3 字节），请缩短");
        }
        if (!hasLower(password) || !hasUpper(password) || !hasDigit(password)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD,
                    "密码需同时包含大写字母、小写字母和数字");
        }

        String normalized = password.toLowerCase(Locale.ROOT);
        if (isCommonPassword(normalized)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD,
                    "该密码过于常见，请更换更安全的密码");
        }
        if (hasSequentialRun(password) || hasRepeatedRun(password)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD,
                    "密码包含过多连续或重复字符，请更换");
        }

        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (name.length() >= 3 && normalized.contains(name)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD, "密码不能包含用户名");
        }
    }

    private static boolean hasLower(String password) {
        return password.codePoints().anyMatch(Character::isLowerCase);
    }

    private static boolean hasUpper(String password) {
        return password.codePoints().anyMatch(Character::isUpperCase);
    }

    private static boolean hasDigit(String password) {
        return password.codePoints().anyMatch(Character::isDigit);
    }

    private static boolean isCommonPassword(String normalized) {
        String alnum = normalized.replaceAll("[^a-z0-9]", "");
        if (COMMON_PASSWORDS.contains(normalized) || COMMON_PASSWORDS.contains(alnum)) {
            return true;
        }
        for (String word : COMMON_WORDS) {
            if (alnum.startsWith(word)
                    && isDigitsOnly(alnum.substring(word.length()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDigitsOnly(String value) {
        return !value.isEmpty() && value.codePoints().allMatch(Character::isDigit);
    }

    /** 检测 4 位及以上的递增/递减序列（如 1234、abcd、98765）。 */
    private static boolean hasSequentialRun(String password) {
        int run = 1;
        int direction = 0;
        for (int i = 1; i < password.length(); i++) {
            int diff = password.charAt(i) - password.charAt(i - 1);
            if (diff == 1 || diff == -1) {
                if (diff == direction) {
                    run++;
                } else {
                    direction = diff;
                    run = 2;
                }
                if (run >= 4) {
                    return true;
                }
            } else {
                run = 1;
                direction = 0;
            }
        }
        return false;
    }

    /** 检测 3 个及以上的连续重复字符（如 aaa、111）。 */
    private static boolean hasRepeatedRun(String password) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                run++;
                if (run >= 3) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }
}
