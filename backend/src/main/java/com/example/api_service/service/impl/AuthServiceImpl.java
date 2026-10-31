package com.example.api_service.service.impl;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.model.dto.auth.ChangePasswordRequest;
import com.example.api_service.model.dto.auth.LoginRequest;
import com.example.api_service.model.dto.auth.RegisterRequest;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.vo.auth.LoginVO;
import com.example.api_service.model.vo.auth.UserProfileVO;
import com.example.api_service.security.JwtUtils;
import com.example.api_service.security.PasswordPolicy;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.service.AuthService;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long INITIAL_QUOTA = 1000L;
    private static final String ROLE_USER = "USER";
    private static final int STATUS_ENABLED = 1;
    private static final String LOGIN_FAIL_BUCKET_PREFIX = "login:fail:";
    private static final String CHANGE_FAIL_BUCKET_PREFIX = "password:change:fail:";
    private static final String CHANGE_LOCK_BUCKET_PREFIX = "password:change:lock:";

    private final SysUserService sysUserService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisRateLimiter rateLimiter;

    @Value("${app.ratelimit.login.max-failures:5}")
    private int maxLoginFailures;

    @Value("${app.ratelimit.login.lockout-minutes:15}")
    private int lockoutMinutes;

    /** 修改密码时旧密码允许连续输错的次数 */
    @Value("${app.ratelimit.password-change.max-failures:5}")
    private int maxPasswordChangeFailures;

    /** 旧密码错误计数的统计窗口（分钟） */
    @Value("${app.ratelimit.password-change.window-minutes:15}")
    private int passwordChangeWindowMinutes;

    /** 达到错误上限后修改密码功能的锁定时间（分钟） */
    @Value("${app.ratelimit.password-change.lock-minutes:30}")
    private int passwordChangeLockMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO register(RegisterRequest request) {
        String username = request.username().trim();
        if (sysUserService.lambdaQuery().eq(SysUser::getUsername, username).exists()) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        // 弱密码在消耗邮箱验证码之前拦下，避免用户因密码不合规而重新获取验证码
        PasswordPolicy.validate(username, request.password());

        // 校验通过后 EmailService 会立即删除验证码，确保一次性使用
        emailService.verifyCode(request.email(), request.code());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setEmail(request.email().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(ROLE_USER);
        user.setBalanceQuota(INITIAL_QUOTA);
        user.setStatus(STATUS_ENABLED);
        user.setIsDeleted(0);

        try {
            sysUserService.save(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册时由唯一索引兜底
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        return toProfile(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        String username = request.username().trim();
        String failBucket = LOGIN_FAIL_BUCKET_PREFIX + username;

        // 登录失败锁定检查：连续失败达到阈值后拒绝登录，直至锁定窗口过期
        long failCount = rateLimiter.getCount(failBucket);
        if (failCount >= maxLoginFailures) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }

        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .one();
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 记录失败次数，首次失败时设置锁定窗口 TTL
            rateLimiter.incrementWithTtl(failBucket, Duration.ofMinutes(lockoutMinutes));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 登录成功，清除失败计数
        rateLimiter.reset(failBucket);

        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String token = jwtUtils.generateToken(
                user.getId(), user.getUsername(), user.getRole(), tokenVersion);
        return new LoginVO(token, "Bearer", jwtUtils.getExpirationMs());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO changePassword(Long userId, ChangePasswordRequest request) {
        String lockBucket = CHANGE_LOCK_BUCKET_PREFIX + userId;
        if (rateLimiter.getCount(lockBucket) > 0) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_LOCKED);
        }

        SysUser user = sysUserService.getById(userId);
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账户不存在或已被封禁");
        }

        // 先校验新密码策略：不合规直接拒绝，不占用旧密码的试错次数
        PasswordPolicy.validate(user.getUsername(), request.newPassword());

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            long failures = rateLimiter.incrementWithTtl(
                    CHANGE_FAIL_BUCKET_PREFIX + userId,
                    Duration.ofMinutes(passwordChangeWindowMinutes));
            if (failures >= maxPasswordChangeFailures) {
                rateLimiter.reset(CHANGE_FAIL_BUCKET_PREFIX + userId);
                rateLimiter.incrementWithTtl(lockBucket,
                        Duration.ofMinutes(passwordChangeLockMinutes));
                throw new BusinessException(ErrorCode.PASSWORD_CHANGE_LOCKED);
            }
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        // 更新密码的同时递增会话版本：其他设备的历史 JWT 全部失效
        boolean updated = sysUserService.lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .setSql("token_version = token_version + 1")
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "密码更新失败，请稍后重试");
        }

        rateLimiter.reset(CHANGE_FAIL_BUCKET_PREFIX + userId);
        rateLimiter.reset(lockBucket);

        // 以数据库中的最新版本签发当前设备的新令牌，避免并发场景下版本错位
        SysUser refreshed = sysUserService.getById(userId);
        int tokenVersion = refreshed == null || refreshed.getTokenVersion() == null
                ? 0
                : refreshed.getTokenVersion();
        String token = jwtUtils.generateToken(
                user.getId(), user.getUsername(), user.getRole(), tokenVersion);
        return new LoginVO(token, "Bearer", jwtUtils.getExpirationMs());
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账户不存在或已被封禁");
        }
        return toProfile(user);
    }

    private UserProfileVO toProfile(SysUser user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getBalanceQuota(),
                user.getStatus(),
                user.getCreateTime()
        );
    }
}
