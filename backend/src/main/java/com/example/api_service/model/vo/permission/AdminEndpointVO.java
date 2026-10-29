package com.example.api_service.model.vo.permission;

import com.example.api_service.model.entity.ApiEndpointMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "AdminEndpointVO", description = "管理员视角的 API 目录条目（API 发布管理）")
public record AdminEndpointVO(
        @Schema(description = "接口 ID") @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "接口名称") String name,
        @Schema(description = "接口路径") String path,
        @Schema(description = "请求方式") String method,
        @Schema(description = "引擎类型：INTERNAL_SQL / EXTERNAL_SQL / ES / HTTP_PROXY") String engineType,
        @Schema(description = "单次调用基础点数") Integer baseCost,
        @Schema(description = "接口描述") String description,
        @Schema(description = "接口状态：1-已上线，0-已下线（新增接口默认下线）") Integer endpointStatus,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime
) {

    public static AdminEndpointVO from(ApiEndpointMeta entity) {
        return new AdminEndpointVO(
                entity.getId(),
                entity.getName(),
                entity.getPath(),
                entity.getMethod(),
                entity.getEngineType(),
                entity.getCostPerCall(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
