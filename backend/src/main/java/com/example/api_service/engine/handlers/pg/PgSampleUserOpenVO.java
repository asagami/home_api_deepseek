package com.example.api_service.engine.handlers.pg;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PostgreSQL 示例用户出参。
 */
@Schema(name = "PgSampleUserOpenVO", description = "PostgreSQL 示例用户响应报文")
public record PgSampleUserOpenVO(
        @Schema(description = "用户ID (PG usesysid)", example = "10") String userId,
        @Schema(description = "用户名 (PG usename)", example = "postgres") String username,
        @Schema(description = "是否超级用户 (PG usesuper)", example = "true") Boolean superuser,
        @Schema(description = "是否允许直接修改系统目录 (PG usecatupd)", example = "true") Boolean canUpdateCatalog,
        @Schema(description = "账户过期时间 (PG valuntil)", example = "infinity") String validUntil
) {
}
