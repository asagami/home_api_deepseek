package com.example.api_service.model.vo.admin.codegen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口简要概要出参 (供前端编辑已有接口下拉选择)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApiEndpointSummaryVO", description = "已配置接口简要概要")
public class ApiEndpointSummaryVO {

    @Schema(description = "接口路径", example = "/api/v1/pg/sample-users")
    private String path;

    @Schema(description = "接口名称", example = "PostgreSQL用户分页查询")
    private String name;

    @Schema(description = "引擎类型", example = "EXTERNAL_SQL")
    private String engineType;

    @Schema(description = "单次调用点数", example = "1")
    private Integer costPerCall;

    @Schema(description = "接口状态：1-已上线，0-已下线（未发布）", example = "0")
    private Integer status;

    @Schema(description = "是否已保存生成元数据配置", example = "true")
    private Boolean hasConfig;
}
