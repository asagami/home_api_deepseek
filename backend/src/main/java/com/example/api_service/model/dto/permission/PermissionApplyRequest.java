package com.example.api_service.model.dto.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "PermissionApplyRequest", description = "API 权限申请请求")
public record PermissionApplyRequest(
        @NotNull(message = "接口 ID 不能为空")
        @Positive(message = "接口 ID 必须大于 0")
        @Schema(description = "申请的接口 ID", example = "1001")
        Long endpointId,

        @NotBlank(message = "申请理由不能为空")
        @Size(max = 512, message = "申请理由不能超过 512 个字符")
        @Schema(description = "申请理由与业务场景说明")
        String applyReason
) {
}
