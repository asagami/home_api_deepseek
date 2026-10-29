package com.example.api_service.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.api_service.model.entity.ApiEndpointMeta;

import java.util.List;

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

    /**
     * 管理员查询接口目录，用于 API 发布管理。
     *
     * @param status  目标状态（1-上线，0-下线）；为 null 时不过滤
     * @param keyword 按接口名称或路径模糊匹配；为空时不过滤
     */
    List<ApiEndpointMeta> listForAdmin(Integer status, String keyword);

    /**
     * 变更接口上下线状态。
     *
     * @param endpointId 接口 ID
     * @param status     目标状态：1-上线，0-下线
     * @return 更新后的接口元数据
     */
    ApiEndpointMeta updateStatus(Long endpointId, int status);
}
