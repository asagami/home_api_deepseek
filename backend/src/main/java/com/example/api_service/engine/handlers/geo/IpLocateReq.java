package com.example.api_service.engine.handlers.geo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * IP 归属地查询请求。普通 DTO，不继承 BasePageRequest，
 * 因此计费倍率固定为 1。
 */
@Schema(name = "IpLocateReq", description = "IP 归属地查询请求")
public class IpLocateReq {

    @Schema(description = "待查询的 IPv4 或 IPv6 地址", example = "8.8.8.8")
    @NotBlank(message = "ip 不能为空")
    @Size(max = 64, message = "ip 长度不能超过 64")
    private String ip;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
