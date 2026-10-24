package com.example.api_service.model.dto.permission;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员申请工单分页查询结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class AdminPermissionRow {

    private Long id;

    private Long userId;

    private String username;

    private Long endpointId;

    private String endpointName;

    private String endpointPath;

    private String endpointMethod;

    private Integer baseCost;

    private String applyReason;

    private Integer status;

    private BigDecimal customDiscount;

    private String auditRemark;

    private Long auditorId;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;
}
