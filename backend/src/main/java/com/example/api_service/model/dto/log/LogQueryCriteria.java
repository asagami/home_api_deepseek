package com.example.api_service.model.dto.log;

import java.time.LocalDateTime;

/**
 * 调用日志分页查询条件。
 *
 * @param startTime  调用时间下界（含），null 表示最近 7 天
 * @param endTime    调用时间上界（含），null 表示今天结束
 * @param httpStatus 精确匹配的 HTTP 状态码，null 表示不筛选
 */
public record LogQueryCriteria(
        long page,
        long size,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer httpStatus
) {
}
