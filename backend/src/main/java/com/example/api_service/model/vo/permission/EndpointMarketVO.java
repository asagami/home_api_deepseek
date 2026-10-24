package com.example.api_service.model.vo.permission;

import com.example.api_service.common.PointCostCalculator;
import com.example.api_service.model.dto.permission.EndpointMarketRow;
import com.example.api_service.model.enums.ApiPermissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "EndpointMarketVO", description = "接口市场条目（含当前用户的申请状态）")
public record EndpointMarketVO(
        @Schema(description = "接口 ID") @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "接口名称") String name,
        @Schema(description = "接口路径") String path,
        @Schema(description = "请求方式") String method,
        @Schema(description = "接口描述") String description,
        @Schema(description = "基础点数") Integer baseCost,
        @Schema(description = "当前用户实际适用的折后点数；未通过审批时等于基础点数") Integer discountedCost,
        @Schema(description = "当前生效的折扣率") BigDecimal discountRate,
        @Schema(description = "申请状态：0-待审批，1-已通过，2-已驳回，3-已收回；null 表示未申请")
        Integer applyStatus,
        @Schema(description = "审批意见 / 驳回原因") String auditRemark,
        @Schema(description = "本次申请提交时间") LocalDateTime applyTime,
        @Schema(description = "接口状态：1-上线，0-维护中") Integer endpointStatus,
        @Schema(description = "请求参数示例或 JSON Schema") String requestSchema,
        @Schema(description = "返回结果示例") String responseSchema
) {

    private static final BigDecimal FULL_PRICE = BigDecimal.ONE;

    public static EndpointMarketVO from(EndpointMarketRow row) {
        int baseCost = row.getBaseCost() == null ? 0 : row.getBaseCost();
        boolean approved = ApiPermissionStatus.isApproved(row.getApplyStatus());
        BigDecimal discountRate = approved && row.getCustomDiscount() != null
                ? row.getCustomDiscount()
                : FULL_PRICE;

        return new EndpointMarketVO(
                row.getEndpointId(),
                row.getEndpointName(),
                row.getEndpointPath(),
                row.getEndpointMethod(),
                row.getDescription(),
                baseCost,
                PointCostCalculator.actualCost(baseCost, discountRate),
                discountRate,
                row.getApplyStatus(),
                row.getAuditRemark(),
                row.getApplyTime(),
                row.getEndpointStatus(),
                row.getRequestSchema(),
                row.getResponseSchema()
        );
    }
}
