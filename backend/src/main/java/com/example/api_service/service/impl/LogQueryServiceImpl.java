package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.mapper.ApiAccessLogMapper;
import com.example.api_service.model.dto.log.LogDailyStatRow;
import com.example.api_service.model.dto.log.LogQueryCriteria;
import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.vo.log.ApiAccessLogVO;
import com.example.api_service.model.vo.log.LogDailyStatVO;
import com.example.api_service.model.vo.log.LogStatsVO;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    /** 默认统计窗口：含今天在内的 7 天。 */
    private static final int DEFAULT_WINDOW_DAYS = 7;
    /** 单次聚合最大跨度，避免大范围扫描。 */
    private static final int MAX_STATS_DAYS = 31;

    private final ApiAccessLogService apiAccessLogService;
    private final ApiAccessLogMapper apiAccessLogMapper;

    @Override
    public PageResult<ApiAccessLogVO> list(Long userId, LogQueryCriteria criteria) {
        LocalDateTime startTime = criteria.startTime() != null
                ? criteria.startTime()
                : LocalDate.now().minusDays(DEFAULT_WINDOW_DAYS - 1L).atStartOfDay();
        LocalDateTime endTime = criteria.endTime() != null
                ? criteria.endTime()
                : LocalDate.now().atTime(LocalTime.MAX);
        if (startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始时间不能晚于结束时间");
        }

        Page<ApiAccessLog> pageRequest = new Page<>(criteria.page(), criteria.size());
        Page<ApiAccessLog> result = apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, userId)
                .ge(ApiAccessLog::getCreateTime, startTime)
                .le(ApiAccessLog::getCreateTime, endTime)
                .eq(criteria.httpStatus() != null, ApiAccessLog::getHttpStatus, criteria.httpStatus())
                .orderByDesc(ApiAccessLog::getCreateTime)
                .page(pageRequest);

        List<ApiAccessLogVO> records = result.getRecords().stream()
                .map(ApiAccessLogVO::from)
                .toList();
        return PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public LogStatsVO stats(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDate resolvedEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate resolvedStart = startDate != null
                ? startDate
                : resolvedEnd.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(resolvedStart, resolvedEnd) >= MAX_STATS_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "统计跨度不能超过 " + MAX_STATS_DAYS + " 天");
        }

        // 使用左闭右开区间，配合 idx_user_time 做范围扫描
        LocalDateTime rangeStart = resolvedStart.atStartOfDay();
        LocalDateTime rangeEnd = resolvedEnd.plusDays(1).atStartOfDay();
        List<LogDailyStatRow> rows = apiAccessLogMapper.selectDailyStats(userId, rangeStart, rangeEnd);

        Map<LocalDate, LogDailyStatRow> rowByDate = new HashMap<>();
        for (LogDailyStatRow row : rows) {
            rowByDate.put(row.getStatDate(), row);
        }

        List<LogDailyStatVO> daily = new ArrayList<>();
        long totalCalls = 0;
        long successCalls = 0;
        long clientErrorCalls = 0;
        long serverErrorCalls = 0;
        double weightedLatency = 0;

        for (LocalDate date = resolvedStart; !date.isAfter(resolvedEnd); date = date.plusDays(1)) {
            LogDailyStatRow row = rowByDate.get(date);
            long dayTotal = row != null && row.getTotalCalls() != null ? row.getTotalCalls() : 0;
            long daySuccess = row != null && row.getSuccessCalls() != null ? row.getSuccessCalls() : 0;
            long dayClientError = row != null && row.getClientErrorCalls() != null
                    ? row.getClientErrorCalls() : 0;
            long dayServerError = row != null && row.getServerErrorCalls() != null
                    ? row.getServerErrorCalls() : 0;
            long dayAvgLatency = row != null && row.getAvgLatencyMs() != null
                    ? Math.round(row.getAvgLatencyMs())
                    : 0;

            daily.add(new LogDailyStatVO(
                    date, dayTotal, daySuccess, dayClientError, dayServerError, dayAvgLatency));

            totalCalls += dayTotal;
            successCalls += daySuccess;
            clientErrorCalls += dayClientError;
            serverErrorCalls += dayServerError;
            weightedLatency += (double) dayAvgLatency * dayTotal;
        }

        long avgLatency = totalCalls == 0 ? 0 : Math.round(weightedLatency / totalCalls);
        double successRate = totalCalls == 0
                ? 0
                : Math.round((double) successCalls / totalCalls * 1000) / 10.0;

        return new LogStatsVO(
                resolvedStart,
                resolvedEnd,
                totalCalls,
                successCalls,
                clientErrorCalls,
                serverErrorCalls,
                avgLatency,
                successRate,
                daily
        );
    }
}
