package com.example.api_service.engine.handlers.geo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 清洗后的 IP 归属地结果。字段命名与上游解耦，避免第三方字段直接透出。
 */
@Schema(name = "IpLocateVO", description = "IP 归属地查询结果")
public record IpLocateVO(
        @Schema(description = "IP 地址", example = "8.8.8.8") String ip,
        @Schema(description = "国家/地区", example = "United States") String country,
        @Schema(description = "省/州", example = "California") String region,
        @Schema(description = "城市", example = "Mountain View") String city,
        @Schema(description = "网络服务商", example = "Google LLC") String isp,
        @Schema(description = "纬度", example = "37.4056") Double latitude,
        @Schema(description = "经度", example = "-122.0775") Double longitude,
        @Schema(description = "时区", example = "America/Los_Angeles") String timezone
) {
}
