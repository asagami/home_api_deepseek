package com.example.api_service.model.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "UserProfileVO", description = "当前用户信息")
public record UserProfileVO(
        @Schema(description = "用户 ID", example = "2113265835333554178")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "用户名", example = "alice") String username,
        @Schema(description = "邮箱", example = "alice@example.com") String email,
        @Schema(description = "角色", example = "USER") String role,
        @Schema(description = "剩余调用额度", example = "1000") Long balanceQuota,
        @Schema(description = "状态：1-正常，0-封禁", example = "1") Integer status,
        @Schema(description = "注册时间") LocalDateTime createTime
) {
}
