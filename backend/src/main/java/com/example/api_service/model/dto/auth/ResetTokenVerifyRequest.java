package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetTokenVerifyRequest", description = "重置链接令牌校验请求")
public record ResetTokenVerifyRequest(
        @NotBlank(message = "重置令牌不能为空")
        @Size(max = 256, message = "重置令牌格式不正确")
        @Schema(description = "邮件链接中的重置令牌（含签名）")
        String token
) {
}
