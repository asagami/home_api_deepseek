package com.example.api_service.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
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
    static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

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
        String key = CODE_KEY_PREFIX + email.trim();
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null || !expected.equals(code.trim())) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        // 校验通过后立即删除，验证码不可重复使用
        redisTemplate.delete(key);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return at >= 0 ? "***" + email.substring(at) : "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
