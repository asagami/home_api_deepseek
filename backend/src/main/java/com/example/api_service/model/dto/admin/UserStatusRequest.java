package com.example.api_service.model.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UserStatusRequest", description = "管理员启用/封禁用户请求")
public record UserStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能是 0（封禁）或 1（正常）")
        @Max(value = 1, message = "状态只能是 0（封禁）或 1（正常）")
        @Schema(description = "1-正常，0-封禁", example = "0")
        Integer status
) {
}
