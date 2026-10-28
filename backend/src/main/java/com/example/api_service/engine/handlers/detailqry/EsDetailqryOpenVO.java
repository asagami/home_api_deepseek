package com.example.api_service.engine.handlers.detailqry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 流水查询响应报文。
 */
@Schema(name = "EsDetailqryOpenVO", description = "流水查询出参")
public record EsDetailqryOpenVO(
        @Schema(description = "对方账户", example = "10") String othactno,
        @Schema(description = "交易金额", example = "postgres") Integer amount,
        @Schema(description = "余额", example = "true") Integer balance
) {
}
