package com.example.api_service.engine.handlers.pg;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * PostgreSQL 示例查询请求参数。
 */
@Getter
@Setter
@Schema(name = "PgSampleUserPageReq", description = "PostgreSQL 示例用户分页查询参数")
public class PgSampleUserPageReq extends BasePageRequest {

    @Schema(description = "按用户名模糊筛选", example = "postgres")
    @Size(max = 64, message = "username 长度不能超过 64")
    private String username;

    @Schema(description = "是否超级管理员筛选 (true/false)", example = "true")
    private Boolean superuser;
}
