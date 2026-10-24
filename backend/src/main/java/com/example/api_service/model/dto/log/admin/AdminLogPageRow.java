package com.example.api_service.model.dto.log.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全站日志分页查询结果行，关联用户名、Key 前缀与接口名称。
 */
@Data
public class AdminLogPageRow {

    private Long id;

    private Long userId;

    private String username;

    private Long apiKeyId;

    private String keyPrefix;

    private String endpointPath;

    private String endpointName;

    private Integer httpStatus;

    private Integer costPoints;

    private Integer latencyMs;

    private String clientIp;

    private LocalDateTime createTime;
}
