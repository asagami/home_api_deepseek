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

/**
 * 邮箱验证码服务实现。
 *
 * <p>Redis 键职责分离：</p>
 * <ul>
 *   <li>{@code auth:email:code:{email}} —— 验证码本体，TTL 5 分钟；重发时直接覆盖旧码，
 *       旧码即刻失效；</li>
 *   <li>{@code auth:email:cooling:{email}} —— 发送冷却标记，TTL 60 秒，用 setIfAbsent
 *       原子判定“60 秒内不可重复发送”，与验证码有效期解耦；</li>
 *   <li>{@code ratelimit:auth:email:attempts:{email}} —— 校验失败计数，TTL 与验证码同步；
 *       累计错误达到上限时立即作废验证码，强制重新获取，防止 6 位纯数字码被暴力枚举。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    static final String CODE_KEY_PREFIX = "auth:email:code:";
    static final String COOLING_KEY_PREFIX = "auth:email:cooling:";
    static final String ATTEMPTS_KEY_PREFIX = "auth:email:attempts:";
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration COOLING_TTL = Duration.ofSeconds(60);

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

        // 冷却标记与验证码分键存储：码的有效期是 5 分钟，但允许每 60 秒重发一次，
        // 重发直接覆盖旧码。setIfAbsent 原子完成“是否冷却中”的判断与标记写入。
        String coolingKey = COOLING_KEY_PREFIX + target;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(coolingKey, "1", COOLING_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_SEND_TOO_FREQUENT);
        }

        String codeKey = CODE_KEY_PREFIX + target;
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL);

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
            // 发送失败时同时释放冷却标记与验证码，允许用户立即重试
            redisTemplate.delete(coolingKey);
            redisTemplate.delete(codeKey);
            log.error("验证码邮件发送失败: email={}", maskEmail(target), ex);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
        log.info("验证码邮件已发送: email={}", maskEmail(target));
    }

    @Override
    public void verifyCode(String email, String code) {
        String target = email.trim();
        String codeKey = CODE_KEY_PREFIX + target;
        String attemptsBucket = ATTEMPTS_KEY_PREFIX + target;

        // 兼容历史遗留计数：超出上限后销毁验证码，迫使重新获取
        long attempts = rateLimiter.getCount(attemptsBucket);
        if (attempts >= maxVerifyAttempts) {
            invalidate(codeKey, attemptsBucket);
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        }

        String expected = redisTemplate.opsForValue().get(codeKey);
        if (expected == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        if (!expected.equals(code.trim())) {
            // 每次失败递增尝试计数；达到上限的当次立即作废验证码，不给第 6 次尝试机会
            long failures = rateLimiter.incrementWithTtl(attemptsBucket, CODE_TTL);
            if (failures >= maxVerifyAttempts) {
                invalidate(codeKey, attemptsBucket);
                log.warn("验证码错误次数达上限，已作废并强制重新获取: email={}", maskEmail(target));
                throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        // 校验通过后立即删除，验证码不可重复使用
        invalidate(codeKey, attemptsBucket);
    }

    private void invalidate(String codeKey, String attemptsBucket) {
        redisTemplate.delete(codeKey);
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
