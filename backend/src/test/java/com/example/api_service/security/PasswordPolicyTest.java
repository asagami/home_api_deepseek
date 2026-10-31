package com.example.api_service.security;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void shouldAcceptStrongPasswords() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("alice", "Str0ngPass2026"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("alice", "Correct-Horse9Battery"));
        assertDoesNotThrow(() -> PasswordPolicy.validate(null, "NewStr0ngPass2027"));
    }

    @Test
    void shouldRejectInvalidLength() {
        assertRejected("alice", "Ab1");
        assertRejected("alice", "Aa1" + "x".repeat(62));
    }

    @Test
    void shouldRequireUppercaseLowercaseAndDigit() {
        assertRejected("alice", "alllowercase123");
        assertRejected("alice", "UPPERCASE123");
        assertRejected("alice", "NoDigitsHere");
    }

    @Test
    void shouldRejectCommonPasswords() {
        assertRejected("alice", "Password123");
        assertRejected("alice", "Qwerty2026");
        assertRejected("alice", "12345678");
    }

    @Test
    void shouldRejectSequentialOrRepeatedRuns() {
        assertRejected("alice", "Abcdefg1");
        assertRejected("alice", "Zyxw9876Q");
        assertRejected("alice", "Aa111111");
    }

    @Test
    void shouldRejectPasswordsBeyondBcryptByteLimit() {
        // 23 个汉字（69 字节）+ Ab1 = 72 字节，恰好卡在 BCrypt 上限
        assertDoesNotThrow(() -> PasswordPolicy.validate("alice",
                "密文口令账号注册登录验证强度策略序列重复常见词Ab1"));
        // 24 个汉字（72 字节）+ Ab1 = 75 字节，字符数仍在 8-64 以内，但 BCrypt 会拒
        assertRejected("alice", "密文口令账号注册登录验证强度策略序列重复常见词典Ab1");
    }

    @Test
    void shouldRejectPasswordContainingUsername() {
        assertRejected("alice", "AlicePass2026");
        // 用户名不足 3 位时不做包含检查
        assertDoesNotThrow(() -> PasswordPolicy.validate("ab", "AbPass2026"));
    }

    private static void assertRejected(String username, String password) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PasswordPolicy.validate(username, password));
        assertEquals(ErrorCode.WEAK_PASSWORD, ex.getErrorCode());
    }
}
