package com.example.api_service.model.vo.admin;

import com.example.api_service.model.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "AdminUserVO", description = "管理员视角的用户信息")
public record AdminUserVO(
        @Schema(description = "用户 ID", example = "2113265835333554178")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "邮箱") String email,
        @Schema(description = "角色：USER / ADMIN") String role,
        @Schema(description = "剩余调用额度") Long balanceQuota,
        @Schema(description = "状态：1-正常，0-封禁") Integer status,
        @Schema(description = "注册时间") LocalDateTime createTime,
        @Schema(description = "最后更新时间") LocalDateTime updateTime
) {

    public static AdminUserVO from(SysUser entity) {
        return new AdminUserVO(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getRole(),
                entity.getBalanceQuota(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
