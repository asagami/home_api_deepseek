package com.example.api_service.engine.handlers.pgnew;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * PostgreSQL用户分页查询响应报文。
 */
@Schema(name = "PgDdlUsers1OpenVO", description = "PostgreSQL用户分页查询出参")
public record PgDdlUsers1OpenVO(
        @Schema(description = "usesysid", example = "") String usesysid,
        @Schema(description = "usename", example = "") String usename,
        @Schema(description = "usesuper", example = "") String usesuper,
        @Schema(description = "valuntil", example = "") String valuntil
) {
}
