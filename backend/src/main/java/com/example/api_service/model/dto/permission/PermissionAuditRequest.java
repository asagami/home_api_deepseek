package com.example.api_service.model.dto.permission;

import com.example.api_service.model.enums.PermissionAuditAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "PermissionAuditRequest", description = "管理员审批申请请求")
public record PermissionAuditRequest(
        @NotNull(message = "审批动作不能为空")
        @Schema(description = "审批动作：APPROVE-通过，REJECT-驳回，REVOKE-收回权限",
                allowableValues = {"APPROVE", "REJECT", "REVOKE"})
        PermissionAuditAction action,

        @DecimalMin(value = "0.00", message = "折扣率不能小于 0")
        @DecimalMax(value = "1.00", message = "折扣率不能大于 1")
        @Digits(integer = 1, fraction = 2, message = "折扣率最多保留两位小数")
        @Schema(description = "专属折扣率，仅 APPROVE 时生效；1.00 原价，0.80 八折，0.00 免费",
                example = "0.80")
        BigDecimal customDiscount,

        @Size(max = 512, message = "审批意见不能超过 512 个字符")
        @Schema(description = "审批意见；REJECT 时必填")
        String auditRemark
) {
}
