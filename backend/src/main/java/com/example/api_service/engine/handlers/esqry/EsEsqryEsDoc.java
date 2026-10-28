package com.example.api_service.engine.handlers.esqry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * es用户分页查询 ES 检索文档模型。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EsEsqryEsDoc", description = "es用户分页查询 ES 文档")
public class EsEsqryEsDoc {

    @Schema(description = "用户ID", example = "10")
    private String userId;

    @Schema(description = "用户名", example = "postgres")
    private String username;

    @Schema(description = "是否超级用户", example = "true")
    private Boolean superuser;

    @Schema(description = "过期时间", example = "infinity")
    private String validUntil;

}
