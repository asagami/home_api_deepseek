package com.example.api_service.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.api_service.model.entity.ApiEndpointMeta;

public interface ApiEndpointMetaService extends IService<ApiEndpointMeta> {

    /**
     * 按路径与请求方式匹配服务目录中的接口。
     *
     * @param path   请求 URI，如 /api/v1/services/echo
     * @param method HTTP 方法，如 GET
     * @return 匹配的接口；未登记时返回 null
     */
    ApiEndpointMeta getByPathAndMethod(String path, String method);

    /**
     * 修改接口的基础点数。
     *
     * @return 更新后的接口元数据
     */
    ApiEndpointMeta updateCost(Long endpointId, int costPerCall);
}
