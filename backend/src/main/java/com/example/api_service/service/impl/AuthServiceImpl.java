package com.example.api_service.service.impl;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.model.dto.auth.LoginRequest;
import com.example.api_service.model.dto.auth.RegisterRequest;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.vo.auth.LoginVO;
import com.example.api_service.model.vo.auth.UserProfileVO;
import com.example.api_service.security.JwtUtils;
import com.example.api_service.service.AuthService;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long INITIAL_QUOTA = 1000L;
    private static final String ROLE_USER = "USER";
    private static final int STATUS_ENABLED = 1;

    private final SysUserService sysUserService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO register(RegisterRequest request) {
        String username = request.username().trim();
        if (sysUserService.lambdaQuery().eq(SysUser::getUsername, username).exists()) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

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
        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .one();
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
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
