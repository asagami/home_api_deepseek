package com.example.api_service.model.dto.log.admin;

import lombok.Data;

/**
 * 接口调用排行聚合结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class AdminLogEndpointRankRow {

    private String endpointPath;

    private String endpointName;

    private Long callCount;

    private Long successCalls;

    private Long costPoints;

    private Long rateLimitCount;

    private Long error5xxCount;

    private Double avgLatencyMs;
}
