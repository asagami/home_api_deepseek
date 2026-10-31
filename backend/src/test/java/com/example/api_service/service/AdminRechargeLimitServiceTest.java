package com.example.api_service.service;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.mapper.AdminRechargeMonthlyUsageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRechargeLimitServiceTest {

    private static final long OPERATOR_ID = 10L;
    private static final long USER_ID = 20L;
    private static final long LIMIT = 5000L;

    @Mock
    private AdminRechargeMonthlyUsageMapper mapper;

    @Test
    void shouldThrowWhenAddGrantedHitsNoCounterRow() {
        when(mapper.insertIfAbsent(anyLong(), eq(OPERATOR_ID), eq(USER_ID), anyString())).thenReturn(1);
        when(mapper.selectGrantedForUpdate(eq(OPERATOR_ID), eq(USER_ID), anyString())).thenReturn(0L);
        when(mapper.addGranted(eq(OPERATOR_ID), eq(USER_ID), anyString(), eq(1000L))).thenReturn(0);

        AdminRechargeLimitService service = new AdminRechargeLimitService(mapper, LIMIT);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reserve(OPERATOR_ID, USER_ID, 1000L));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    @Test
    void shouldRejectWhenMonthlyLimitWouldBeExceeded() {
        when(mapper.insertIfAbsent(anyLong(), eq(OPERATOR_ID), eq(USER_ID), anyString())).thenReturn(1);
        when(mapper.selectGrantedForUpdate(eq(OPERATOR_ID), eq(USER_ID), anyString())).thenReturn(4500L);

        AdminRechargeLimitService service = new AdminRechargeLimitService(mapper, LIMIT);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reserve(OPERATOR_ID, USER_ID, 1000L));
        assertEquals(ErrorCode.ADMIN_RECHARGE_MONTHLY_LIMIT_EXCEEDED, ex.getErrorCode());
        verify(mapper, never()).addGranted(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void shouldAccumulateWhenWithinLimit() {
        when(mapper.insertIfAbsent(anyLong(), eq(OPERATOR_ID), eq(USER_ID), anyString())).thenReturn(1);
        when(mapper.selectGrantedForUpdate(eq(OPERATOR_ID), eq(USER_ID), anyString())).thenReturn(1000L);
        when(mapper.addGranted(eq(OPERATOR_ID), eq(USER_ID), anyString(), eq(1000L))).thenReturn(1);

        AdminRechargeLimitService service = new AdminRechargeLimitService(mapper, LIMIT);

        assertEquals(2000L, service.reserve(OPERATOR_ID, USER_ID, 1000L));
    }
}
