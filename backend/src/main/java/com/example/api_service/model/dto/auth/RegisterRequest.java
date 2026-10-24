package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "注册请求")
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度需在 3-64 之间")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        @Schema(description = "用户名", example = "alice")
        String username,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        @Schema(description = "邮箱", example = "alice@example.com")
        String email,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
        @Schema(description = "邮箱验证码", example = "123456")
        String code,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        @Schema(description = "登录密码（明文只在传输中使用，服务端存 BCrypt 哈希）", example = "password123")
        String password
) {
}
