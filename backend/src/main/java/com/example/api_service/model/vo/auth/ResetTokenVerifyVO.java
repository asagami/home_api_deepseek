package com.example.api_service.model.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ResetTokenVerifyVO", description = "重置令牌校验结果")
public record ResetTokenVerifyVO(
        @Schema(description = "短期一次性 Reset Ticket，提交新密码时使用") String ticket,
        @Schema(description = "票据有效期（秒）", example = "600") long expiresInSeconds,
        @Schema(description = "脱敏后的注册邮箱，用于提示用户", example = "a***@example.com") String maskedEmail
) {
}
