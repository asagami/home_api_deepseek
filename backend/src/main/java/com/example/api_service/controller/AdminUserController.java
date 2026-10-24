package com.example.api_service.controller;

import com.example.api_service.common.PageResult;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.admin.QuotaRechargeRequest;
import com.example.api_service.model.dto.admin.UserStatusRequest;
import com.example.api_service.model.vo.admin.AdminUserVO;
import com.example.api_service.security.RequireAdmin;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.AdminUserManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/console/admin/users")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "Console - Admin Users", description = "管理员用户管理（仅 ADMIN 角色）")
public class AdminUserController {

    private final AdminUserManageService adminUserManageService;

    @GetMapping
    @Operation(summary = "分页查询用户", description = "支持用户名/邮箱模糊搜索与状态筛选")
    public Result<PageResult<AdminUserVO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于 0")
            @Max(value = 100, message = "每页最多 100 条") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @Min(value = 0, message = "状态只能是 0 或 1")
            @Max(value = 1, message = "状态只能是 0 或 1") Integer status) {
        return Result.success(adminUserManageService.list(page, size, keyword, status));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "封禁/解封用户", description = "status=0 封禁，status=1 解封；管理员不能封禁自己")
    public Result<Void> updateStatus(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody UserStatusRequest request) {
        adminUserManageService.updateStatus(UserContext.getUserId(), id, request.status());
        return Result.success();
    }

    @PostMapping("/{id}/quota")
    @Operation(summary = "额度充值", description = "原子追加 balance_quota，返回更新后的用户信息")
    public Result<AdminUserVO> recharge(
            @PathVariable @Positive(message = "用户 ID 必须大于 0") Long id,
            @Valid @RequestBody QuotaRechargeRequest request) {
        return Result.success(adminUserManageService.recharge(id, request.amount()));
    }
}
