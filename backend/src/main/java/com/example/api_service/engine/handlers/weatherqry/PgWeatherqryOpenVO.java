package com.example.api_service.engine.handlers.weatherqry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 天气查询响应报文。
 */
@Schema(name = "PgWeatherqryOpenVO", description = "天气查询出参")
public record PgWeatherqryOpenVO(
        @Schema(description = "天气", example = "10") String weather,
        @Schema(description = "时间", example = "postgres") String time,
        @Schema(description = "是否下雨", example = "true") Boolean isxiayu
) {
}
