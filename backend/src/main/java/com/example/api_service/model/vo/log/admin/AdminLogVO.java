package com.example.api_service.model.vo.log.admin;

import com.example.api_service.model.dto.log.admin.AdminLogPageRow;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "AdminLogVO", description = "管理员视角的全站调用日志")
public record AdminLogVO(
        @Schema(description = "日志 ID")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "调用者用户 ID")
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        @Schema(description = "调用者用户名") String username,
        @Schema(description = "使用的 API Key ID")
        @JsonSerialize(using = ToStringSerializer.class) Long apiKeyId,
        @Schema(description = "API Key 脱敏前缀") String keyPrefix,
        @Schema(description = "调用的接口路径", example = "/api/v1/services/echo") String endpointPath,
        @Schema(description = "接口名称，目录中不存在时为空") String endpointName,
        @Schema(description = "HTTP 状态码", example = "200") Integer httpStatus,
        @Schema(description = "本次调用实际扣减点数", example = "1") Integer costPoints,
        @Schema(description = "耗时（毫秒）", example = "12") Integer latencyMs,
        @Schema(description = "客户端 IP", example = "127.0.0.1") String clientIp,
        @Schema(description = "调用时间") LocalDateTime createTime
) {

    public static AdminLogVO from(AdminLogPageRow row) {
        return new AdminLogVO(
                row.getId(),
                row.getUserId(),
                row.getUsername(),
                row.getApiKeyId(),
                row.getKeyPrefix(),
                row.getEndpointPath(),
                row.getEndpointName(),
                row.getHttpStatus(),
                row.getCostPoints(),
                row.getLatencyMs(),
                row.getClientIp(),
                row.getCreateTime()
        );
    }
}
