package com.example.api_service.model.dto.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ApiKeyStatusRequest", description = "修改 API Key 状态请求")
public record ApiKeyStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能是 0（禁用）或 1（启用）")
        @Max(value = 1, message = "状态只能是 0（禁用）或 1（启用）")
        @Schema(description = "1-启用，0-禁用", example = "0")
        Integer status
) {
}
