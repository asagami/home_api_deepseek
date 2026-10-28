package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.vo.admin.codegen.ApiEndpointSummaryVO;
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
    public Result<String> writeToDisk(@Valid @RequestBody ApiCodeGenSpec spec) {
        String targetDir = apiCodeGenService.writeToWorkspace(spec);
        return Result.success(targetDir);
    }

    @PostMapping("/update")
    @Operation(summary = "原地更新已有接口代码", description = "原地更新指定接口的 Req、VO 与 SQL，完整保留已有 Handler 自定义业务代码")
    public Result<String> updateToDisk(@Valid @RequestBody ApiCodeGenSpec spec) {
        String targetDir = apiCodeGenService.updateWorkspace(spec);
        return Result.success(targetDir);
    }

    @GetMapping("/endpoints")
    @Operation(summary = "获取已配置接口列表", description = "供接口生成器下拉选择已有接口以加载配置或更新代码")
    public Result<List<ApiEndpointSummaryVO>> listEndpoints() {
        return Result.success(apiCodeGenService.listConfiguredEndpoints());
    }

    @GetMapping("/endpoint-detail")
    @Operation(summary = "加载接口生成规格详情", description = "按 path 获取接口的历史代码生成规格供前端表单回显与编辑")
    public Result<ApiCodeGenSpec> getEndpointDetail(@RequestParam String path) {
        return Result.success(apiCodeGenService.getEndpointSpec(path));
    }

    @GetMapping("/datasources")
    @Operation(summary = "获取可用外部数据源", description = "查询当前已在系统中注册的外部异构数据库列表")
    public Result<List<DataSourceOptionVO>> listDataSources() {
        return Result.success(apiCodeGenService.getAvailableDataSources());
    }
}
