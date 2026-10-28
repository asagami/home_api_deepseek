package com.example.api_service.model.dto.admin.codegen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口脚手架代码生成入参规格。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApiCodeGenSpec", description = "代码生成规格参数")
public class ApiCodeGenSpec {

    @NotBlank(message = "业务领域不能为空")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "业务领域必须为纯小写英文字母与下划线")
    @Schema(description = "所属业务领域包名 (如 order, pg, finance)", example = "order")
    private String domain;

    @NotBlank(message = "接口名称不能为空")
    @Schema(description = "接口中文名称", example = "海外订单分页查询")
    private String apiName;

    @Schema(description = "接口详细描述", example = "从外部PG库分页检索海外订单")
    private String description;

    @NotBlank(message = "接口路径不能为空")
    @Pattern(regexp = "^/api/v1/[a-zA-Z0-9_/-]+$", message = "接口路径必须以 /api/v1/ 开头，仅包含字母数字短横线下划线")
    @Schema(description = "对外接口访问路径", example = "/api/v1/pg/orders")
    private String endpointPath;

    @NotNull(message = "计费单价不能为空")
    @Schema(description = "单次调用扣减点数", example = "2")
    private Integer costPerCall;

    @NotBlank(message = "引擎类型不能为空")
    @Schema(description = "引擎类型: INTERNAL_SQL, EXTERNAL_SQL, ES, HTTP_PROXY", example = "EXTERNAL_SQL")
    private String engineType;

    @Schema(description = "外部数据源 Key (仅 EXTERNAL_SQL 时需要)", example = "pg-local")
    private String targetDataSourceKey;

    @Schema(description = "是否分页接口", example = "true")
    private Boolean paged;

    @Schema(description = "核心业务只读 SQL 模板 (针对 SQL 引擎可选)", example = "SELECT id, amount, status FROM orders WHERE 1=1")
    private String customSql;

    @Schema(description = "ES 目标索引名称 (仅 ES 检索引擎时需要)", example = "news")
    private String esIndexName;

    @Schema(description = "第三方 HTTP 基础 URL (仅 HTTP 代理引擎时需要)", example = "https://ipapi.co")
    private String upstreamBaseUrl;

    @Schema(description = "第三方 HTTP 请求方法 (GET / POST)", example = "GET")
    private String upstreamHttpMethod;

    @Schema(description = "第三方 HTTP 请求头 (JSON 格式)", example = "{\"Accept\": \"application/json\"}")
    private String upstreamHeaders;

    @Valid
    @Builder.Default
    @Schema(description = "请求入参字段列表")
    private List<FieldSpec> requestFields = new ArrayList<>();

    @Valid
    @Builder.Default
    @Schema(description = "响应出参字段列表")
    private List<FieldSpec> responseFields = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "FieldSpec", description = "字段规格定义")
    public static class FieldSpec {
        @NotBlank(message = "字段名不能为空")
        @Schema(description = "属性名 (驼峰)", example = "status")
        private String name;

        @NotBlank(message = "字段类型不能为空")
        @Schema(description = "Java 类型 (String, Integer, Long, BigDecimal, Boolean)", example = "String")
        private String type;

        @Schema(description = "字段描述", example = "订单状态")
        private String description;

        @Schema(description = "示例值", example = "PAID")
        private String example;

        @Schema(description = "是否必填 (仅入参使用)", example = "true")
        private Boolean required;

        @Schema(description = "最大字符串长度限制", example = "32")
        private Integer maxLength;
    }
}
