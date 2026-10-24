package com.example.api_service.service;

import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.apikey.ApiKeyCreateRequest;
import com.example.api_service.model.vo.apikey.ApiKeyCreateVO;
import com.example.api_service.model.vo.apikey.ApiKeyVO;

public interface ApiKeyManageService {

    /**
     * 创建 API Key，明文仅在本次响应中返回。
     */
    ApiKeyCreateVO create(Long userId, ApiKeyCreateRequest request);

    /**
     * 分页查询当前用户的 Key。
     */
    PageResult<ApiKeyVO> list(Long userId, long page, long size);

    /**
     * 启用或禁用 Key。
     */
    void updateStatus(Long userId, Long apiKeyId, Integer status);

    /**
     * 逻辑删除 Key。
     */
    void delete(Long userId, Long apiKeyId);
}
