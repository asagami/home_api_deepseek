package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.mapper.ApiAccessLogMapper;
import com.example.api_service.model.dto.log.admin.AdminLogDailyTrendRow;
import com.example.api_service.model.dto.log.admin.AdminLogEndpointRankRow;
import com.example.api_service.model.dto.log.admin.AdminLogOverviewReq;
import com.example.api_service.model.dto.log.admin.AdminLogPageReq;
import com.example.api_service.model.dto.log.admin.AdminLogPageRow;
import com.example.api_service.model.dto.log.admin.AdminLogSummaryRow;
import com.example.api_service.model.dto.log.admin.AdminLogUserConsumeRow;
import com.example.api_service.model.vo.log.admin.AdminLogOverviewVO;
import com.example.api_service.model.vo.log.admin.AdminLogVO;
import com.example.api_service.model.vo.log.admin.DailyTrendItem;
import com.example.api_service.model.vo.log.admin.EndpointRankItem;
import com.example.api_service.model.vo.log.admin.UserConsumeRankItem;
import com.example.api_service.service.AdminLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLogQueryServiceImpl implements AdminLogQueryService {

    /** 默认总览窗口：含今天在内的 7 天。 */
    private static final int DEFAULT_WINDOW_DAYS = 7;
    /** 单次查询最大跨度，避免无界扫描整个日志表。 */
    private static final int MAX_WINDOW_DAYS = 31;
    /** 接口排行返回条数。 */
    private static final int ENDPOINT_RANK_LIMIT = 10;
    /** 点数消耗排行返回条数。 */
    private static final int USER_CONSUME_LIMIT = 5;

    private final ApiAccessLogMapper apiAccessLogMapper;

    @Override
    public AdminLogOverviewVO overview(AdminLogOverviewReq request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = request != null && request.endTime() != null
                ? request.endTime()
                : now;
        LocalDateTime startTime = request != null && request.startTime() != null
                ? request.startTime()
                : endTime.toLocalDate().minusDays(DEFAULT_WINDOW_DAYS - 1L).atStartOfDay();
        validateRange(startTime, endTime);

        AdminLogSummaryRow summary = apiAccessLogMapper.selectAdminOverviewSummary(startTime, endTime);
        List<AdminLogDailyTrendRow> trendRows =
                apiAccessLogMapper.selectAdminDailyTrend(startTime, endTime);
        List<AdminLogEndpointRankRow> rankRows =
                apiAccessLogMapper.selectAdminEndpointRank(startTime, endTime, ENDPOINT_RANK_LIMIT);
        List<AdminLogUserConsumeRow> consumeRows =
                apiAccessLogMapper.selectAdminTopConsumeUsers(startTime, endTime, USER_CONSUME_LIMIT);

        long totalCalls = summary != null ? nullToZero(summary.getTotalCalls()) : 0;
        long successCalls = summary != null ? nullToZero(summary.getSuccessCalls()) : 0;
        long totalCostPoints = summary != null ? nullToZero(summary.getTotalCostPoints()) : 0;
        long rateLimitCount = summary != null ? nullToZero(summary.getRateLimitCount()) : 0;
        long error5xxCount = summary != null ? nullToZero(summary.getError5xxCount()) : 0;
        long avgLatencyMs = summary != null ? round(summary.getAvgLatencyMs()) : 0;

        return new AdminLogOverviewVO(
                startTime,
                endTime,
                totalCalls,
                totalCostPoints,
                successRate(successCalls, totalCalls),
                rateLimitCount,
                error5xxCount,
                avgLatencyMs,
                buildDailyTrend(startTime, endTime, trendRows),
                buildEndpointRank(rankRows),
                buildUserConsumeRank(consumeRows)
        );
    }

    @Override
    public PageResult<AdminLogVO> page(AdminLogPageReq request) {
        if (request == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "分页查询必须同时指定 startTime 与 endTime");
        }
        validateRange(request.getStartTime(), request.getEndTime());
        request.setUsername(StringUtils.hasText(request.getUsername())
                ? request.getUsername().trim() : null);
        request.setEndpointPath(StringUtils.hasText(request.getEndpointPath())
                ? request.getEndpointPath().trim() : null);

        Page<AdminLogPageRow> pageRequest =
                new Page<>(request.getSafePageNum(), request.getSafePageSize());
        List<AdminLogPageRow> rows = apiAccessLogMapper.selectAdminLogPage(pageRequest, request);

        List<AdminLogVO> records = rows.stream()
                .map(AdminLogVO::from)
                .toList();
        return PageResult.of(records, pageRequest.getTotal(),
                request.getSafePageNum(), request.getSafePageSize());
    }

    private void validateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始时间不能晚于结束时间");
        }
        long spanDays = ChronoUnit.DAYS.between(startTime.toLocalDate(), endTime.toLocalDate());
        if (spanDays >= MAX_WINDOW_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "查询跨度不能超过 " + MAX_WINDOW_DAYS + " 天");
        }
    }

    private List<DailyTrendItem> buildDailyTrend(LocalDateTime startTime,
                                                 LocalDateTime endTime,
                                                 List<AdminLogDailyTrendRow> rows) {
        Map<LocalDate, AdminLogDailyTrendRow> rowByDate = new HashMap<>();
        for (AdminLogDailyTrendRow row : rows) {
            if (row.getStatDate() != null) {
                rowByDate.put(row.getStatDate(), row);
            }
        }

        List<DailyTrendItem> daily = new ArrayList<>();
        LocalDate endDate = endTime.toLocalDate();
        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endDate); date = date.plusDays(1)) {
            AdminLogDailyTrendRow row = rowByDate.get(date);
            long totalCalls = row != null ? nullToZero(row.getTotalCalls()) : 0;
            long successCalls = row != null ? nullToZero(row.getSuccessCalls()) : 0;
            long rateLimitCount = row != null ? nullToZero(row.getRateLimitCount()) : 0;
            long error5xxCount = row != null ? nullToZero(row.getError5xxCount()) : 0;
            long costPoints = row != null ? nullToZero(row.getCostPoints()) : 0;
            long avgLatencyMs = row != null ? round(row.getAvgLatencyMs()) : 0;
            daily.add(new DailyTrendItem(
                    date,
                    totalCalls,
                    successCalls,
                    rateLimitCount,
                    error5xxCount,
                    costPoints,
                    avgLatencyMs,
                    successRate(successCalls, totalCalls)
            ));
        }
        return daily;
    }

    private List<EndpointRankItem> buildEndpointRank(List<AdminLogEndpointRankRow> rows) {
        return rows.stream()
                .map(row -> {
                    long callCount = nullToZero(row.getCallCount());
                    long successCalls = nullToZero(row.getSuccessCalls());
                    return new EndpointRankItem(
                            row.getEndpointPath(),
                            StringUtils.hasText(row.getEndpointName())
                                    ? row.getEndpointName() : row.getEndpointPath(),
                            callCount,
                            successCalls,
                            nullToZero(row.getCostPoints()),
                            nullToZero(row.getRateLimitCount()),
                            nullToZero(row.getError5xxCount()),
                            round(row.getAvgLatencyMs()),
                            successRate(successCalls, callCount)
                    );
                })
                .toList();
    }

    private List<UserConsumeRankItem> buildUserConsumeRank(List<AdminLogUserConsumeRow> rows) {
        return rows.stream()
                .map(row -> new UserConsumeRankItem(
                        row.getUserId(),
                        row.getUsername(),
                        nullToZero(row.getCostPoints()),
                        nullToZero(row.getCallCount())
                ))
                .toList();
    }

    /** 成功率保留 1 位小数，分母为 0 时返回 0。 */
    private double successRate(long successCalls, long totalCalls) {
        if (totalCalls <= 0) {
            return 0;
        }
        return Math.round((double) successCalls / totalCalls * 1000) / 10.0;
    }

    private long round(Double value) {
        return value == null ? 0 : Math.round(value);
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }
}
