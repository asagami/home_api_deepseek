package com.example.api_service.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest", description = "修改密码请求")
public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        @Size(max = 64, message = "旧密码格式不正确")
        @Schema(description = "当前密码", example = "OldPass2026")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        @Schema(description = "新密码（需包含大小写字母和数字，且不能与当前密码相同）",
                example = "NewPass2026")
        String newPassword
) {
}
