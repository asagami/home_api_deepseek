package com.example.api_service.engine.handlers.pgnew;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * PostgreSQL用户分页查询请求参数。
 */
@Getter
@Setter
@Schema(name = "PgDdlUsers1Req", description = "PostgreSQL用户分页查询请求入参")
public class PgDdlUsers1Req  {

    @Schema(description = "username", example = "postgres")
    private String username;

    @Schema(description = "superuser", example = "true")
    private Boolean superuser;
}
