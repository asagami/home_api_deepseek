package com.example.api_service.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户 API 权限申请与专属计费表 user_api_permission。
 */
@Data
@TableName("user_api_permission")
public class UserApiPermission {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 申请人 UserID */
    private Long userId;

    /** 申请的 API 服务 ID，关联 api_endpoint_meta.id */
    private Long endpointId;

    /** 申请理由与业务场景说明 */
    private String applyReason;

    /** 0-待审批，1-已通过，2-已驳回，3-已收回 */
    private Integer status;

    /** 专属折扣率：1.00 为原价，0.80 为 8 折，0.00 为免费 */
    private BigDecimal customDiscount;

    /** 管理员审批意见 / 驳回原因 */
    private String auditRemark;

    private Long auditorId;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
