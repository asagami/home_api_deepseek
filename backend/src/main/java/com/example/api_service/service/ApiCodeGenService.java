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
     */
    void writeToWorkspace(ApiCodeGenSpec spec);

    /**
     * 查询当前已就绪的外部数据源列表。
     */
    List<DataSourceOptionVO> getAvailableDataSources();
}
