package com.example.api_service.model.dto.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "EndpointStatusUpdateRequest", description = "管理员变更 API 上下线状态请求")
public record EndpointStatusUpdateRequest(
        @NotNull(message = "接口状态不能为空")
        @Min(value = 0, message = "接口状态只能是 0-下线 或 1-上线")
        @Max(value = 1, message = "接口状态只能是 0-下线 或 1-上线")
        @Schema(description = "目标状态：1-上线，0-下线", example = "1")
        Integer status
) {
}
