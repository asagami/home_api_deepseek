package com.example.api_service.engine.handlers.detailqry;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 流水查询请求参数。
 */
@Getter
@Setter
@Schema(name = "EsDetailqryPageReq", description = "流水查询请求入参")
public class EsDetailqryPageReq extends BasePageRequest {

    @Schema(description = "账号", example = "postgres")
    private String actno;

    @Schema(description = "开始时间", example = "true")
    private String begdt;

    @Schema(description = "结束时间", example = "")
    private String enddt;
}
