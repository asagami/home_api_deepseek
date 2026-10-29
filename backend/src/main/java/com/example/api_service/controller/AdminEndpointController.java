package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.dto.permission.EndpointStatusUpdateRequest;
import com.example.api_service.model.vo.permission.AdminEndpointVO;
import com.example.api_service.security.RequireAdmin;
import com.example.api_service.service.ApiEndpointMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API 发布管理：管理员查看接口目录并变更上下线状态。
 *
 * <p>新增接口默认下线，需管理员在此上线后开放网关才会放行调用。</p>
 */
@Validated
@RestController
@RequestMapping("/api/console/admin/endpoints")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "Console - Admin API Publish", description = "API 发布管理（仅 ADMIN 角色）")
public class AdminEndpointController {

    private final ApiEndpointMetaService apiEndpointMetaService;

    @GetMapping("/list")
    @Operation(summary = "查询 API 目录", description = "返回全部接口的上下线状态，支持按状态与关键字筛选")
    public Result<List<AdminEndpointVO>> list(
            @RequestParam(required = false)
            @Min(value = 0, message = "状态只能是 0-下线 或 1-上线")
            @Max(value = 1, message = "状态只能是 0-下线 或 1-上线") Integer status,
            @RequestParam(required = false) String keyword) {
        List<AdminEndpointVO> endpoints = apiEndpointMetaService.listForAdmin(status, keyword).stream()
                .map(AdminEndpointVO::from)
                .toList();
        return Result.success(endpoints);
    }

    @PutMapping("/{endpointId}/status")
    @Operation(summary = "变更 API 上下线状态",
            description = "1-上线，0-下线；下线后开放网关统一返回「API 已下线」")
    public Result<AdminEndpointVO> updateStatus(
            @PathVariable @Positive(message = "接口 ID 必须大于 0") Long endpointId,
            @Valid @RequestBody EndpointStatusUpdateRequest request) {
        return Result.success(AdminEndpointVO.from(
                apiEndpointMetaService.updateStatus(endpointId, request.status())));
    }
}
