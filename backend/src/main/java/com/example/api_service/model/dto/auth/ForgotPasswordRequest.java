package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ForgotPasswordRequest", description = "找回密码申请")
public record ForgotPasswordRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 64, message = "账号长度不能超过 64")
        @Schema(description = "用户名（账号）", example = "alice")
        String account,

        @Schema(description = "Cloudflare Turnstile 人机校验 token，未启用校验时可为空")
        String captchaToken
) {
}
