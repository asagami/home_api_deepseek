package com.example.api_service.service.impl;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.mapper.PasswordResetTokenMapper;
import com.example.api_service.model.entity.PasswordResetToken;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.security.ResetTokenCodec;
import com.example.api_service.service.ResetMailDispatcher;
import com.example.api_service.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 找回密码在 Redis / 数据库故障时的补偿行为。
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    private static final String TICKET_KEY = "password:reset:ticket:ticket-x";
    private static final Long USER_ID = 2L;
    private static final int TOKEN_VERSION = 3;

    @Mock
    private PasswordResetTokenMapper passwordResetTokenMapper;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private ResetMailDispatcher resetMailDispatcher;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private ResetTokenCodec resetTokenCodec;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ticketTtlMinutes", 10L);
    }

    @Test
    void shouldKeepTokenUsableWhenRedisWriteFails() {
        PasswordResetToken row = resetTokenRow();
        SysUser user = enabledUser();
        when(resetTokenCodec.verifySignature("token")).thenReturn(true);
        when(resetTokenCodec.hash("token")).thenReturn("hash");
        when(resetTokenCodec.randomToken(32)).thenReturn("ticket-x");
        when(passwordResetTokenMapper.selectOne(any())).thenReturn(row);
        when(sysUserService.getById(USER_ID)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations).set(TICKET_KEY, USER_ID + ":" + TOKEN_VERSION,
                        Duration.ofMinutes(10));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyToken("token"));

        assertEquals(ErrorCode.RESET_TICKET_INVALID, ex.getErrorCode());
        // 票据没写成功就不能消费令牌，否则用户凭原链接重试会一直拿到 40005
        verify(passwordResetTokenMapper, never()).update(any(), any());
    }

    @Test
    void shouldRestoreTicketWhenDatabaseUpdateFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(TICKET_KEY)).thenReturn(USER_ID + ":" + TOKEN_VERSION);
        // getById 位于 applyReset 内，任何数据库异常都应触发票据恢复
        when(sysUserService.getById(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.resetPassword("ticket-x", "newPassword123"));

        // 事务回滚后票据被写回，用户可凭同一票据重试
        verify(valueOperations).set(TICKET_KEY, USER_ID + ":" + TOKEN_VERSION,
                Duration.ofMinutes(10));
    }

    private static PasswordResetToken resetTokenRow() {
        PasswordResetToken row = new PasswordResetToken();
        row.setId(1L);
        row.setUserId(USER_ID);
        row.setTokenHash("hash");
        row.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return row;
    }

    private static SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setStatus(1);
        user.setRole("USER");
        user.setTokenVersion(TOKEN_VERSION);
        return user;
    }
}
