package com.example.api_service.service.impl;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.security.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    private static final String CODE_KEY = "auth:email:code:alice@example.com";
    private static final String COOLING_KEY = "auth:email:cooling:alice@example.com";
    private static final String ATTEMPTS_BUCKET = "auth:email:attempts:alice@example.com";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private JavaMailSender mailSender;
    private RedisRateLimiter rateLimiter;
    private EmailServiceImpl emailService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        mailSender = mock(JavaMailSender.class);
        rateLimiter = mock(RedisRateLimiter.class);
        emailService = new EmailServiceImpl(redisTemplate, mailSender, rateLimiter);
        ReflectionTestUtils.setField(emailService, "mailFrom", "no-reply@example.com");
        ReflectionTestUtils.setField(emailService, "maxVerifyAttempts", 5);
        // 默认模拟：无历史尝试次数
        lenient().when(rateLimiter.getCount(anyString())).thenReturn(0L);
        lenient().when(rateLimiter.incrementWithTtl(anyString(), any(Duration.class))).thenReturn(1L);
    }

    @Test
    void shouldStoreCodeWithFiveMinuteTtlAndSetSixtySecondCooling() {
        when(valueOperations.setIfAbsent(eq(COOLING_KEY), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(true);

        emailService.sendVerifyCode("alice@example.com");

        // 冷却标记独立成键，60 秒 TTL；验证码直接写入，5 分钟 TTL
        verify(valueOperations).setIfAbsent(eq(COOLING_KEY), anyString(), eq(Duration.ofSeconds(60)));
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(CODE_KEY), codeCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage message = mailCaptor.getValue();
        assertArrayEquals(new String[]{"alice@example.com"}, message.getTo());
        assertTrue(message.getText().contains(codeCaptor.getValue()));
    }

    @Test
    void shouldRejectResendWhileCooling() {
        when(valueOperations.setIfAbsent(eq(COOLING_KEY), anyString(), any(Duration.class)))
                .thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.sendVerifyCode("alice@example.com"));

        assertEquals(ErrorCode.EMAIL_CODE_SEND_TOO_FREQUENT, ex.getErrorCode());
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldReleaseCoolingAndCodeWhenMailSendingFails() {
        when(valueOperations.setIfAbsent(eq(COOLING_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.sendVerifyCode("alice@example.com"));

        assertEquals(ErrorCode.EMAIL_SEND_FAILED, ex.getErrorCode());
        verify(redisTemplate).delete(COOLING_KEY);
        verify(redisTemplate).delete(CODE_KEY);
    }

    @Test
    void shouldDeleteCodeAfterSuccessfulVerification() {
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");

        emailService.verifyCode("alice@example.com", "123456");

        verify(redisTemplate).delete(CODE_KEY);
        verify(rateLimiter).reset(ATTEMPTS_BUCKET);
    }

    @Test
    void shouldRejectWrongOrExpiredCodeWithoutDeleting() {
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");

        BusinessException wrong = assertThrows(BusinessException.class,
                () -> emailService.verifyCode("alice@example.com", "000000"));
        assertEquals(ErrorCode.EMAIL_CODE_INVALID, wrong.getErrorCode());
        verify(redisTemplate, never()).delete(anyString());
        // 失败时递增尝试计数
        verify(rateLimiter).incrementWithTtl(eq(ATTEMPTS_BUCKET), any(Duration.class));

        BusinessException expired = assertThrows(BusinessException.class,
                () -> emailService.verifyCode("bob@example.com", "123456"));
        assertEquals(ErrorCode.EMAIL_CODE_INVALID, expired.getErrorCode());
    }

    @Test
    void shouldInvalidateCodeOnFifthConsecutiveFailure() {
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");
        // 前面已错 4 次，本次是第 5 次失败
        when(rateLimiter.incrementWithTtl(eq(ATTEMPTS_BUCKET), any(Duration.class))).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.verifyCode("alice@example.com", "000000"));

        // 达到上限的当次即作废验证码，不给第 6 次尝试机会
        assertEquals(ErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED, ex.getErrorCode());
        verify(redisTemplate).delete(CODE_KEY);
        verify(rateLimiter).reset(ATTEMPTS_BUCKET);
    }

    @Test
    void shouldInvalidateCodeWhenAttemptsAlreadyExhausted() {
        when(rateLimiter.getCount(ATTEMPTS_BUCKET)).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.verifyCode("alice@example.com", "123456"));

        assertEquals(ErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED, ex.getErrorCode());
        verify(redisTemplate).delete(CODE_KEY);
        verify(rateLimiter).reset(ATTEMPTS_BUCKET);
    }
}
