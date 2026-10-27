package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.external.DynamicDataSourceManager;
import com.example.api_service.mapper.ApiEndpointMetaMapper;
import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.model.vo.admin.codegen.DataSourceOptionVO;
import com.example.api_service.service.ApiCodeGenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 接口脚手架代码生成器业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCodeGenServiceImpl implements ApiCodeGenService {

    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final ApiEndpointMetaMapper apiEndpointMetaMapper;

    private static final String BASE_JAVA_DIR = "backend/src/main/java/com/example/api_service/engine/handlers/";
    private static final String BASE_DOCS_DIR = "docs/generated_sql/";

    @Override
    public CodeGenPreviewVO preview(ApiCodeGenSpec spec) {
        validateSpec(spec);

        String classPrefix = extractClassPrefix(spec.getEndpointPath());
        boolean isPaged = spec.getPaged() == null || spec.getPaged();

        String reqClassName = classPrefix + (isPaged ? "PageReq" : "Req");
        String voClassName = classPrefix + "OpenVO";
        String handlerClassName = classPrefix + "ApiHandler";

        String reqCode = renderReqCode(spec, reqClassName, isPaged);
        String voCode = renderVoCode(spec, voClassName);
        String handlerCode = renderHandlerCode(spec, handlerClassName, reqClassName, voClassName, isPaged);
        String metaSql = renderMetaSql(spec, reqClassName);

        return CodeGenPreviewVO.builder()
                .reqClassName(reqClassName)
                .reqCode(reqCode)
                .voClassName(voClassName)
                .voCode(voCode)
                .handlerClassName(handlerClassName)
                .handlerCode(handlerCode)
                .metaSql(metaSql)
                .build();
    }

    @Override
    public void writeToWorkspace(ApiCodeGenSpec spec) {
        CodeGenPreviewVO previewVO = preview(spec);

        Path domainDir = Paths.get(BASE_JAVA_DIR, spec.getDomain());
        Path docsDir = Paths.get(BASE_DOCS_DIR);

        try {
            Files.createDirectories(domainDir);
            Files.createDirectories(docsDir);

            // 1. 工程防护规则：严格防覆盖，已有业务代码绝不覆盖
            Path reqPath = domainDir.resolve(previewVO.getReqClassName() + ".java");
            Path voPath = domainDir.resolve(previewVO.getVoClassName() + ".java");
            Path handlerPath = domainDir.resolve(previewVO.getHandlerClassName() + ".java");

            checkNotExists(reqPath);
            checkNotExists(voPath);
            checkNotExists(handlerPath);

            Files.writeString(reqPath, previewVO.getReqCode(), StandardOpenOption.CREATE_NEW);
            Files.writeString(voPath, previewVO.getVoCode(), StandardOpenOption.CREATE_NEW);
            Files.writeString(handlerPath, previewVO.getHandlerCode(), StandardOpenOption.CREATE_NEW);

            // 2. 元数据 SQL 追加写入文档
            Path sqlPath = docsDir.resolve("init_endpoints.sql");
            Files.writeString(sqlPath, previewVO.getMetaSql() + "\n\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // 3. 自动同步登记到平台 api_endpoint_meta 表（若未登记）
            Long count = apiEndpointMetaMapper.selectCount(Wrappers.<ApiEndpointMeta>lambdaQuery()
                    .eq(ApiEndpointMeta::getPath, spec.getEndpointPath())
                    .eq(ApiEndpointMeta::getMethod, "POST"));
            if (count == 0L) {
                ApiEndpointMeta meta = new ApiEndpointMeta();
                meta.setName(spec.getApiName());
                meta.setPath(spec.getEndpointPath());
                meta.setMethod("POST");
                meta.setCostPerCall(spec.getCostPerCall());
                meta.setDescription(spec.getDescription());
                meta.setStatus(1);
                meta.setRequestSchema(buildSampleJson(spec));
                apiEndpointMetaMapper.insert(meta);
                log.info("已将生成接口自动登记至 api_endpoint_meta: path={}", spec.getEndpointPath());
            }

            log.info("代码生成完成并安全写入工作区: domain={}, endpoint={}", spec.getDomain(), spec.getEndpointPath());
        } catch (IOException e) {
            log.error("写入工作区文件失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "写入工作区代码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DataSourceOptionVO> getAvailableDataSources() {
        return dynamicDataSourceManager.listAllHolders().stream()
                .map(h -> DataSourceOptionVO.builder()
                        .key(h.getKey())
                        .name(h.getName())
                        .dbType(h.getDbType())
                        .jdbcUrl(h.getJdbcUrl())
                        .build())
                .toList();
    }

    private void validateSpec(ApiCodeGenSpec spec) {
        if (spec.getEndpointPath() == null || !spec.getEndpointPath().startsWith("/api/v1/")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "接口路径必须以 /api/v1/ 开头");
        }
        if (spec.getDomain() == null || !spec.getDomain().matches("^[a-z0-9_]+$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "业务领域包名必须为纯小写英文字母");
        }
        if ("EXTERNAL_SQL".equalsIgnoreCase(spec.getEngineType())
                && (spec.getTargetDataSourceKey() == null || spec.getTargetDataSourceKey().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "外部 SQL 引擎必须指定目标数据源 Key");
        }
    }

    private void checkNotExists(Path path) {
        if (Files.exists(path)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "目标文件已存在，为防止误覆盖已有业务逻辑，已拒绝生成: " + path.getFileName());
        }
    }

    private String extractClassPrefix(String path) {
        String clean = path.replace("/api/v1/", "").trim();
        StringBuilder sb = new StringBuilder();
        for (String segment : clean.split("[/\\-_]")) {
            if (!segment.isEmpty()) {
                sb.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
            }
        }
        return sb.toString();
    }

    private String renderReqCode(ApiCodeGenSpec spec, String className, boolean isPaged) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
            package com.example.api_service.engine.handlers.%s;

            import com.example.api_service.model.dto.BasePageRequest;
            import io.swagger.v3.oas.annotations.media.Schema;
            import jakarta.validation.constraints.*;
            import lombok.Getter;
            import lombok.Setter;

            import java.math.BigDecimal;

            /**
             * %s请求参数。
             */
            @Getter
            @Setter
            @Schema(name = "%s", description = "%s请求入参")
            public class %s %s {
            """, spec.getDomain(), spec.getApiName(), className, spec.getApiName(), className,
                isPaged ? "extends BasePageRequest" : ""));

        if (spec.getRequestFields() != null) {
            for (var f : spec.getRequestFields()) {
                sb.append("\n");
                String desc = f.getDescription() != null ? f.getDescription() : f.getName();
                String example = f.getExample() != null ? f.getExample() : "";
                sb.append(String.format("    @Schema(description = \"%s\", example = \"%s\")\n", desc, example));

                if (Boolean.TRUE.equals(f.getRequired())) {
                    sb.append(String.format("    @NotNull(message = \"%s 不能为空\")\n", f.getName()));
                }
                if (f.getMaxLength() != null && f.getMaxLength() > 0 && "String".equalsIgnoreCase(f.getType())) {
                    sb.append(String.format("    @Size(max = %d, message = \"%s 长度不能超过 %d\")\n",
                            f.getMaxLength(), f.getName(), f.getMaxLength()));
                }
                sb.append(String.format("    private %s %s;\n", normalizeType(f.getType()), f.getName()));
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String renderVoCode(ApiCodeGenSpec spec, String className) {
        StringBuilder fieldsDef = new StringBuilder();
        List<ApiCodeGenSpec.FieldSpec> fields = spec.getResponseFields() != null ? spec.getResponseFields() : Collections.emptyList();

        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            String desc = f.getDescription() != null ? f.getDescription() : f.getName();
            String example = f.getExample() != null ? f.getExample() : "";
            fieldsDef.append(String.format(
                    "        @Schema(description = \"%s\", example = \"%s\") %s %s%s\n",
                    desc, example, normalizeType(f.getType()), f.getName(),
                    i == fields.size() - 1 ? "" : ","
            ));
        }

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import io.swagger.v3.oas.annotations.media.Schema;
            import java.math.BigDecimal;

            /**
             * %s响应报文。
             */
            @Schema(name = "%s", description = "%s出参")
            public record %s(
            %s) {
            }
            """, spec.getDomain(), spec.getApiName(), className, spec.getApiName(), className, fieldsDef);
    }

    private String renderHandlerCode(ApiCodeGenSpec spec, String handlerName, String reqName, String voName, boolean isPaged) {
        String respType = isPaged ? "PageResult<" + voName + ">" : voName;
        String engineType = spec.getEngineType() != null ? spec.getEngineType().toUpperCase() : "EXTERNAL_SQL";

        String baseClass;
        String extraMethods = "";
        String executionBody;

        if ("EXTERNAL_SQL".equals(engineType)) {
            baseClass = "AbstractExternalSqlApiHandler<" + reqName + ", " + respType + ">";
            extraMethods = String.format("""

                @Override
                protected String getTargetDataSourceKey() {
                    return "%s";
                }
            """, spec.getTargetDataSourceKey() != null ? spec.getTargetDataSourceKey() : "pg-local");

            String sql = spec.getCustomSql() != null && !spec.getCustomSql().isBlank()
                    ? spec.getCustomSql().trim()
                    : "SELECT id, status, create_time FROM orders WHERE 1=1";

            executionBody = String.format("""
                    String sql = "%s";
                    Map<String, Object> paramMap = new HashMap<>();
                    // TODO: 按需根据 request 填充 paramMap 与拼接查询条件

                    return queryForPage(sql, paramMap, request, (rs, rowNum) -> new %s(
                            // TODO: 映射字段
                    ));""", sql, voName);
        } else if ("INTERNAL_SQL".equals(engineType)) {
            baseClass = "AbstractSqlApiHandler<" + reqName + ", " + respType + ">";
            executionBody = String.format("""
                    // TODO: 调用本地 MyBatis-Plus Mapper 进行查询
                    throw new UnsupportedOperationException("尚未实现本地数据查询");""");
        } else if ("ES".equals(engineType)) {
            baseClass = "AbstractEsApiHandler<" + reqName + ", Object, " + voName + ">";
            executionBody = String.format("""
                    // TODO: 调用 Elasticsearch 进行检索
                    throw new UnsupportedOperationException("尚未实现 ES 检索");""");
        } else {
            baseClass = "AbstractHttpProxyApiHandler<" + reqName + ", " + voName + ">";
            executionBody = String.format("""
                    // TODO: 调用第三方 HTTP API 并清洗出参
                    throw new UnsupportedOperationException("尚未实现 HTTP 代理调用");""");
        }

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import com.example.api_service.common.PageResult;
            import com.example.api_service.engine.*;
            import lombok.RequiredArgsConstructor;
            import org.springframework.stereotype.Component;

            import java.util.HashMap;
            import java.util.Map;

            /**
             * %s
             */
            @Component
            @RequiredArgsConstructor
            public class %s extends %s {

                @Override
                public String getEndpointPath() {
                    return "%s";
                }
            %s
                @Override
                protected %s doExecute(%s request) throws Exception {
            %s
                }
            }
            """, spec.getDomain(), spec.getApiName(), handlerName, baseClass,
                spec.getEndpointPath(), extraMethods, respType, reqName, indent(executionBody, 8));
    }

    private String renderMetaSql(ApiCodeGenSpec spec, String reqClassName) {
        String sampleJson = buildSampleJson(spec);
        return String.format("""
            -- ==========================================
            -- 接口: %s
            -- 路径: %s
            -- ==========================================
            INSERT IGNORE INTO `api_endpoint_meta`
                (`name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
            VALUES
                ('%s', '%s', 'POST', %d, '%s', '%s', 1);
            """, spec.getApiName(), spec.getEndpointPath(),
                spec.getApiName(), spec.getEndpointPath(), spec.getCostPerCall(),
                spec.getDescription() != null ? spec.getDescription() : spec.getApiName(),
                sampleJson.replace("'", "\\'"));
    }

    private String buildSampleJson(ApiCodeGenSpec spec) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (spec.getPaged() == null || spec.getPaged()) {
            map.put("pageNum", 1);
            map.put("pageSize", 20);
        }
        if (spec.getRequestFields() != null) {
            for (var f : spec.getRequestFields()) {
                map.put(f.getName(), f.getExample() != null ? f.getExample() : "demo");
            }
        }
        try {
            return new tools.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalizeType(String type) {
        if (type == null) return "String";
        return switch (type.toLowerCase()) {
            case "int", "integer" -> "Integer";
            case "long" -> "Long";
            case "boolean", "bool" -> "Boolean";
            case "decimal", "bigdecimal" -> "BigDecimal";
            case "double" -> "Double";
            default -> "String";
        };
    }

    private String indent(String code, int spaces) {
        String pad = " ".repeat(spaces);
        return pad + code.replace("\n", "\n" + pad);
    }
}
