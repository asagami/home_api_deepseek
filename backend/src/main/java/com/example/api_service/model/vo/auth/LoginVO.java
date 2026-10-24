package com.example.api_service.model.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginVO", description = "登录结果")
public record LoginVO(
        @Schema(description = "JWT 访问令牌") String accessToken,
        @Schema(description = "令牌类型", example = "Bearer") String tokenType,
        @Schema(description = "有效期（毫秒）", example = "86400000") long expiresIn
) {
}
