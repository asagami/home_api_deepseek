package com.example.api_service.model.dto.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(name = "ApiKeyCreateRequest", description = "创建 API Key 请求")
public record ApiKeyCreateRequest(
        @NotBlank(message = "Key 名称不能为空")
        @Size(max = 64, message = "Key 名称长度不能超过 64")
        @Schema(description = "密钥备注名称", example = "生产环境 Key")
        String keyName,

        @Min(value = 1, message = "QPS 必须大于 0")
        @Max(value = 20, message = "QPS 不能超过 20")
        @Schema(description = "每秒最大请求数，默认 5，上限 20", example = "5")
        Integer rateLimitQps,

        @Future(message = "过期时间必须晚于当前时间")
        @Schema(description = "过期时间，null 表示永不过期", example = "2027-01-01T00:00:00")
        LocalDateTime expiredTime
) {
}
