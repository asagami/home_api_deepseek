package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetPasswordRequest", description = "提交新密码请求")
public record ResetPasswordRequest(
        @NotBlank(message = "重置票据不能为空")
        @Size(max = 256, message = "重置票据格式不正确")
        @Schema(description = "令牌校验通过后换取的一次性票据")
        String ticket,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        @Schema(description = "新密码（明文只在传输中使用，服务端存 BCrypt 哈希）", example = "newPassword123")
        String newPassword
) {
}
