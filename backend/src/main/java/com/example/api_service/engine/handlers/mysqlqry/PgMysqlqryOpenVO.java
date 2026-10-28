package com.example.api_service.engine.handlers.mysqlqry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * mysql用户分页查询响应报文。
 */
@Schema(name = "PgMysqlqryOpenVO", description = "mysql用户分页查询出参")
public record PgMysqlqryOpenVO(
        @Schema(description = "usesysid", example = "") String usesysid,
        @Schema(description = "usename", example = "") String usename,
        @Schema(description = "usesuper", example = "") String usesuper,
        @Schema(description = "valuntil", example = "") String valuntil
) {
}
