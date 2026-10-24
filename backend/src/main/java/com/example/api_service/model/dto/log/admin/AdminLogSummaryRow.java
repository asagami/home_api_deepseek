package com.example.api_service.model.dto.log.admin;

import lombok.Data;

/**
 * 全站日志汇总聚合结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class AdminLogSummaryRow {

    private Long totalCalls;

    private Long successCalls;

    private Long totalCostPoints;

    private Long rateLimitCount;

    private Long error5xxCount;

    private Double avgLatencyMs;
}
