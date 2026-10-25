package com.example.api_service.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    static final String CODE_KEY_PREFIX = "auth:email:code:";
    static final String ATTEMPTS_KEY_PREFIX = "auth:email:attempts:";
    static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final RedisRateLimiter rateLimiter;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${app.ratelimit.email-code.max-attempts:5}")
    private int maxVerifyAttempts;

    @Override
    public void sendVerifyCode(String email) {
        String target = email.trim();
        String key = CODE_KEY_PREFIX + target;
        String code = RandomUtil.randomNumbers(6);

        // setIfAbsent 同时完成“是否已发送”的判断与写入，避免并发重复发送
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, code, CODE_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_SEND_TOO_FREQUENT);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(mailFrom)) {
            message.setFrom(mailFrom);
        }
        message.setTo(target);
        message.setSubject("【API Service】邮箱验证码");
        message.setText("您的验证码为 " + code + "，5 分钟内有效。请勿将验证码泄露给他人。");

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // 发送失败时释放占用，允许用户立即重试
            redisTemplate.delete(key);
            log.error("验证码邮件发送失败: email={}", maskEmail(target), ex);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
        log.info("验证码邮件已发送: email={}", maskEmail(target));
    }

    @Override
    public void verifyCode(String email, String code) {
        String target = email.trim();
        String key = CODE_KEY_PREFIX + target;
        String attemptsBucket = ATTEMPTS_KEY_PREFIX + target;

        // 尝试次数上限检查，防止 6 位纯数字验证码被暴力枚举
        long attempts = rateLimiter.getCount(attemptsBucket);
        if (attempts >= maxVerifyAttempts) {
            // 超出上限后销毁验证码，迫使重新获取
            redisTemplate.delete(key);
            rateLimiter.reset(attemptsBucket);
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        }

        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null || !expected.equals(code.trim())) {
            // 每次失败递增尝试计数，TTL 与验证码同步
            rateLimiter.incrementWithTtl(attemptsBucket, CODE_TTL);
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        // 校验通过后立即删除，验证码不可重复使用
        redisTemplate.delete(key);
        rateLimiter.reset(attemptsBucket);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return at >= 0 ? "***" + email.substring(at) : "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
