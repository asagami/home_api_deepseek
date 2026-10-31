package com.example.api_service.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点数充值订单表 recharge_order。
 */
@Data
@TableName("recharge_order")
public class RechargeOrder {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 平台商户订单号，全局唯一 */
    private String orderNo;

    private Long userId;

    /** 套餐档位 ID，见 RechargePackageEnum */
    private String packageId;

    /** 实付金额，单位：分 */
    private Integer amountFen;

    /** 到账总点数（基础点数 + 赠送点数） */
    private Long totalPoints;

    /** 支付渠道：ALIPAY / WECHAT；管理员充值为 ADMIN */
    private String payChannel;

    /** 订单来源：1-用户支付，2-管理员直接充值，见 RechargeOrderSource */
    private Integer source;

    /** 管理员充值时的操作人 ID，用户支付为空 */
    private Long operatorId;

    /** 操作人用户名快照，便于管理员账号变更后仍可审计 */
    private String operatorName;

    /** 充值原因/备注，用户支付为空 */
    private String remark;

    /** 0-待支付，1-已支付，2-已关闭 */
    private Integer status;

    /** 支付二维码内容串，供前端生成二维码 */
    private String qrCodeUrl;

    /** 第三方支付流水号 */
    private String tradeNo;

    private LocalDateTime payTime;

    /** 二维码过期时间，默认创建后 15 分钟 */
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
