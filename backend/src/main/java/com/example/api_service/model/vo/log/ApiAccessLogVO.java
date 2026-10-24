package com.example.api_service.model.vo.log;

import com.example.api_service.model.entity.ApiAccessLog;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "ApiAccessLogVO", description = "API 调用日志")
public record ApiAccessLogVO(
        @Schema(description = "日志 ID") @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "调用者用户 ID") @JsonSerialize(using = ToStringSerializer.class) Long userId,
        @Schema(description = "使用的 API Key ID") @JsonSerialize(using = ToStringSerializer.class) Long apiKeyId,
        @Schema(description = "调用的接口路径", example = "/api/v1/services/echo") String endpointPath,
        @Schema(description = "HTTP 状态码", example = "200") Integer httpStatus,
        @Schema(description = "本次调用实际扣减点数", example = "1") Integer costPoints,
        @Schema(description = "耗时（毫秒）", example = "12") Integer latencyMs,
        @Schema(description = "客户端 IP", example = "127.0.0.1") String clientIp,
        @Schema(description = "调用时间") LocalDateTime createTime
) {

    public static ApiAccessLogVO from(ApiAccessLog entity) {
        return new ApiAccessLogVO(
                entity.getId(),
                entity.getUserId(),
                entity.getApiKeyId(),
                entity.getEndpointPath(),
                entity.getHttpStatus(),
                entity.getCostPoints(),
                entity.getLatencyMs(),
                entity.getClientIp(),
                entity.getCreateTime()
        );
    }
}
