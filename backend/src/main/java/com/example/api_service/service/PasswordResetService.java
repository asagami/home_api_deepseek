package com.example.api_service.service;

import com.example.api_service.model.vo.auth.ResetTokenVerifyVO;

/**
 * 密码找回服务：申请重置链接、校验令牌换取一次性票据、提交新密码。
 */
public interface PasswordResetService {

    /**
     * 发起找回密码。账号不存在、未绑定邮箱、冷却中或邮件发送失败时均静默返回，
     * 由调用方统一返回“如果账号存在，重置邮件已发出”，避免账号枚举。
     *
     * @param account 用户名（账号）
     * @param ip      客户端 IP，用于按 IP + 账号维度限流
     */
    void requestReset(String account, String ip);

    /**
     * 校验邮件中的重置令牌，通过后消费该令牌并换发绑定当前会话版本的短期一次性票据。
     *
     * @param token 邮件链接中的令牌（含签名）
     * @return 一次性票据与脱敏邮箱
     */
    ResetTokenVerifyVO verifyToken(String token);

    /**
     * 使用一次性票据提交新密码：更新密码哈希、递增会话版本（踢出历史 JWT 及此前换出的票据）、
     * 作废该用户全部未使用的重置令牌。
     *
     * @param ticket      校验环节换发的票据
     * @param newPassword 新密码明文
     */
    void resetPassword(String ticket, String newPassword);
}
