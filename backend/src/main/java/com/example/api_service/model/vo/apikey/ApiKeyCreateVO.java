package com.example.api_service.model.vo.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "ApiKeyCreateVO", description = "创建 API Key 结果（明文仅此一次返回）")
public record ApiKeyCreateVO(
        @Schema(description = "Key ID", example = "2113263705721819138")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "Key 名称") String keyName,
        @Schema(description = "脱敏前缀") String keyPrefix,
        @Schema(description = "完整明文 Key，仅创建时返回一次，请立即保存") String apiKey,
        @Schema(description = "QPS 限制") Integer rateLimitQps,
        @Schema(description = "该 Key 总额度，-1 表示共享账户额度") Long totalQuota,
        @Schema(description = "已使用次数") Long usedQuota,
        @Schema(description = "过期时间") LocalDateTime expiredTime,
        @Schema(description = "1-启用，0-禁用") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime
        
) {
}
