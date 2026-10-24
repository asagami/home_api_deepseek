package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.api_service.mapper.ApiKeyInfoMapper;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.service.ApiKeyInfoService;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyInfoServiceImpl extends ServiceImpl<ApiKeyInfoMapper, ApiKeyInfo>
        implements ApiKeyInfoService {

    @Override
    public ApiKeyInfo getByKeyHash(String keyHash) {
        return lambdaQuery()
                .eq(ApiKeyInfo::getKeyHash, keyHash)
                .one();
    }

    @Override
    public boolean markUsed(Long apiKeyId) {
        return baseMapper.markUsed(apiKeyId) > 0;
    }
}
