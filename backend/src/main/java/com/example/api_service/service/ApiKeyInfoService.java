package com.example.api_service.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.api_service.model.entity.ApiKeyInfo;

public interface ApiKeyInfoService extends IService<ApiKeyInfo> {

    /**
     * 按 API Key 明文的 SHA-256 哈希查询密钥。
     *
     * @param keyHash SHA-256 十六进制摘要
     * @return 匹配的密钥，不存在时返回 null
     */
    ApiKeyInfo getByKeyHash(String keyHash);

    /**
     * 累加 Key 的使用次数并刷新最后调用时间。
     *
     * @return true 更新成功；false Key 不存在或已删除
     */
    boolean markUsed(Long apiKeyId);
}
