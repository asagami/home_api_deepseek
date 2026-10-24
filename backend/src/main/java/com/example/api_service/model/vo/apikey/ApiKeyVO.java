package com.example.api_service.model.vo.apikey;

import com.example.api_service.model.entity.ApiKeyInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "ApiKeyVO", description = "API Key 列表项（不含明文与哈希）")
public record ApiKeyVO(
        @Schema(description = "Key ID", example = "2113263705721819138")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "Key 名称") String keyName,
        @Schema(description = "脱敏前缀") String keyPrefix,
        @Schema(description = "QPS 限制") Integer rateLimitQps,
        @Schema(description = "该 Key 总额度，-1 表示共享账户额度") Long totalQuota,
        @Schema(description = "已使用次数") Long usedQuota,
        @Schema(description = "过期时间") LocalDateTime expiredTime,
        @Schema(description = "最后调用时间") LocalDateTime lastUsedTime,
        @Schema(description = "1-启用，0-禁用") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime
) {

    public static ApiKeyVO from(ApiKeyInfo entity) {
        return new ApiKeyVO(
                entity.getId(),
                entity.getKeyName(),
                entity.getKeyPrefix(),
                entity.getRateLimitQps(),
                entity.getTotalQuota(),
                entity.getUsedQuota(),
                entity.getExpiredTime(),
                entity.getLastUsedTime(),
                entity.getStatus(),
                entity.getCreateTime()
        );
    }
}
