package com.example.api_service.model.enums;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;

import java.util.Locale;

/**
 * 充值订单的支付渠道。
 */
public enum PayChannelEnum {

    ALIPAY("支付宝"),
    WECHAT("微信支付");

    private final String displayName;

    PayChannelEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 按渠道编码查找，忽略大小写与首尾空白。
     *
     * @throws BusinessException 渠道为空或不受支持
     */
    public static PayChannelEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.PAY_CHANNEL_NOT_SUPPORTED, "支付渠道不能为空");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (PayChannelEnum value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.PAY_CHANNEL_NOT_SUPPORTED,
                "不支持的支付渠道: " + code);
    }

    /**
     * 渠道编码回显；未知渠道返回原始编码，避免历史数据展示异常。
     */
    public static String displayNameOf(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        for (PayChannelEnum value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value.displayName;
            }
        }
        return code;
    }
}
