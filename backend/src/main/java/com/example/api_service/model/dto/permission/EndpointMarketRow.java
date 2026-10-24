package com.example.api_service.model.dto.permission;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接口市场左连接查询结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class EndpointMarketRow {

    private Long endpointId;

    private String endpointName;

    private String endpointPath;

    private String endpointMethod;

    private Integer baseCost;

    private String description;

    private String requestSchema;

    private String responseSchema;

    private Integer endpointStatus;

    private Long permissionId;

    private Integer applyStatus;

    private BigDecimal customDiscount;

    private String auditRemark;

    private LocalDateTime applyTime;
}
