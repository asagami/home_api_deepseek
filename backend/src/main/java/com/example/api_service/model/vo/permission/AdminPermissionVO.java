package com.example.api_service.model.vo.permission;

import com.example.api_service.common.PointCostCalculator;
import com.example.api_service.model.dto.permission.AdminPermissionRow;
import com.example.api_service.model.enums.ApiPermissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "AdminPermissionVO", description = "管理员视角的权限申请工单")
public record AdminPermissionVO(
        @Schema(description = "工单 ID") @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "申请人用户 ID") @JsonSerialize(using = ToStringSerializer.class) Long userId,
        @Schema(description = "申请人用户名") String username,
        @Schema(description = "接口 ID") @JsonSerialize(using = ToStringSerializer.class) Long endpointId,
        @Schema(description = "接口名称") String endpointName,
        @Schema(description = "接口路径") String endpointPath,
        @Schema(description = "请求方式") String endpointMethod,
        @Schema(description = "接口基础点数") Integer baseCost,
        @Schema(description = "申请理由") String applyReason,
        @Schema(description = "申请状态：0-待审批，1-已通过，2-已驳回，3-已收回") Integer status,
        @Schema(description = "专属折扣率") BigDecimal customDiscount,
        @Schema(description = "当前生效的单次扣费点数；未通过审批时等于基础点数") Integer actualCost,
        @Schema(description = "审批意见 / 驳回原因") String auditRemark,
        @Schema(description = "审批管理员 ID") @JsonSerialize(using = ToStringSerializer.class) Long auditorId,
        @Schema(description = "审批时间") LocalDateTime auditTime,
        @Schema(description = "申请时间") LocalDateTime createTime
) {

    private static final BigDecimal FULL_PRICE = BigDecimal.ONE;

    public static AdminPermissionVO from(AdminPermissionRow row) {
        int baseCost = row.getBaseCost() == null ? 0 : row.getBaseCost();
        BigDecimal discountRate = ApiPermissionStatus.isApproved(row.getStatus())
                && row.getCustomDiscount() != null
                ? row.getCustomDiscount()
                : FULL_PRICE;

        return new AdminPermissionVO(
                row.getId(),
                row.getUserId(),
                row.getUsername(),
                row.getEndpointId(),
                row.getEndpointName(),
                row.getEndpointPath(),
                row.getEndpointMethod(),
                baseCost,
                row.getApplyReason(),
                row.getStatus(),
                row.getCustomDiscount(),
                PointCostCalculator.actualCost(baseCost, discountRate),
                row.getAuditRemark(),
                row.getAuditorId(),
                row.getAuditTime(),
                row.getCreateTime()
        );
    }
}
