package com.example.api_service.engine.handlers.detailqry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流水查询 ES 检索文档模型。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EsDetailqryEsDoc", description = "流水查询 ES 文档")
public class EsDetailqryEsDoc {

    @Schema(description = "对方账户", example = "10")
    private String othactno;

    @Schema(description = "交易金额", example = "postgres")
    private Integer amount;

    @Schema(description = "余额", example = "true")
    private Integer balance;
}
