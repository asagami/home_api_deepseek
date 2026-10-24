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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreateVO create(Long userId, ApiKeyCreateRequest request) {
        String plaintextKey = ApiKeyUtils.generateApiKey();

        ApiKeyInfo keyInfo = new ApiKeyInfo();
        keyInfo.setUserId(userId);
        keyInfo.setKeyName(request.keyName().trim());
        keyInfo.setKeyPrefix(ApiKeyUtils.extractPrefix(plaintextKey));
        keyInfo.setKeyHash(ApiKeyUtils.sha256(plaintextKey));
        keyInfo.setRateLimitQps(request.rateLimitQps() == null ? DEFAULT_QPS : request.rateLimitQps());
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
