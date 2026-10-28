package com.example.api_service.engine.handlers.weatherqry;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 天气查询请求参数。
 */
@Getter
@Setter
@Schema(name = "PgWeatherqryPageReq", description = "天气查询请求入参")
public class PgWeatherqryPageReq extends BasePageRequest {

    @Schema(description = "地区", example = "postgres")
    private String local;

    @Schema(description = "时间", example = "true")
    private String time;
}
