package com.example.api_service.service;

/**
 * 邮箱验证码服务：负责验证码的生成、存储、发送与校验。
 */
public interface EmailService {

    /**
     * 生成 6 位数字验证码，写入 Redis（TTL 5 分钟）并发送到指定邮箱。
     *
     * @param email 接收验证码的邮箱
     */
    void sendVerifyCode(String email);

    /**
     * 校验邮箱验证码，校验通过后立即删除，防止重复使用。
     *
     * @param email 邮箱
     * @param code  用户提交的验证码
     */
    void verifyCode(String email, String code);
}
