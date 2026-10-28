package com.example.api_service.service;

import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.model.vo.admin.codegen.DataSourceOptionVO;

import java.util.List;

/**
 * 接口脚手架代码生成服务接口。
 */
public interface ApiCodeGenService {

    /**
     * 实时渲染代码预览（纯内存，不写磁盘）。
     */
    CodeGenPreviewVO preview(ApiCodeGenSpec spec);

    /**
     * 将生成的类安全写入当前本地工程目录，并输出元数据登记 SQL。
     *
     * @param spec 代码生成规格
     * @return 实际落盘的文件目录绝对路径
     */
    String writeToWorkspace(ApiCodeGenSpec spec);

    /**
     * 更新已有接口的代码 (仅允许更新 Req、VO 和 SQL)。
     * 保持已有 Handler 业务逻辑不被破坏。
     *
     * @param spec 更新规格
     * @return 实际更新的文件目录绝对路径
     */
    String updateWorkspace(ApiCodeGenSpec spec);

    /**
     * 查询当前已就绪的外部数据源列表。
     */
    List<DataSourceOptionVO> getAvailableDataSources();

    /**
     * 获取所有可编辑的开放接口列表 (供前端下拉回显选择)。
     */
    List<com.example.api_service.model.vo.admin.codegen.ApiEndpointSummaryVO> listConfiguredEndpoints();

    /**
     * 加载指定接口的历史配置规格以供前端表单回显。
     */
    ApiCodeGenSpec getEndpointSpec(String path);
}
