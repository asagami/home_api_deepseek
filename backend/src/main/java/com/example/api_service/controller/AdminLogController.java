package com.example.api_service.controller;

import com.example.api_service.common.PageResult;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.log.admin.AdminLogOverviewReq;
import com.example.api_service.model.dto.log.admin.AdminLogPageReq;
import com.example.api_service.model.vo.log.admin.AdminLogOverviewVO;
import com.example.api_service.model.vo.log.admin.AdminLogVO;
import com.example.api_service.security.RequireAdmin;
import com.example.api_service.service.AdminLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/console/admin/logs")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "Console - Admin Logs", description = "管理员全站调用日志总览（仅 ADMIN 角色）")
public class AdminLogController {

    private final AdminLogQueryService adminLogQueryService;

    @PostMapping("/overview")
    @Operation(summary = "全站调用总览",
            description = "返回汇总指标、按天趋势、接口排行与点数消耗 Top 5 用户；时间范围为空时默认最近 7 天，最大跨度 31 天")
    public Result<AdminLogOverviewVO> overview(
            @Valid @RequestBody(required = false) AdminLogOverviewReq request) {
        return Result.success(adminLogQueryService.overview(request));
    }

    @PostMapping("/page")
    @Operation(summary = "全站调用日志分页",
            description = "时间范围必填以命中 create_time 索引；关联用户名、Key 前缀与接口名称，最大跨度 31 天")
    public Result<PageResult<AdminLogVO>> page(@Valid @RequestBody AdminLogPageReq request) {
        return Result.success(adminLogQueryService.page(request));
    }
}
