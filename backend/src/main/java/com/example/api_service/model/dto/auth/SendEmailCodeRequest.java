package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "SendEmailCodeRequest", description = "发送邮箱验证码请求")
public record SendEmailCodeRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        @Schema(description = "接收验证码的邮箱", example = "alice@example.com")
        String email
) {
}
