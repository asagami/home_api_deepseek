package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.mapper.ApiEndpointMetaMapper;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.service.ApiEndpointMetaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class ApiEndpointMetaServiceImpl extends ServiceImpl<ApiEndpointMetaMapper, ApiEndpointMeta>
        implements ApiEndpointMetaService {

    @Override
    public ApiEndpointMeta getByPathAndMethod(String path, String method) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(method)) {
            return null;
        }
        return lambdaQuery()
                .eq(ApiEndpointMeta::getPath, normalizePath(path))
                .eq(ApiEndpointMeta::getMethod, method.trim().toUpperCase(Locale.ROOT))
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiEndpointMeta updateCost(Long endpointId, int costPerCall) {
        ApiEndpointMeta endpoint = getById(endpointId);
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        endpoint.setCostPerCall(costPerCall);
        updateById(endpoint);
        return endpoint;
    }

    @Override
    public List<ApiEndpointMeta> listForAdmin(Integer status, String keyword) {
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return lambdaQuery()
                .eq(status != null, ApiEndpointMeta::getStatus, status)
                .and(trimmedKeyword != null, wrapper -> wrapper
                        .like(ApiEndpointMeta::getName, trimmedKeyword)
                        .or()
                        .like(ApiEndpointMeta::getPath, trimmedKeyword))
                .orderByDesc(ApiEndpointMeta::getId)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiEndpointMeta updateStatus(Long endpointId, int status) {
        ApiEndpointMeta endpoint = getById(endpointId);
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (!Integer.valueOf(status).equals(endpoint.getStatus())) {
            endpoint.setStatus(status);
            updateById(endpoint);
        }
        return endpoint;
    }

    /** 去掉多余的尾部斜杠，避免 /api/v1/foo/ 与 /api/v1/foo 匹配不到同一条目录。 */
    private String normalizePath(String path) {
        String normalized = path.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
