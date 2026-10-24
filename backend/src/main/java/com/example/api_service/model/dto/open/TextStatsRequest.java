package com.example.api_service.model.dto.open;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "TextStatsRequest", description = "文本统计请求")
public record TextStatsRequest(
        @NotBlank(message = "文本不能为空")
        @Size(max = 20000, message = "文本长度不能超过 20000")
        @Schema(description = "待统计文本", example = "Hello world, hello API!")
        String text
) {
}
