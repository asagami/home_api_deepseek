package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.model.vo.admin.codegen.DataSourceOptionVO;
import com.example.api_service.security.RequireAdmin;
import com.example.api_service.service.ApiCodeGenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员接口脚手架代码生成器控制器。
 */
@Validated
@RestController
@RequestMapping("/api/console/admin/codegen")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "Console - Admin CodeGen", description = "接口脚手架代码生成器（仅管理员）")
public class AdminCodeGenController {

    private final ApiCodeGenService apiCodeGenService;

    @PostMapping("/preview")
    @Operation(summary = "实时代码预览", description = "根据传入的接口规格渲染各层 Java 源码与元数据 SQL（内存中渲染，不修改磁盘）")
    public Result<CodeGenPreviewVO> preview(@Valid @RequestBody ApiCodeGenSpec spec) {
        return Result.success(apiCodeGenService.preview(spec));
    }

    @PostMapping("/write-disk")
    @Operation(summary = "写入本地项目工程", description = "将生成的类安全落盘至工作区 handlers 目录，带防覆盖保护与自动目录登记")
    public Result<Void> writeToDisk(@Valid @RequestBody ApiCodeGenSpec spec) {
        apiCodeGenService.writeToWorkspace(spec);
        return Result.success();
    }

    @GetMapping("/datasources")
    @Operation(summary = "获取可用外部数据源", description = "查询当前已在系统中注册的外部异构数据库列表")
    public Result<List<DataSourceOptionVO>> listDataSources() {
        return Result.success(apiCodeGenService.getAvailableDataSources());
    }
}
