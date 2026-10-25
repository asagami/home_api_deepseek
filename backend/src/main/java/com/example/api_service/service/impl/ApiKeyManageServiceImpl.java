package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.apikey.ApiKeyCreateRequest;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.vo.apikey.ApiKeyCreateVO;
import com.example.api_service.model.vo.apikey.ApiKeyVO;
import com.example.api_service.security.ApiKeyUtils;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.ApiKeyManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyManageServiceImpl implements ApiKeyManageService {

    private static final int DEFAULT_QPS = 5;
    private static final long SHARED_ACCOUNT_QUOTA = -1L;
    private static final int STATUS_ENABLED = 1;

    private final ApiKeyInfoService apiKeyInfoService;

    @Value("${app.apikey.max-per-user:5}")
    private int maxKeysPerUser;

    @Value("${app.apikey.max-qps:20}")
    private int maxQps;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreateVO create(Long userId, ApiKeyCreateRequest request) {
        // 限制每用户 API Key 数量，防止批量建 Key 成倍抬高总 QPS
        long existingCount = apiKeyInfoService.lambdaQuery()
                .eq(ApiKeyInfo::getUserId, userId)
                .count();
        if (existingCount >= maxKeysPerUser) {
            throw new BusinessException(ErrorCode.API_KEY_LIMIT_EXCEEDED,
                    "每用户最多创建 " + maxKeysPerUser + " 个 API Key，请先删除不再使用的 Key");
        }

        String plaintextKey = ApiKeyUtils.generateApiKey();

        // QPS 兜底校验：DTO 层 @Max 已限制，此处再次防御确保不超上限
        int rateLimitQps = request.rateLimitQps() == null ? DEFAULT_QPS : request.rateLimitQps();
        if (rateLimitQps > maxQps) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "QPS 不能超过 " + maxQps);
        }

        ApiKeyInfo keyInfo = new ApiKeyInfo();
        keyInfo.setUserId(userId);
        keyInfo.setKeyName(request.keyName().trim());
        keyInfo.setKeyPrefix(ApiKeyUtils.extractPrefix(plaintextKey));
        keyInfo.setKeyHash(ApiKeyUtils.sha256(plaintextKey));
        keyInfo.setRateLimitQps(rateLimitQps);
        keyInfo.setTotalQuota(SHARED_ACCOUNT_QUOTA);
        keyInfo.setUsedQuota(0L);
        keyInfo.setExpiredTime(request.expiredTime());
        keyInfo.setStatus(STATUS_ENABLED);
        keyInfo.setIsDeleted(0);
        apiKeyInfoService.save(keyInfo);

        return new ApiKeyCreateVO(
                keyInfo.getId(),
                keyInfo.getKeyName(),
                keyInfo.getKeyPrefix(),
                plaintextKey,
                keyInfo.getRateLimitQps(),
                keyInfo.getTotalQuota(),
                keyInfo.getUsedQuota(),
                keyInfo.getExpiredTime(),
                keyInfo.getStatus(),
                keyInfo.getCreateTime()
        );
    }

    @Override
    public PageResult<ApiKeyVO> list(Long userId, long page, long size) {
        Page<ApiKeyInfo> pageRequest = new Page<>(page, size);
        Page<ApiKeyInfo> result = apiKeyInfoService.lambdaQuery()
                .eq(ApiKeyInfo::getUserId, userId)
                .orderByDesc(ApiKeyInfo::getCreateTime)
                .page(pageRequest);

        List<ApiKeyVO> records = result.getRecords().stream()
                .map(ApiKeyVO::from)
                .toList();
        return PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Long apiKeyId, Integer status) {
        ApiKeyInfo keyInfo = getOwnedKey(userId, apiKeyId);
        keyInfo.setStatus(status);
        apiKeyInfoService.updateById(keyInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long apiKeyId) {
        getOwnedKey(userId, apiKeyId);
        apiKeyInfoService.removeById(apiKeyId);
    }

    private ApiKeyInfo getOwnedKey(Long userId, Long apiKeyId) {
        ApiKeyInfo keyInfo = apiKeyInfoService.lambdaQuery()
                .eq(ApiKeyInfo::getId, apiKeyId)
                .eq(ApiKeyInfo::getUserId, userId)
                .one();
        if (keyInfo == null) {
            throw new BusinessException(ErrorCode.API_KEY_NOT_FOUND);
        }
        return keyInfo;
    }
}
