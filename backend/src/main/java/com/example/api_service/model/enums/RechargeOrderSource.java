package com.example.api_service.model.enums;

/**
 * recharge_order.source 的取值：区分用户支付充值与管理员直接充值。
 */
public enum RechargeOrderSource {

    USER_PAY(1, "用户充值"),
    ADMIN_GRANT(2, "管理员发放");

    private final int code;
    private final String description;

    RechargeOrderSource(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean matches(Integer source) {
        return source != null && source == code;
    }

    public static boolean isAdminGrant(Integer source) {
        return ADMIN_GRANT.matches(source);
    }

    /**
     * 来源文案；未知或空值按用户充值处理，兼容迁移前的历史订单。
     */
    public static String describe(Integer source) {
        return isAdminGrant(source) ? ADMIN_GRANT.description : USER_PAY.description;
    }

    /**
     * 管理员充值不是支付，历史列表用“已到账”而不是“已支付”。
     */
    public static String statusDescOf(Integer source, Integer status) {
        if (isAdminGrant(source) && RechargeOrderStatus.PAID.matches(status)) {
            return "已到账";
        }
        return RechargeOrderStatus.describe(status);
    }

    /**
     * 管理员充值没有支付渠道，展示为“管理员发放”。
     */
    public static String channelNameOf(Integer source, String payChannel) {
        if (isAdminGrant(source)) {
            return ADMIN_GRANT.description;
        }
        return PayChannelEnum.displayNameOf(payChannel);
    }
}
