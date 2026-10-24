package com.example.api_service.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应包装类。
 *
 * @param code      业务码，0 表示成功
 * @param message   提示信息
 * @param data      业务数据
 * @param timestamp 服务端时间戳（毫秒）
 */
@Schema(name = "Result", description = "统一响应结构")
public record Result<T>(
        @Schema(description = "业务码，0 表示成功", example = "0") int code,
        @Schema(description = "提示信息", example = "success") String message,
        @Schema(description = "业务数据") T data,
        @Schema(description = "服务端时间戳（毫秒）", example = "1735689600000") long timestamp
) {

    public static <T> Result<T> success(T data) {
        return of(data);
    }

    public static Result<Void> success() {
        return empty();
    }

    /**
     * 成功响应，携带业务数据。
     */
    public static <T> Result<T> of(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data,
                System.currentTimeMillis());
    }

    /**
     * 成功响应，但无业务数据。
     */
    public static <T> Result<T> empty() {
        return of(null);
    }

    /**
     * 自定义业务码的响应，便于透传上游错误码或做局部定制。
     */
    public static <T> Result<T> of(int code, String message, T data) {
        return new Result<>(code, message, data, System.currentTimeMillis());
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return failure(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String message) {
        return failure(errorCode.getCode(), message);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }
}
