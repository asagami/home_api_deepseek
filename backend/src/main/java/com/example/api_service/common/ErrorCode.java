package com.example.api_service.common;

import org.springframework.http.HttpStatus;

/**
 * 全局业务错误码。
 *
 * <p>code 为业务码，httpStatus 为对应的 HTTP 状态码。业务码与 HTTP 状态码分离，
 * 便于前端在不依赖 HTTP 语义的情况下做细粒度处理。</p>
 */
public enum ErrorCode {

    SUCCESS(0, "success", HttpStatus.OK),
    PARAM_ERROR(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    EMAIL_CODE_INVALID(40001, "邮箱验证码不正确或已过期", HttpStatus.BAD_REQUEST),
    RECHARGE_PACKAGE_INVALID(40002, "不支持的充值套餐档位", HttpStatus.BAD_REQUEST),
    PAY_CHANNEL_NOT_SUPPORTED(40003, "不支持的支付渠道", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未认证或登录已过期", HttpStatus.UNAUTHORIZED),
    API_KEY_MISSING(40101, "缺少 API Key", HttpStatus.UNAUTHORIZED),
    API_KEY_INVALID(40102, "无效的 API Key", HttpStatus.UNAUTHORIZED),
    API_KEY_DISABLED(40103, "API Key 已被禁用", HttpStatus.UNAUTHORIZED),
    API_KEY_EXPIRED(40104, "API Key 已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_MISSING(40105, "缺少访问令牌", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40106, "访问令牌无效或已过期", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40107, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    QUOTA_INSUFFICIENT(40200, "账户剩余额度不足", HttpStatus.PAYMENT_REQUIRED),
    QUOTA_EXCEEDED(40201, "账户剩余点数不足以支付本次调用", HttpStatus.PAYMENT_REQUIRED),
    FORBIDDEN(40300, "没有访问权限", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(40301, "账户已被封禁", HttpStatus.FORBIDDEN),
    QUOTA_EXHAUSTED(40302, "账户剩余额度不足", HttpStatus.FORBIDDEN),
    OPERATION_NOT_ALLOWED(40303, "不允许执行该操作", HttpStatus.FORBIDDEN),
    API_PERMISSION_DENIED(40304, "未获得该接口的调用权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    API_KEY_NOT_FOUND(40401, "API Key 不存在", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(40402, "用户不存在", HttpStatus.NOT_FOUND),
    PERMISSION_RECORD_NOT_FOUND(40403, "权限申请记录不存在", HttpStatus.NOT_FOUND),
    ENDPOINT_NOT_FOUND(40404, "接口不存在或未在服务目录中登记", HttpStatus.NOT_FOUND),
    RECHARGE_ORDER_NOT_FOUND(40405, "充值订单不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40900, "资源状态冲突", HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS(40901, "用户名已存在", HttpStatus.CONFLICT),
    PERMISSION_ALREADY_PENDING(40902, "该接口已有待审批的申请", HttpStatus.CONFLICT),
    PERMISSION_ALREADY_APPROVED(40903, "已拥有该接口的权限", HttpStatus.CONFLICT),
    RECHARGE_ORDER_ALREADY_PAID(40904, "订单已支付，请勿重复提交", HttpStatus.CONFLICT),
    RECHARGE_ORDER_CLOSED(40905, "订单已关闭，无法支付", HttpStatus.CONFLICT),
    RECHARGE_ORDER_EXPIRED(40906, "订单已超时，请重新下单", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(42900, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_CODE_SEND_TOO_FREQUENT(42901, "验证码发送过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_SEND_FAILED(50001, "验证码邮件发送失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    UPSTREAM_ERROR(50400, "上游服务异常，请稍后重试", HttpStatus.GATEWAY_TIMEOUT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
