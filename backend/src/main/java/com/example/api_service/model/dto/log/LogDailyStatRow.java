package com.example.api_service.model.dto.log;

import lombok.Data;

import java.time.LocalDate;

/**
 * 日志按天聚合的查询结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class LogDailyStatRow {

    private LocalDate statDate;

    private Long totalCalls;

    private Long successCalls;

    private Long clientErrorCalls;

    private Long serverErrorCalls;

    private Double avgLatencyMs;
}
