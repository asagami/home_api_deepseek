package com.example.api_service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 健康检查响应。
 *
 * @param status    服务状态，UP 表示正常
 * @param service   服务名称
 * @param version   应用版本
 * @param timestamp 检查时间（毫秒）
 */
@Schema(name = "HealthVO", description = "健康检查信息")
public record HealthVO(
        @Schema(description = "服务状态", example = "UP") String status,
        @Schema(description = "服务名称", example = "api-service") String service,
        @Schema(description = "应用版本", example = "0.0.1-SNAPSHOT") String version,
        @Schema(description = "检查时间（毫秒）", example = "1735689600000") long timestamp
) {
}
