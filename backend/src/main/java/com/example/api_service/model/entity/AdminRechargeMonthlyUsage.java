package com.example.api_service.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员月度充值限额使用表 admin_recharge_monthly_usage。
 *
 * <p>按 (operator_id, user_id, month_key) 唯一。每次管理员充值在同一事务内先锁定
 * 该行再校验/累加，保证并发的多次充值不会突破月度上限；订单表仍是可审计的流水。</p>
 */
@Data
@TableName("admin_recharge_monthly_usage")
public class AdminRechargeMonthlyUsage {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long operatorId;

    private Long userId;

    /** 自然月，格式 yyyy-MM */
    private String monthKey;

    /** 本月该管理员已向该账号充值的点数 */
    private Long grantedPoints;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
