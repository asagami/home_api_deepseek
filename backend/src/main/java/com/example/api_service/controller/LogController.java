package com.example.api_service.controller;

import com.example.api_service.common.PageResult;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.log.LogQueryCriteria;
import com.example.api_service.model.vo.log.ApiAccessLogVO;
import com.example.api_service.model.vo.log.LogStatsVO;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.LogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/console/logs")
@RequiredArgsConstructor
@Tag(name = "Console - Logs", description = "当前用户的 API 调用日志与统计")
public class LogController {

    private final LogQueryService logQueryService;

    @GetMapping
    @Operation(summary = "分页查询调用日志",
            description = "按 create_time 范围过滤以命中 idx_user_time 索引；默认最近 7 天")
    public Result<PageResult<ApiAccessLogVO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于 0")
            @Max(value = 100, message = "每页最多 100 条") long size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "调用时间下界（含），ISO 格式，如 2026-10-16T00:00:00") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "调用时间上界（含），ISO 格式") LocalDateTime endTime,
            @RequestParam(required = false)
            @Min(value = 100, message = "状态码最小为 100")
            @Max(value = 599, message = "状态码最大为 599")
            @Parameter(description = "精确匹配的 HTTP 状态码") Integer httpStatus) {
        LogQueryCriteria criteria = new LogQueryCriteria(page, size, startTime, endTime, httpStatus);
        return Result.success(logQueryService.list(UserContext.getUserId(), criteria));
    }

    @GetMapping("/stats")
    @Operation(summary = "近 7 天调用统计",
            description = "按天聚合调用量、成功数、4xx/5xx 错误数与平均耗时；默认含今天在内的 7 天")
    public Result<LogStatsVO> stats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "起始日期（含），默认 6 天前") LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "结束日期（含），默认今天") LocalDate endDate) {
        return Result.success(logQueryService.stats(UserContext.getUserId(), startDate, endDate));
    }
}
