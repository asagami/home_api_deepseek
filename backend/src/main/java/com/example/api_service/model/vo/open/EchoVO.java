package com.example.api_service.model.vo.open;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EchoVO", description = "回声测试结果")
public record EchoVO(
        @Schema(description = "原样返回的 message", example = "Hello, API!") String message,
        @Schema(description = "服务端时间戳（毫秒）", example = "1735689600000") long timestamp
) {
}
