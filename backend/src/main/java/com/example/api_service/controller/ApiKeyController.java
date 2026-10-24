package com.example.api_service.controller;

import com.example.api_service.common.PageResult;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.apikey.ApiKeyCreateRequest;
import com.example.api_service.model.dto.apikey.ApiKeyStatusRequest;
import com.example.api_service.model.vo.apikey.ApiKeyCreateVO;
import com.example.api_service.model.vo.apikey.ApiKeyVO;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.ApiKeyManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/console/apikeys")
@RequiredArgsConstructor
@Tag(name = "Console - API Key", description = "当前用户的 API Key 管理")
public class ApiKeyController {

    private final ApiKeyManageService apiKeyManageService;

    @PostMapping
    @Operation(summary = "创建 API Key", description = "明文 Key 仅在本次响应中返回一次，数据库只保存 SHA-256 与脱敏前缀")
    public Result<ApiKeyCreateVO> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        return Result.success(apiKeyManageService.create(UserContext.getUserId(), request));
    }

    @GetMapping
    @Operation(summary = "分页查询 API Key", description = "仅返回当前用户的 Key，不含明文与哈希")
    public Result<PageResult<ApiKeyVO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于 0")
            @Max(value = 100, message = "每页最多 100 条") long size) {
        return Result.success(apiKeyManageService.list(UserContext.getUserId(), page, size));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用/禁用 API Key")
    public Result<Void> updateStatus(
            @PathVariable @Positive(message = "Key ID 必须大于 0") Long id,
            @Valid @RequestBody ApiKeyStatusRequest request) {
        apiKeyManageService.updateStatus(UserContext.getUserId(), id, request.status());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 API Key", description = "逻辑删除，删除后不可再用于鉴权")
    public Result<Void> delete(@PathVariable @Positive(message = "Key ID 必须大于 0") Long id) {
        apiKeyManageService.delete(UserContext.getUserId(), id);
        return Result.success();
    }
}
