package com.example.api_service.model.vo.log.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Schema(name = "UserConsumeRankItem", description = "用户点数消耗排行项")
public record UserConsumeRankItem(
        @Schema(description = "用户 ID")
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "消耗点数") long costPoints,
        @Schema(description = "调用次数") long callCount
) {
}
