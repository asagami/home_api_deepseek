package com.example.api_service.model.dto.log.admin;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理员全站调用日志分页请求。
 *
 * <p>时间范围必填：api_access_log 没有针对全表排序的索引，
 * 缺少 create_time 条件会退化为全表扫描。</p>
 */
@Getter
@Setter
@Schema(name = "AdminLogPageReq", description = "管理员全站调用日志分页请求")
public class AdminLogPageReq extends BasePageRequest {

    @Schema(description = "调用者用户 ID", nullable = true)
    @Positive(message = "用户 ID 必须大于 0")
    private Long userId;

    @Schema(description = "调用者用户名，模糊匹配", nullable = true)
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @Schema(description = "接口路径，精确匹配", example = "/api/v1/services/echo", nullable = true)
    @Size(max = 128, message = "接口路径长度不能超过 128")
    private String endpointPath;

    @Schema(description = "精确匹配的 HTTP 状态码", example = "200", nullable = true)
    @Min(value = 100, message = "状态码最小为 100")
    @Max(value = 599, message = "状态码最大为 599")
    private Integer httpStatus;

    @Schema(description = "最小耗时（毫秒），用于筛选慢请求", example = "500", nullable = true)
    @Min(value = 0, message = "最小耗时不能小于 0")
    private Integer minLatencyMs;

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "调用时间下界（含）", example = "2026-10-18T00:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "调用时间上界（含）", example = "2026-10-24T23:59:59", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;
}
