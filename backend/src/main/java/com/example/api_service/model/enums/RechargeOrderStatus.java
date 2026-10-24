package com.example.api_service.model.enums;

/**
 * recharge_order.status 的取值。
 */
public enum RechargeOrderStatus {

    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CLOSED(2, "已关闭");

    private final int code;
    private final String description;

    RechargeOrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean matches(Integer status) {
        return status != null && status == code;
    }

    /**
     * 状态码转展示文案，未知状态返回空串。
     */
    public static String describe(Integer status) {
        if (status == null) {
            return "";
        }
        for (RechargeOrderStatus value : values()) {
            if (value.code == status) {
                return value.description;
            }
        }
        return "";
    }
}
