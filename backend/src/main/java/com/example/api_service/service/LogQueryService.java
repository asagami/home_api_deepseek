package com.example.api_service.service;

import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.log.LogQueryCriteria;
import com.example.api_service.model.vo.log.ApiAccessLogVO;
import com.example.api_service.model.vo.log.LogStatsVO;

import java.time.LocalDate;

public interface LogQueryService {

    /**
     * 分页查询指定用户的调用日志，时间条件走 create_time 索引。
     */
    PageResult<ApiAccessLogVO> list(Long userId, LogQueryCriteria criteria);

    /**
     * 聚合查询指定用户在 [startDate, endDate] 内的调用统计，缺失日期补 0。
     */
    LogStatsVO stats(Long userId, LocalDate startDate, LocalDate endDate);
}
