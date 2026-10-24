package com.example.api_service.model.dto.log.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理员全站调用日志总览请求。
 *
 * <p>时间范围为空时默认最近 7 天。查询条件直接作用于
 * api_access_log.create_time，以命中 idx_time_status_cost 索引。</p>
 */
@Schema(name = "AdminLogOverviewReq", description = "管理员全站调用日志总览请求")
public record AdminLogOverviewReq(
        @Schema(description = "开始时间（含），默认 6 天前的 00:00:00",
                example = "2026-10-18T00:00:00", nullable = true)
        LocalDateTime startTime,

        @Schema(description = "结束时间（含），默认当前时间",
                example = "2026-10-24T23:59:59", nullable = true)
        LocalDateTime endTime
) {
}
