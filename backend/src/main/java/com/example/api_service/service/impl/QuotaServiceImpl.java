package com.example.api_service.service.impl;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.billing.QuotaLedger;
import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.QuotaService;
import com.example.api_service.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final SysUserService sysUserService;
    private final ApiKeyInfoService apiKeyInfoService;
    private final ApiAccessLogService apiAccessLogService;
    private final QuotaLedger quotaLedger;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long userId, Long apiKeyId, String endpointPath,
                        int httpStatus, int costPoints, long latencyMs, String clientIp) {
        int actualCost = Math.max(0, costPoints);
        // 条件 UPDATE：余额不足时不扣减，避免并发下把余额扣成负数
        boolean deducted = sysUserService.deductQuota(userId, actualCost);
        if (!deducted) {
            throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        }
        // 遗留直扣路径绕过了 Redis 账本，扣后必须失效缓存键，否则网关热路径会读到陈旧的高余额。
        // 事务回滚也无妨：缓存未命中会从（未变的）MySQL 重新装载正确余额。
        quotaLedger.invalidate(userId);

        boolean keyUpdated = apiKeyInfoService.markUsed(apiKeyId);
        if (!keyUpdated) {
            throw new BusinessException(ErrorCode.API_KEY_INVALID, "API Key 已失效");
        }

        ApiAccessLog accessLog = new ApiAccessLog();
        accessLog.setUserId(userId);
        accessLog.setApiKeyId(apiKeyId);
        accessLog.setEndpointPath(endpointPath);
        accessLog.setHttpStatus(httpStatus);
        accessLog.setCostPoints(actualCost);
        accessLog.setLatencyMs((int) Math.max(0, Math.min(latencyMs, Integer.MAX_VALUE)));
        accessLog.setClientIp(clientIp);
        apiAccessLogService.save(accessLog);

        log.debug("调用计量完成: userId={}, keyId={}, path={}, costPoints={}, latencyMs={}",
                userId, apiKeyId, endpointPath, actualCost, latencyMs);
    }
}
