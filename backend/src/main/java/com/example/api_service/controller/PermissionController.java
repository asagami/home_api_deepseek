package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.dto.permission.PermissionApplyRequest;
import com.example.api_service.model.vo.permission.EndpointMarketVO;
import com.example.api_service.model.vo.permission.UserPermissionVO;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.UserApiPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/console/permissions")
@RequiredArgsConstructor
@Tag(name = "Console - Permissions", description = "开发者接口市场与权限申请")
public class PermissionController {

    private final UserApiPermissionService userApiPermissionService;

    @GetMapping("/market")
    @Operation(summary = "接口市场",
            description = "返回全部接口目录，并左连接当前用户的审批状态、专属折扣与驳回原因")
    public Result<List<EndpointMarketVO>> market() {
        return Result.success(userApiPermissionService.market(UserContext.getUserId()));
    }

    @PostMapping("/apply")
    @Operation(summary = "申请接口权限",
            description = "待审批或已通过的接口不能重复申请；已驳回/已收回的申请会复用原记录重新提交")
    public Result<UserPermissionVO> apply(@Valid @RequestBody PermissionApplyRequest request) {
        return Result.success(userApiPermissionService.apply(UserContext.getUserId(), request));
    }
}
