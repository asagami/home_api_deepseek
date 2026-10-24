package com.example.api_service.model.dto.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "EndpointCostUpdateRequest", description = "管理员修改接口基础点数请求")
public record EndpointCostUpdateRequest(
        @NotNull(message = "基础点数不能为空")
        @Min(value = 0, message = "基础点数不能小于 0")
        @Max(value = 1_000_000, message = "基础点数不能超过 1000000")
        @Schema(description = "新的单次调用基础点数", example = "5")
        Integer costPerCall
) {
}
