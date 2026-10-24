package com.example.api_service.model.dto.log.admin;

import lombok.Data;

/**
 * 用户点数消耗排行聚合结果行，仅用于 Mapper → Service 内部传递。
 */
@Data
public class AdminLogUserConsumeRow {

    private Long userId;

    private String username;

    private Long costPoints;

    private Long callCount;
}
