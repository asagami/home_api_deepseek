package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.mapper.PasswordResetTokenMapper;
import com.example.api_service.model.entity.PasswordResetToken;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.vo.auth.ResetTokenVerifyVO;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.security.ResetTokenCodec;
import com.example.api_service.service.PasswordResetService;
import com.example.api_service.service.ResetMailDispatcher;
import com.example.api_service.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 密码找回实现。
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>数据库只存令牌 SHA-256 哈希，明文仅出现在邮件链接中；</li>
 *   <li>令牌校验通过即消费（used_at），换发的票据存 Redis，绑定签发时的会话版本，10 分钟一次性有效；</li>
 *   <li>重置成功后 {@code sys_user.token_version + 1}，所有历史 JWT 与此前换出的票据同时失效；</li>
 *   <li>申请接口无论账号是否存在都返回成功，邮件在独立线程池异步发送、失败只记日志，
 *       响应耗时与账号是否存在无关，避免枚举。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final int STATUS_ENABLED = 1;
    private static final String TICKET_KEY_PREFIX = "password:reset:ticket:";
    private static final String COOLING_KEY_PREFIX = "password:reset:cooling:";
    private static final String ACCOUNT_BUCKET_PREFIX = "password:reset:account:";
    /** 与 AuthServiceImpl 的登录失败计数桶保持一致，重置成功后解除锁定 */
    private static final String LOGIN_FAIL_BUCKET_PREFIX = "login:fail:";

    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final SysUserService sysUserService;
    private final ResetMailDispatcher resetMailDispatcher;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimiter rateLimiter;
    private final ResetTokenCodec resetTokenCodec;

    @Value("${app.password-reset.link-base-url:http://localhost:5173/reset-password}")
    private String linkBaseUrl;

    @Value("${app.password-reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.password-reset.ticket-ttl-minutes:10}")
    private long ticketTtlMinutes;

    @Value("${app.password-reset.resend-cooling-seconds:60}")
    private long resendCoolingSeconds;

    @Value("${app.ratelimit.password-reset.account-limit:3}")
    private int accountLimit;

    @Value("${app.ratelimit.password-reset.account-window-minutes:15}")
    private int accountWindowMinutes;

    @Override
    public void requestReset(String account, String ip) {
        String normalized = account.trim();
        // 按 IP + 账号限流，避免攻击者用他人账号把受害者锁在限流窗口外
        String bucket = ACCOUNT_BUCKET_PREFIX + ip + ":"
                + normalized.toLowerCase(Locale.ROOT);
        if (!rateLimiter.allowFixedWindow(bucket, accountLimit,
                Duration.ofMinutes(accountWindowMinutes))) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOO_FREQUENT);
        }

        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, normalized)
                .one();
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            log.info("找回密码请求未命中可用账号: account={}", maskAccount(normalized));
            return;
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("账号未绑定邮箱，无法发送重置邮件: userId={}", user.getId());
            return;
        }
        if (!acquireResendCooling(user.getId())) {
            log.info("找回密码邮件处于冷却期，跳过发送: userId={}", user.getId());
            return;
        }

        String rawToken = resetTokenCodec.issue();
        PasswordResetToken row = new PasswordResetToken();
        row.setUserId(user.getId());
        row.setTokenHash(resetTokenCodec.hash(rawToken));
        row.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes));
        row.setRequestIp(ip);
        passwordResetTokenMapper.insert(row);

        String resetUrl = linkBaseUrl
                + (linkBaseUrl.contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        // 邮件在独立线程池异步发送：无论账号是否命中都不在请求线程里等待 SMTP，
        // 两条路径的响应耗时因此趋于一致，消除时序侧信道。
        resetMailDispatcher.dispatch(user.getId(), email, resetUrl, tokenTtlMinutes);
    }

    @Override
    public ResetTokenVerifyVO verifyToken(String token) {
        String raw = token.trim();
        if (!resetTokenCodec.verifySignature(raw)) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        PasswordResetToken row = passwordResetTokenMapper.selectOne(
                Wrappers.<PasswordResetToken>lambdaQuery()
                        .eq(PasswordResetToken::getTokenHash, resetTokenCodec.hash(raw)));
        LocalDateTime now = LocalDateTime.now();
        if (row == null || row.getUsedAt() != null || !row.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        // 会话版本必须在消费令牌之前读取：并发的重置若在本步骤之后提交，
        // 换出的票据携带的是过期版本，提交时会被拒绝；反过来先消费再读版本，
        // 则可能换出版本已经递增、重置后仍然可用的票据。
        SysUser user = sysUserService.getById(row.getUserId());
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();

        // 先写票据再消费令牌：Redis 不可用时令牌保持可用，用户可凭原链接重试；
        // 若顺序相反，Redis 抖动会让已消费的令牌永久作废。
        String ticket = resetTokenCodec.randomToken(32);
        try {
            // 票据绑定签发时的会话版本，密码重置后旧票据因版本不匹配而失效
            redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket,
                    user.getId() + ":" + tokenVersion, Duration.ofMinutes(ticketTtlMinutes));
        } catch (Exception ex) {
            log.error("重置票据写入 Redis 失败: userId={}", user.getId(), ex);
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }

        // 条件更新保证并发下同一令牌只能换票一次
        int consumed;
        try {
            consumed = passwordResetTokenMapper.update(new PasswordResetToken(),
                    Wrappers.<PasswordResetToken>lambdaUpdate()
                            .eq(PasswordResetToken::getId, row.getId())
                            .isNull(PasswordResetToken::getUsedAt)
                            .set(PasswordResetToken::getUsedAt, now));
        } catch (RuntimeException ex) {
            discardTicket(ticket);
            throw ex;
        }
        if (consumed == 0) {
            discardTicket(ticket);
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
        return new ResetTokenVerifyVO(ticket, ticketTtlMinutes * 60, maskEmail(user.getEmail()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String ticket, String newPassword) {
        Ticket issued = consumeTicket(ticket);
        try {
            applyReset(issued, newPassword);
        } catch (BusinessException ex) {
            // 业务拒绝（版本过期、账号不可用等）属于永久失效，票据不应写回
            throw ex;
        } catch (RuntimeException ex) {
            // 事务回滚后密码并未改变，把票据放回 Redis，用户可凭同一票据重试，
            // 无需重新走一遍邮件流程
            restoreTicket(ticket, issued);
            throw ex;
        }
    }

    private void applyReset(Ticket issued, String newPassword) {
        SysUser user = sysUserService.getById(issued.userId());
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        // 会话版本已递增说明密码在此期间已被重置过，重置前换出的票据一律作废
        if (issued.tokenVersion() != currentVersion) {
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }

        boolean updated = sysUserService.lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getPasswordHash, passwordEncoder.encode(newPassword))
                .setSql("token_version = token_version + 1")
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }

        // 作废该用户全部未使用的重置令牌，旧邮件链接随之失效
        passwordResetTokenMapper.update(new PasswordResetToken(),
                Wrappers.<PasswordResetToken>lambdaUpdate()
                        .eq(PasswordResetToken::getUserId, user.getId())
                        .isNull(PasswordResetToken::getUsedAt)
                        .set(PasswordResetToken::getUsedAt, LocalDateTime.now()));

        // 重置成功即解除登录失败锁定，允许用户立刻用新密码登录
        rateLimiter.reset(LOGIN_FAIL_BUCKET_PREFIX + user.getUsername());
        log.info("用户密码已重置，历史登录会话全部失效: userId={}", user.getId());
    }

    private Ticket consumeTicket(String ticket) {
        String key = TICKET_KEY_PREFIX + ticket.trim();
        String value;
        try {
            value = redisTemplate.opsForValue().getAndDelete(key);
        } catch (Exception ex) {
            log.warn("读取重置票据失败: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }
        if (value == null) {
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }
        int separator = value.indexOf(':');
        try {
            if (separator <= 0 || separator == value.length() - 1) {
                throw new NumberFormatException("票据格式不正确");
            }
            return new Ticket(Long.valueOf(value.substring(0, separator)),
                    Integer.parseInt(value.substring(separator + 1)));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.RESET_TICKET_INVALID);
        }
    }

    /** 票据内容：所属用户与签发时的会话版本，版本变化即失效。 */
    private record Ticket(Long userId, int tokenVersion) {
    }

    /** 换票未完成时清理刚写入的票据，避免 Redis 中残留无人持有的票据。 */
    private void discardTicket(String ticket) {
        try {
            redisTemplate.delete(TICKET_KEY_PREFIX + ticket);
        } catch (Exception ex) {
            log.warn("清理重置票据失败: {}", ex.getMessage());
        }
    }

    /** 重置事务失败时把票据写回 Redis，允许用户凭同一票据重试。 */
    private void restoreTicket(String ticket, Ticket issued) {
        try {
            redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket.trim(),
                    issued.userId() + ":" + issued.tokenVersion(),
                    Duration.ofMinutes(ticketTtlMinutes));
        } catch (Exception ex) {
            log.warn("重置失败后恢复票据失败: userId={}", issued.userId(), ex);
        }
    }

    /**
     * 同一用户的重置邮件冷却期；冷却配置为 0 时关闭（测试环境使用）。
     * Redis 故障时 fail-open，仍允许发送。
     */
    private boolean acquireResendCooling(Long userId) {
        if (resendCoolingSeconds <= 0) {
            return true;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(COOLING_KEY_PREFIX + userId, "1",
                            Duration.ofSeconds(resendCoolingSeconds));
            return !Boolean.FALSE.equals(acquired);
        } catch (Exception ex) {
            log.warn("重置邮件冷却标记写入失败，fail-open 放行: userId={}", userId, ex);
            return true;
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        String maskedName = name.length() <= 1 ? "*" : name.charAt(0) + "***";
        return maskedName + email.substring(at);
    }

    private String maskAccount(String account) {
        return account.length() <= 2 ? "***" : account.charAt(0) + "***";
    }
}
