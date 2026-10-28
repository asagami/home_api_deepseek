package com.example.api_service.engine.handlers.apiqry;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * api用户分页查询请求参数。
 */
@Getter
@Setter
@Schema(name = "EsApiqryPageReq", description = "api用户分页查询请求入参")
public class EsApiqryPageReq extends BasePageRequest {

    @Schema(description = "用户名关键字", example = "postgres")
    private String username;

    @Schema(description = "是否超级管理员", example = "true")
    private Boolean superuser;
}
