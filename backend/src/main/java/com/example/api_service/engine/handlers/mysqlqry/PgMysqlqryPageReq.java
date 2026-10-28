package com.example.api_service.engine.handlers.mysqlqry;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * mysql用户分页查询请求参数。
 */
@Getter
@Setter
@Schema(name = "PgMysqlqryPageReq", description = "mysql用户分页查询请求入参")
public class PgMysqlqryPageReq extends BasePageRequest {

    @Schema(description = "username", example = "postgres")
    private String username;

    @Schema(description = "superuser", example = "true")
    private Boolean superuser;
}
