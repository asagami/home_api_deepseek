package com.example.api_service.model.vo.permission;

import com.example.api_service.model.entity.UserApiPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "UserPermissionVO", description = "当前用户的单条权限申请记录")
public record UserPermissionVO(
        @Schema(description = "权限记录 ID") @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "接口 ID") @JsonSerialize(using = ToStringSerializer.class) Long endpointId,
        @Schema(description = "申请状态：0-待审批，1-已通过，2-已驳回，3-已收回") Integer status,
        @Schema(description = "申请理由") String applyReason,
        @Schema(description = "专属折扣率") BigDecimal customDiscount,
        @Schema(description = "审批意见 / 驳回原因") String auditRemark,
        @Schema(description = "申请时间") LocalDateTime createTime,
        @Schema(description = "审批时间") LocalDateTime auditTime
) {

    public static UserPermissionVO from(UserApiPermission entity) {
        return new UserPermissionVO(
                entity.getId(),
                entity.getEndpointId(),
                entity.getStatus(),
                entity.getApplyReason(),
                entity.getCustomDiscount(),
                entity.getAuditRemark(),
                entity.getCreateTime(),
                entity.getAuditTime()
        );
    }
}
