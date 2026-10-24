package com.example.api_service.model.dto.log.admin;

import lombok.Data;

import java.time.LocalDate;

/**
 * 全站日志按天聚合结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class AdminLogDailyTrendRow {

    private LocalDate statDate;

    private Long totalCalls;

    private Long successCalls;

    private Long rateLimitCount;

    private Long error5xxCount;

    private Long costPoints;

    private Double avgLatencyMs;
}
