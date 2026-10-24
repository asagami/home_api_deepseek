package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "登录请求")
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Schema(description = "用户名", example = "alice")
        String username,

        @NotBlank(message = "密码不能为空")
        @Schema(description = "密码", example = "password123")
        String password
) {
}
