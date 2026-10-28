package com.example.api_service.engine.handlers.esqry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * es用户分页查询响应报文。
 */
@Schema(name = "EsEsqryOpenVO", description = "es用户分页查询出参")
public record EsEsqryOpenVO(
        @Schema(description = "用户ID", example = "10") String userId,
        @Schema(description = "用户名", example = "postgres") String username,
        @Schema(description = "是否超级用户", example = "true") Boolean superuser,
        @Schema(description = "过期时间", example = "infinity") String validUntil
) {
}
