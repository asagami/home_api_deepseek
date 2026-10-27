package com.example.api_service.model.vo.admin.codegen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成器预览响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CodeGenPreviewVO", description = "生成代码预览内容")
public class CodeGenPreviewVO {

    @Schema(description = "请求 DTO 类名")
    private String reqClassName;

    @Schema(description = "请求 DTO 完整源码")
    private String reqCode;

    @Schema(description = "响应 VO 类名")
    private String voClassName;

    @Schema(description = "响应 VO 完整源码")
    private String voCode;

    @Schema(description = "Handler 类名")
    private String handlerClassName;

    @Schema(description = "Handler 完整源码")
    private String handlerCode;

    @Schema(description = "元数据初始化 SQL")
    private String metaSql;
}
