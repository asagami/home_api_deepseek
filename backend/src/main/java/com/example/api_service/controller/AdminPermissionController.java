package com.example.api_service.controller;

import com.example.api_service.common.PageResult;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.permission.EndpointCostUpdateRequest;
import com.example.api_service.model.dto.permission.PermissionAuditRequest;
import com.example.api_service.model.vo.permission.AdminPermissionVO;
import com.example.api_service.model.vo.permission.UserPermissionVO;
import com.example.api_service.security.RequireAdmin;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.UserApiPermissionService;
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

@Validated
@RestController
@RequestMapping("/api/console/admin/permissions")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "Console - Admin Permissions", description = "管理员权限审批与接口点数管理（仅 ADMIN 角色）")
public class AdminPermissionController {

    private final UserApiPermissionService userApiPermissionService;
    private final ApiEndpointMetaService apiEndpointMetaService;

    @GetMapping("/list")
    @Operation(summary = "分页查询权限申请工单",
            description = "返回申请人用户名、接口名称、基础点数与审批信息；支持按状态和关键字筛选")
    public Result<PageResult<AdminPermissionVO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于 0")
            @Max(value = 100, message = "每页最多 100 条") long size,
            @RequestParam(required = false)
            @Min(value = 0, message = "状态只能是 0-3")
            @Max(value = 3, message = "状态只能是 0-3") Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(userApiPermissionService.adminList(page, size, status, keyword));
    }

    @PutMapping("/{id}/audit")
    @Operation(summary = "审批权限申请",
            description = "APPROVE-通过并可设置专属折扣，REJECT-驳回（必须填写意见），REVOKE-收回已授予的权限")
    public Result<UserPermissionVO> audit(
            @PathVariable @Positive(message = "工单 ID 必须大于 0") Long id,
            @Valid @RequestBody PermissionAuditRequest request) {
        return Result.success(userApiPermissionService.audit(UserContext.getUserId(), id, request));
    }

    @PutMapping("/endpoints/{endpointId}/cost")
    @Operation(summary = "修改接口基础点数", description = "调整 api_endpoint_meta.cost_per_call")
    public Result<Void> updateEndpointCost(
            @PathVariable @Positive(message = "接口 ID 必须大于 0") Long endpointId,
            @Valid @RequestBody EndpointCostUpdateRequest request) {
        apiEndpointMetaService.updateCost(endpointId, request.costPerCall());
        return Result.success();
    }
}
