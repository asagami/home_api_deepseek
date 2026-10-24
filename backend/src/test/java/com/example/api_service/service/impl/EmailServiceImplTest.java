package com.example.api_service.service.impl;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private JavaMailSender mailSender;
    private EmailServiceImpl emailService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailServiceImpl(redisTemplate, mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "no-reply@example.com");
    }

    @Test
    void shouldStoreSixDigitCodeWithTtlAndSendMail() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        emailService.sendVerifyCode("alice@example.com");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                eq("auth:email:code:alice@example.com"),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(5)));
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage message = mailCaptor.getValue();
        assertArrayEquals(new String[]{"alice@example.com"}, message.getTo());
        assertTrue(message.getText().contains(codeCaptor.getValue()));
    }

    @Test
    void shouldRejectResendWhileCodeIsStillAlive() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.sendVerifyCode("alice@example.com"));

        assertEquals(ErrorCode.EMAIL_CODE_SEND_TOO_FREQUENT, ex.getErrorCode());
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldReleaseCodeWhenMailSendingFails() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.sendVerifyCode("alice@example.com"));

        assertEquals(ErrorCode.EMAIL_SEND_FAILED, ex.getErrorCode());
        verify(redisTemplate).delete("auth:email:code:alice@example.com");
    }

    @Test
    void shouldDeleteCodeAfterSuccessfulVerification() {
        when(valueOperations.get("auth:email:code:alice@example.com")).thenReturn("123456");

        emailService.verifyCode("alice@example.com", "123456");

        verify(redisTemplate).delete("auth:email:code:alice@example.com");
    }

    @Test
    void shouldRejectWrongOrExpiredCodeWithoutDeleting() {
        when(valueOperations.get("auth:email:code:alice@example.com")).thenReturn("123456");

        BusinessException wrong = assertThrows(BusinessException.class,
                () -> emailService.verifyCode("alice@example.com", "000000"));
        assertEquals(ErrorCode.EMAIL_CODE_INVALID, wrong.getErrorCode());
        verify(redisTemplate, never()).delete(anyString());

        BusinessException expired = assertThrows(BusinessException.class,
                () -> emailService.verifyCode("bob@example.com", "123456"));
        assertEquals(ErrorCode.EMAIL_CODE_INVALID, expired.getErrorCode());
    }
}
