package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.external.DynamicDataSourceManager;
import com.example.api_service.mapper.ApiEndpointMetaMapper;
import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.vo.admin.codegen.ApiEndpointSummaryVO;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.model.vo.admin.codegen.DataSourceOptionVO;
import com.example.api_service.service.ApiCodeGenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口脚手架代码生成器业务实现。
 * 遵循严格的工程安全防护规则：
 * 1. 严格防覆盖：已有业务代码绝不覆盖
 * 2. 局部安全修改：修改已有接口仅允许变更 VO、Req 与 SQL 锚点区，完整保留开发者自定义业务逻辑
 * 3. 严格类型约束：ES 与 HTTP Proxy 生成专属规范代码及所需 protected 模板方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCodeGenServiceImpl implements ApiCodeGenService {

    public static final String SQL_ANCHOR_START = "// >>>>> AUTO_SQL_TEMPLATE_START >>>>>";
    public static final String SQL_ANCHOR_END = "// <<<<< AUTO_SQL_TEMPLATE_END <<<<<";

    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final ApiEndpointMetaMapper apiEndpointMetaMapper;
    private final DataSource dataSource;

    @Override
    public CodeGenPreviewVO preview(ApiCodeGenSpec spec) {
        validateSpec(spec);

        String classPrefix = extractClassPrefix(spec.getEndpointPath());
        String engineType = spec.getEngineType() != null ? spec.getEngineType().toUpperCase() : "EXTERNAL_SQL";

        // ES 检索基类强制要求 Req 继承 BasePageRequest
        boolean isPaged = !"ES".equals(engineType) && (spec.getPaged() == null || spec.getPaged()) || "ES".equals(engineType);

        String reqClassName = classPrefix + (isPaged ? "PageReq" : "Req");
        String voClassName = classPrefix + "OpenVO";
        String handlerClassName = classPrefix + "ApiHandler";
        String docClassName = classPrefix + "EsDoc";

        String reqCode = renderReqCode(spec, reqClassName, isPaged);
        String voCode = renderVoCode(spec, voClassName);
        String docCode = "ES".equals(engineType) ? renderDocCode(spec, docClassName) : null;
        String handlerCode = renderHandlerCode(spec, handlerClassName, reqClassName, voClassName, docClassName, isPaged);
        String metaSql = renderMetaSql(spec, reqClassName);

        return CodeGenPreviewVO.builder()
                .reqClassName(reqClassName)
                .reqCode(reqCode)
                .voClassName(voClassName)
                .voCode(voCode)
                .docClassName("ES".equals(engineType) ? docClassName : null)
                .docCode(docCode)
                .handlerClassName(handlerClassName)
                .handlerCode(handlerCode)
                .metaSql(metaSql)
                .build();
    }

    @Override
    public String writeToWorkspace(ApiCodeGenSpec spec) {
        CodeGenPreviewVO previewVO = preview(spec);

        Path handlersBaseDir = resolveHandlersBaseDir();
        Path domainDir = handlersBaseDir.resolve(spec.getDomain());
        Path docsDir = resolveDocsBaseDir();

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

            // 如果是 ES 引擎，额外写出 ES 文档类
            if ("ES".equalsIgnoreCase(spec.getEngineType()) && previewVO.getDocClassName() != null) {
                Path docPath = domainDir.resolve(previewVO.getDocClassName() + ".java");
                checkNotExists(docPath);
                Files.writeString(docPath, previewVO.getDocCode(), StandardOpenOption.CREATE_NEW);
            }

            // 2. 元数据 SQL 追加写入文档
            Path sqlPath = docsDir.resolve("init_endpoints.sql");
            Files.writeString(sqlPath, previewVO.getMetaSql() + "\n\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // 3. 自动同步登记到平台 api_endpoint_meta 表
            syncMetadataToDb(spec, previewVO.getReqClassName(), false);

            String absolutePath = domainDir.toAbsolutePath().normalize().toString();
            log.info("代码生成完成并安全写入工作区: domainDir={}, endpoint={}", absolutePath, spec.getEndpointPath());
            return absolutePath;
        } catch (IOException e) {
            log.error("写入工作区文件失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "写入工作区代码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String updateWorkspace(ApiCodeGenSpec spec) {
        validateSpec(spec);

        String classPrefix = extractClassPrefix(spec.getEndpointPath());
        String engineType = spec.getEngineType() != null ? spec.getEngineType().toUpperCase() : "EXTERNAL_SQL";
        boolean isPaged = !"ES".equals(engineType) && (spec.getPaged() == null || spec.getPaged()) || "ES".equals(engineType);

        String reqClassName = classPrefix + (isPaged ? "PageReq" : "Req");
        String voClassName = classPrefix + "OpenVO";
        String handlerClassName = classPrefix + "ApiHandler";
        String docClassName = classPrefix + "EsDoc";

        Path handlersBaseDir = resolveHandlersBaseDir();
        Path domainDir = handlersBaseDir.resolve(spec.getDomain());

        Path reqPath = domainDir.resolve(reqClassName + ".java");
        Path voPath = domainDir.resolve(voClassName + ".java");
        Path handlerPath = domainDir.resolve(handlerClassName + ".java");
        Path docPath = domainDir.resolve(docClassName + ".java");

        if (!Files.exists(handlerPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "目标 Handler 文件不存在，无法原地修改: " + handlerPath.getFileName());
        }

        try {
            // 1. 覆写更新 Req (纯数据契约)
            String reqCode = renderReqCode(spec, reqClassName, isPaged);
            Files.writeString(reqPath, reqCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 2. 覆写更新 VO (纯出参契约)
            String voCode = renderVoCode(spec, voClassName);
            Files.writeString(voPath, voCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 3. 如果是 ES 引擎，覆写更新 Doc
            if ("ES".equalsIgnoreCase(engineType)) {
                String docCode = renderDocCode(spec, docClassName);
                Files.writeString(docPath, docCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            // 4. 更新 Handler：严格仅替换 SQL 锚点区域（其他开发者逻辑与自定义代码 100% 保留）
            if (spec.getCustomSql() != null && !spec.getCustomSql().isBlank()) {
                String existingHandlerCode = Files.readString(handlerPath);
                if (existingHandlerCode.contains(SQL_ANCHOR_START) && existingHandlerCode.contains(SQL_ANCHOR_END)) {
                    String cleanSql = spec.getCustomSql().trim();
                    String newSqlBlock = SQL_ANCHOR_START + "\n" +
                            "        String sql = \"\"\"\n" +
                            indent(cleanSql, 16) + "\n" +
                            "                \"\"\";\n" +
                            "        " + SQL_ANCHOR_END;

                    Pattern pattern = Pattern.compile(
                            Pattern.quote(SQL_ANCHOR_START) + ".*?" + Pattern.quote(SQL_ANCHOR_END),
                            Pattern.DOTALL
                    );
                    String updatedHandlerCode = pattern.matcher(existingHandlerCode)
                            .replaceAll(Matcher.quoteReplacement(newSqlBlock));

                    Files.writeString(handlerPath, updatedHandlerCode, StandardOpenOption.TRUNCATE_EXISTING);
                    log.info("已精准更新 Handler 中的 SQL 锚点: handlerPath={}", handlerPath);
                } else {
                    log.warn("Handler 中未找到 SQL 锚点，跳过 SQL 文本更新以保护原代码: {}", handlerPath);
                }
            }

            // 5. 同步更新数据库 api_endpoint_meta
            syncMetadataToDb(spec, reqClassName, true);

            String absolutePath = domainDir.toAbsolutePath().normalize().toString();
            log.info("接口代码更新完成: domainDir={}, endpoint={}", absolutePath, spec.getEndpointPath());
            return absolutePath;
        } catch (IOException e) {
            log.error("更新工作区代码失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "更新工作区代码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DataSourceOptionVO> getAvailableDataSources() {
        if (dynamicDataSourceManager == null) {
            return Collections.emptyList();
        }
        return dynamicDataSourceManager.listAllHolders().stream()
                .map(h -> DataSourceOptionVO.builder()
                        .key(h.getKey())
                        .name(h.getName())
                        .dbType(h.getDbType())
                        .jdbcUrl(h.getJdbcUrl())
                        .build())
                .toList();
    }

    @Override
    public List<ApiEndpointSummaryVO> listConfiguredEndpoints() {
        if (apiEndpointMetaMapper == null) {
            return Collections.emptyList();
        }
        try {
            return fetchEndpoints();
        } catch (BadSqlGrammarException e) {
            log.warn("检测到 api_endpoint_meta 表结构缺少新字段，触发自愈修复: {}", e.getMessage());
            trySelfHealSchema();
            try {
                return fetchEndpoints();
            } catch (Exception ex) {
                log.warn("表结构自愈后查询仍报错，回退至基础字段兼容查询: {}", ex.getMessage());
                return queryLegacyEndpoints();
            }
        }
    }

    private List<ApiEndpointSummaryVO> fetchEndpoints() {
        List<ApiEndpointMeta> list = apiEndpointMetaMapper.selectList(Wrappers.<ApiEndpointMeta>lambdaQuery()
                .eq(ApiEndpointMeta::getStatus, 1)
                .orderByDesc(ApiEndpointMeta::getId));

        return list.stream().map(m -> {
            String engineType = m.getEngineType();
            if (engineType == null || "INTERNAL_SQL".equals(engineType)) {
                // 如果数据库中还是默认的 INTERNAL_SQL 或 null，尝试从 Handler 源码快速嗅探实际类型
                Path h = findHandlerFileByPath(m.getPath());
                if (h != null) {
                    try {
                        String code = Files.readString(h);
                        if (code.contains("AbstractExternalSqlApiHandler")) engineType = "EXTERNAL_SQL";
                        else if (code.contains("AbstractEsApiHandler")) engineType = "ES";
                        else if (code.contains("AbstractHttpProxyApiHandler")) engineType = "HTTP_PROXY";
                    } catch (Exception ignored) {}
                }
            }
            return ApiEndpointSummaryVO.builder()
                    .path(m.getPath())
                    .name(m.getName())
                    .engineType(engineType != null ? engineType : "EXTERNAL_SQL")
                    .costPerCall(m.getCostPerCall())
                    .hasConfig(m.getEngineConfig() != null && !m.getEngineConfig().isBlank())
                    .build();
        }).toList();
    }

    private List<ApiEndpointSummaryVO> queryLegacyEndpoints() {
        if (dataSource == null) {
            return Collections.emptyList();
        }
        List<ApiEndpointSummaryVO> result = new ArrayList<>();
        String sql = "SELECT path, name, cost_per_call FROM api_endpoint_meta WHERE is_deleted=0 AND status=1 ORDER BY id DESC";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(ApiEndpointSummaryVO.builder()
                        .path(rs.getString("path"))
                        .name(rs.getString("name"))
                        .engineType("EXTERNAL_SQL")
                        .costPerCall(rs.getInt("cost_per_call"))
                        .hasConfig(false)
                        .build());
            }
        } catch (Exception e) {
            log.error("降级兼容查询 api_endpoint_meta 失败", e);
        }
        return result;
    }

    @Override
    public ApiCodeGenSpec getEndpointSpec(String path) {
        if (apiEndpointMetaMapper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未初始化数据访问层");
        }
        ApiEndpointMeta meta = null;
        try {
            meta = apiEndpointMetaMapper.selectOne(Wrappers.<ApiEndpointMeta>lambdaQuery()
                    .eq(ApiEndpointMeta::getPath, path));
        } catch (BadSqlGrammarException e) {
            log.warn("检测到 api_endpoint_meta 表结构缺少新字段，触发自愈修复: {}", e.getMessage());
            trySelfHealSchema();
            try {
                meta = apiEndpointMetaMapper.selectOne(Wrappers.<ApiEndpointMeta>lambdaQuery()
                        .eq(ApiEndpointMeta::getPath, path));
            } catch (Exception ex) {
                log.warn("表结构自愈后查询仍报错，回退至基础字段兼容查询: {}", ex.getMessage());
                meta = queryLegacyMeta(path);
            }
        }
        if (meta == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该接口元数据: " + path);
        }

        // 1. 如果已有完整的 engine_config JSON，直接反序列化
        if (meta.getEngineConfig() != null && !meta.getEngineConfig().isBlank()) {
            try {
                ApiCodeGenSpec spec = new ObjectMapper().readValue(meta.getEngineConfig(), ApiCodeGenSpec.class);
                if (spec.getRequestFields() != null && !spec.getRequestFields().isEmpty()
                        && spec.getResponseFields() != null && !spec.getResponseFields().isEmpty()) {
                    spec.setApiName(meta.getName());
                    spec.setEndpointPath(meta.getPath());
                    spec.setCostPerCall(meta.getCostPerCall());
                    spec.setDescription(meta.getDescription());
                    if (meta.getEngineType() != null) {
                        spec.setEngineType(meta.getEngineType());
                    }
                    return spec;
                }
            } catch (Exception e) {
                log.warn("反序列化 engineConfig 失败，回退至源码逆向推导: path={}", path, e);
            }
        }

        // 2. 源码智能逆向推导：解决历史接口无 engine_config 或字段未持久化的问题
        ApiCodeGenSpec reverseSpec = reverseEngineerFromWorkspace(path, meta);
        try {
            meta.setEngineType(reverseSpec.getEngineType());
            meta.setEngineConfig(new ObjectMapper().writeValueAsString(reverseSpec));
            apiEndpointMetaMapper.updateById(meta);
            log.info("已将老接口推导出的规格回填至 api_endpoint_meta: path={}", path);
        } catch (Exception e) {
            log.warn("自动回填 engineConfig 失败: {}", e.getMessage());
        }
        return reverseSpec;
    }

    private Path findHandlerFileByPath(String endpointPath) {
        Path handlersBaseDir = resolveHandlersBaseDir();
        if (!Files.exists(handlersBaseDir)) {
            return null;
        }
        try (var stream = Files.walk(handlersBaseDir, 4)) {
            List<Path> candidates = stream
                    .filter(p -> p.toString().endsWith("ApiHandler.java"))
                    .toList();
            for (Path p : candidates) {
                try {
                    String content = Files.readString(p);
                    if (content.contains("\"" + endpointPath + "\"")) {
                        return p;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("扫描 handlers 目录寻找 {} 失败: {}", endpointPath, e.getMessage());
        }
        return null;
    }

    public ApiCodeGenSpec reverseEngineerFromWorkspace(String endpointPath, ApiEndpointMeta meta) {
        Path handlerFile = findHandlerFileByPath(endpointPath);
        String domain = extractDomainFromPath(endpointPath);
        if (handlerFile != null && handlerFile.getParent() != null) {
            domain = handlerFile.getParent().getFileName().toString();
        }

        String engineType = meta != null && meta.getEngineType() != null ? meta.getEngineType() : "EXTERNAL_SQL";
        String customSql = null;
        String targetDsKey = "pg-local";
        String esIndexName = domain;
        String upstreamBaseUrl = "https://api.example.com";
        String upstreamMethod = "GET";
        boolean isPaged = true;

        List<ApiCodeGenSpec.FieldSpec> reqFields = new ArrayList<>();
        List<ApiCodeGenSpec.FieldSpec> voFields = new ArrayList<>();

        if (handlerFile != null) {
            try {
                String handlerCode = Files.readString(handlerFile);

                // 1. 推断引擎类型
                if (handlerCode.contains("AbstractExternalSqlApiHandler")) {
                    engineType = "EXTERNAL_SQL";
                } else if (handlerCode.contains("AbstractEsApiHandler")) {
                    engineType = "ES";
                } else if (handlerCode.contains("AbstractHttpProxyApiHandler")) {
                    engineType = "HTTP_PROXY";
                } else if (handlerCode.contains("AbstractSqlApiHandler")) {
                    engineType = "INTERNAL_SQL";
                }

                // 2. 提取 SQL
                customSql = extractSqlFromHandler(handlerCode);

                // 3. 提取 targetDataSourceKey
                Matcher dsMatcher = Pattern.compile("getTargetDataSourceKey\\(\\)[^}]*?return\\s+\"([^\"]+)\";", Pattern.DOTALL).matcher(handlerCode);
                if (dsMatcher.find()) {
                    targetDsKey = dsMatcher.group(1);
                }

                // 4. 提取 esIndexName
                Matcher esMatcher = Pattern.compile("getIndexName\\(\\)[^}]*?return\\s+\"([^\"]+)\";", Pattern.DOTALL).matcher(handlerCode);
                if (esMatcher.find()) {
                    esIndexName = esMatcher.group(1);
                } else {
                    Matcher esValMatcher = Pattern.compile("app\\.search\\.[^:]*:([^\"}]+)", Pattern.DOTALL).matcher(handlerCode);
                    if (esValMatcher.find()) {
                        esIndexName = esValMatcher.group(1);
                    }
                }

                // 5. 提取 upstreamBaseUrl & method
                Matcher httpValMatcher = Pattern.compile("app\\.proxy\\.[^:]*:([^\"}]+)", Pattern.DOTALL).matcher(handlerCode);
                if (httpValMatcher.find()) {
                    upstreamBaseUrl = httpValMatcher.group(1);
                }
                Matcher methodMatcher = Pattern.compile("HttpMethod\\.([A-Z]+)").matcher(handlerCode);
                if (methodMatcher.find()) {
                    upstreamMethod = methodMatcher.group(1);
                }

                // 6. 查找同目录下的 Req / PageReq 和 VO 文件
                Path dir = handlerFile.getParent();
                try (var stream = Files.list(dir)) {
                    List<Path> dirFiles = stream.toList();
                    for (Path f : dirFiles) {
                        String fname = f.getFileName().toString();
                        if (fname.endsWith("Req.java") || fname.endsWith("PageReq.java")) {
                            String reqCode = Files.readString(f);
                            isPaged = reqCode.contains("BasePageRequest");
                            List<ApiCodeGenSpec.FieldSpec> parsedReq = parseReqFieldsFromJava(reqCode);
                            if (!parsedReq.isEmpty()) {
                                reqFields = parsedReq;
                            }
                        } else if (fname.endsWith("VO.java")) {
                            String voCode = Files.readString(f);
                            List<ApiCodeGenSpec.FieldSpec> parsedVo = parseVoFieldsFromJava(voCode);
                            if (!parsedVo.isEmpty()) {
                                voFields = parsedVo;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("从源码逆向推导接口规格失败: handlerFile={}", handlerFile, e);
            }
        }

        // 如果源码没解析到 Req 字段，尝试从 request_schema (JSON) 兜底提取
        if (reqFields.isEmpty() && meta != null && meta.getRequestSchema() != null && !meta.getRequestSchema().isBlank()) {
            reqFields = parseReqFieldsFromJson(meta.getRequestSchema());
        }

        return ApiCodeGenSpec.builder()
                .domain(domain)
                .apiName(meta != null && meta.getName() != null ? meta.getName() : domain)
                .description(meta != null ? meta.getDescription() : "")
                .endpointPath(endpointPath)
                .costPerCall(meta != null && meta.getCostPerCall() != null ? meta.getCostPerCall() : 1)
                .engineType(engineType)
                .targetDataSourceKey(targetDsKey)
                .paged(isPaged)
                .customSql(customSql)
                .esIndexName(esIndexName)
                .upstreamBaseUrl(upstreamBaseUrl)
                .upstreamHttpMethod(upstreamMethod)
                .upstreamHeaders("{\n  \"Accept\": \"application/json\"\n}")
                .requestFields(reqFields)
                .responseFields(voFields)
                .build();
    }

    private String extractSqlFromHandler(String handlerCode) {
        if (handlerCode.contains(SQL_ANCHOR_START) && handlerCode.contains(SQL_ANCHOR_END)) {
            Pattern anchorPattern = Pattern.compile(
                    Pattern.quote(SQL_ANCHOR_START) + "([\\s\\S]*?)" + Pattern.quote(SQL_ANCHOR_END)
            );
            Matcher m = anchorPattern.matcher(handlerCode);
            if (m.find()) {
                String block = m.group(1);
                Matcher sqlTextMatcher = Pattern.compile("String\\s+sql\\s*=\\s*\"\"\"([\\s\\S]*?)\"\"\";").matcher(block);
                if (sqlTextMatcher.find()) {
                    return sqlTextMatcher.group(1).trim();
                }
                Matcher sqlStrMatcher = Pattern.compile("String\\s+sql\\s*=\\s*\"([^\"]+)\";").matcher(block);
                if (sqlStrMatcher.find()) {
                    return sqlStrMatcher.group(1).trim();
                }
            }
        }

        Matcher tbMatcher = Pattern.compile("(?:String|StringBuilder)\\s+sql\\s*(?:=|\\+=|\\-=)\\s*(?:new\\s+StringBuilder\\()?\\s*\"\"\"([\\s\\S]*?)\"\"\"").matcher(handlerCode);
        if (tbMatcher.find()) {
            return tbMatcher.group(1).trim();
        }

        Matcher strMatcher = Pattern.compile("String\\s+sql\\s*=\\s*\"([^\"]+)\";").matcher(handlerCode);
        if (strMatcher.find()) {
            return strMatcher.group(1).trim();
        }

        return null;
    }

    private List<ApiCodeGenSpec.FieldSpec> parseReqFieldsFromJava(String reqCode) {
        List<ApiCodeGenSpec.FieldSpec> fields = new ArrayList<>();
        Pattern fieldPattern = Pattern.compile("private\\s+([A-Za-z0-9_<>]+)\\s+([a-zA-Z0-9_]+);");
        Matcher matcher = fieldPattern.matcher(reqCode);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            if ("pageNum".equals(name) || "pageSize".equals(name) || "serialVersionUID".equals(name)) {
                continue;
            }
            int fieldStart = matcher.start();
            int snippetStart = Math.max(0, fieldStart - 200);
            String snippet = reqCode.substring(snippetStart, fieldStart);

            String desc = name;
            Matcher descMatcher = Pattern.compile("description\\s*=\\s*\"([^\"]*)\"").matcher(snippet);
            if (descMatcher.find()) {
                desc = descMatcher.group(1);
            }

            String example = "";
            Matcher exampleMatcher = Pattern.compile("example\\s*=\\s*\"([^\"]*)\"").matcher(snippet);
            if (exampleMatcher.find()) {
                example = exampleMatcher.group(1);
            }

            boolean required = snippet.contains("@NotNull");

            fields.add(ApiCodeGenSpec.FieldSpec.builder()
                    .name(name)
                    .type(normalizeType(type))
                    .description(desc)
                    .example(example)
                    .required(required)
                    .build());
        }
        return fields;
    }

    private List<ApiCodeGenSpec.FieldSpec> parseVoFieldsFromJava(String voCode) {
        List<ApiCodeGenSpec.FieldSpec> fields = new ArrayList<>();
        if (voCode.contains("record ")) {
            Matcher recordMatcher = Pattern.compile("record\\s+[A-Za-z0-9_]+\\s*\\(([\\s\\S]*?)\\)\\s*\\{").matcher(voCode);
            if (recordMatcher.find()) {
                String paramsBlock = recordMatcher.group(1);
                String[] parts = paramsBlock.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (String part : parts) {
                    part = part.trim();
                    if (part.isEmpty()) continue;
                    String desc = "";
                    String example = "";
                    Matcher descM = Pattern.compile("description\\s*=\\s*\"([^\"]*)\"").matcher(part);
                    if (descM.find()) desc = descM.group(1);
                    Matcher egM = Pattern.compile("example\\s*=\\s*\"([^\"]*)\"").matcher(part);
                    if (egM.find()) example = egM.group(1);

                    String clean = part.replaceAll("@Schema\\([^)]*\\)", "").trim();
                    String[] tokens = clean.split("\\s+");
                    if (tokens.length >= 2) {
                        String type = tokens[tokens.length - 2];
                        String name = tokens[tokens.length - 1];
                        if (name.equalsIgnoreCase("entity") || name.equalsIgnoreCase("document")) {
                            continue;
                        }
                        fields.add(ApiCodeGenSpec.FieldSpec.builder()
                                .name(name)
                                .type(normalizeType(type))
                                .description(!desc.isEmpty() ? desc : name)
                                .example(example)
                                .build());
                    }
                }
            }
        } else {
            Pattern fieldPattern = Pattern.compile("private\\s+([A-Za-z0-9_<>]+)\\s+([a-zA-Z0-9_]+);");
            Matcher matcher = fieldPattern.matcher(voCode);
            while (matcher.find()) {
                String type = matcher.group(1);
                String name = matcher.group(2);
                if ("serialVersionUID".equals(name)) continue;
                int fieldStart = matcher.start();
                int snippetStart = Math.max(0, fieldStart - 200);
                String snippet = voCode.substring(snippetStart, fieldStart);

                String desc = name;
                Matcher descMatcher = Pattern.compile("description\\s*=\\s*\"([^\"]*)\"").matcher(snippet);
                if (descMatcher.find()) desc = descMatcher.group(1);

                String example = "";
                Matcher exampleMatcher = Pattern.compile("example\\s*=\\s*\"([^\"]*)\"").matcher(snippet);
                if (exampleMatcher.find()) example = exampleMatcher.group(1);

                fields.add(ApiCodeGenSpec.FieldSpec.builder()
                        .name(name)
                        .type(normalizeType(type))
                        .description(desc)
                        .example(example)
                        .build());
            }
        }
        return fields;
    }

    private List<ApiCodeGenSpec.FieldSpec> parseReqFieldsFromJson(String json) {
        List<ApiCodeGenSpec.FieldSpec> fields = new ArrayList<>();
        try {
            tools.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
            if (node.isObject()) {
                Iterator<Map.Entry<String, tools.jackson.databind.JsonNode>> it = node.properties().iterator();
                while (it.hasNext()) {
                    var entry = it.next();
                    String key = entry.getKey();
                    if ("pageNum".equals(key) || "pageSize".equals(key)) {
                        continue;
                    }
                    var val = entry.getValue();
                    String type = "String";
                    if (val.isInt()) type = "Integer";
                    else if (val.isLong()) type = "Long";
                    else if (val.isBoolean()) type = "Boolean";
                    else if (val.isDouble() || val.isBigDecimal()) type = "BigDecimal";

                    fields.add(ApiCodeGenSpec.FieldSpec.builder()
                            .name(key)
                            .type(type)
                            .description(key)
                            .example(val.asText())
                            .required(false)
                            .build());
                }
            }
        } catch (Exception ignored) {
        }
        return fields;
    }

    private ApiEndpointMeta queryLegacyMeta(String path) {
        if (dataSource == null) {
            return null;
        }
        String sql = "SELECT id, name, path, cost_per_call, description FROM api_endpoint_meta WHERE path = ? AND is_deleted = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ApiEndpointMeta meta = new ApiEndpointMeta();
                    meta.setId(rs.getLong("id"));
                    meta.setName(rs.getString("name"));
                    meta.setPath(rs.getString("path"));
                    meta.setCostPerCall(rs.getInt("cost_per_call"));
                    meta.setDescription(rs.getString("description"));
                    meta.setEngineType("EXTERNAL_SQL");
                    return meta;
                }
            }
        } catch (Exception e) {
            log.error("降级兼容查询单条接口元数据失败: path={}", path, e);
        }
        return null;
    }

    private void syncMetadataToDb(ApiCodeGenSpec spec, String reqClassName, boolean isUpdate) {
        if (apiEndpointMetaMapper == null) {
            return;
        }
        try {
            saveOrUpdateMeta(spec, isUpdate);
        } catch (BadSqlGrammarException e) {
            log.warn("保存元数据时检测到缺少列，尝试自愈表结构: {}", e.getMessage());
            trySelfHealSchema();
            try {
                saveOrUpdateMeta(spec, isUpdate);
            } catch (Exception ex) {
                log.error("自愈后仍无法保存元数据", ex);
            }
        }
    }

    private void saveOrUpdateMeta(ApiCodeGenSpec spec, boolean isUpdate) {
        try {
            ApiEndpointMeta meta = apiEndpointMetaMapper.selectOne(Wrappers.<ApiEndpointMeta>lambdaQuery()
                    .eq(ApiEndpointMeta::getPath, spec.getEndpointPath())
                    .eq(ApiEndpointMeta::getMethod, "POST"));

            String engineConfigJson = new ObjectMapper().writeValueAsString(spec);

            if (meta == null) {
                meta = new ApiEndpointMeta();
                meta.setName(spec.getApiName());
                meta.setPath(spec.getEndpointPath());
                meta.setMethod("POST");
                meta.setEngineType(spec.getEngineType());
                meta.setCostPerCall(spec.getCostPerCall());
                meta.setDescription(spec.getDescription());
                meta.setStatus(1);
                meta.setRequestSchema(buildSampleJson(spec));
                meta.setEngineConfig(engineConfigJson);
                apiEndpointMetaMapper.insert(meta);
                log.info("已将生成接口自动登记至 api_endpoint_meta: path={}", spec.getEndpointPath());
            } else if (isUpdate) {
                meta.setName(spec.getApiName());
                meta.setEngineType(spec.getEngineType());
                meta.setCostPerCall(spec.getCostPerCall());
                meta.setDescription(spec.getDescription());
                meta.setRequestSchema(buildSampleJson(spec));
                meta.setEngineConfig(engineConfigJson);
                apiEndpointMetaMapper.updateById(meta);
                log.info("已更新 api_endpoint_meta 元数据: path={}", spec.getEndpointPath());
            }
        } catch (Exception e) {
            if (e instanceof BadSqlGrammarException) {
                throw (BadSqlGrammarException) e;
            }
            log.error("同步 api_endpoint_meta 失败", e);
        }
    }

    private void trySelfHealSchema() {
        if (dataSource == null) {
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("ALTER TABLE `api_endpoint_meta` ADD COLUMN `engine_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_SQL' AFTER `method`");
                log.info("自愈成功：已为 api_endpoint_meta 添加 engine_type 列");
            } catch (Exception ignored) {
            }
            try {
                stmt.execute("ALTER TABLE `api_endpoint_meta` ADD COLUMN `engine_config` TEXT NULL AFTER `response_schema`");
                log.info("自愈成功：已为 api_endpoint_meta 添加 engine_config 列");
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.warn("自愈修复表结构异常: {}", e.getMessage());
        }
    }

    public Path resolveHandlersBaseDir() {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();

        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString())) {
            Path directSrc = userDir.resolve("src/main/java/com/example/api_service/engine/handlers");
            if (Files.exists(directSrc.getParent())) {
                return directSrc;
            }
        }

        Path nestedBackend = userDir.resolve("backend/src/main/java/com/example/api_service/engine/handlers");
        if (Files.exists(nestedBackend.getParent())) {
            return nestedBackend;
        }

        Path cursor = userDir;
        while (cursor != null) {
            Path candidateNested = cursor.resolve("backend/src/main/java/com/example/api_service/engine/handlers");
            if (Files.exists(candidateNested.getParent())) {
                return candidateNested;
            }
            Path candidateDirect = cursor.resolve("src/main/java/com/example/api_service/engine/handlers");
            if (Files.exists(candidateDirect.getParent())) {
                return candidateDirect;
            }
            cursor = cursor.getParent();
        }

        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString())) {
            return userDir.resolve("src/main/java/com/example/api_service/engine/handlers");
        }
        return userDir.resolve("backend/src/main/java/com/example/api_service/engine/handlers");
    }

    public Path resolveDocsBaseDir() {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();

        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString()) && userDir.getParent() != null) {
            return userDir.getParent().resolve("docs/generated_sql");
        }

        if (Files.exists(userDir.resolve("docs"))) {
            return userDir.resolve("docs/generated_sql");
        }

        Path cursor = userDir;
        while (cursor != null) {
            Path candidate = cursor.resolve("docs");
            if (Files.exists(candidate)) {
                return candidate.resolve("generated_sql");
            }
            cursor = cursor.getParent();
        }

        return userDir.resolve("docs/generated_sql");
    }

    private void validateSpec(ApiCodeGenSpec spec) {
        if (spec.getEndpointPath() == null || !spec.getEndpointPath().startsWith("/api/v1/")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "接口路径必须以 /api/v1/ 开头");
        }
        if (spec.getDomain() == null || !spec.getDomain().matches("^[a-z0-9_]+$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "业务领域包名必须为纯小写英文字母与下划线");
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

    private String extractDomainFromPath(String path) {
        String clean = path.replace("/api/v1/", "").trim();
        String[] parts = clean.split("/");
        if (parts.length > 0 && !parts[0].isBlank()) {
            return parts[0].toLowerCase().replaceAll("[^a-z0-9_]", "");
        }
        return "data";
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

    private String renderDocCode(ApiCodeGenSpec spec, String className) {
        StringBuilder fieldsDef = new StringBuilder();
        List<ApiCodeGenSpec.FieldSpec> fields = spec.getResponseFields() != null ? spec.getResponseFields() : Collections.emptyList();
        for (var f : fields) {
            String desc = f.getDescription() != null ? f.getDescription() : f.getName();
            String example = f.getExample() != null ? f.getExample() : "";
            fieldsDef.append(String.format("""
                    @Schema(description = "%s", example = "%s")
                    private %s %s;

                """, desc, example, normalizeType(f.getType()), f.getName()));
        }

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
            import io.swagger.v3.oas.annotations.media.Schema;
            import lombok.Data;

            import java.math.BigDecimal;

            /**
             * %s ES 检索文档模型。
             */
            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            @Schema(name = "%s", description = "%s ES 文档")
            public class %s {

            %s}
            """,
                spec.getDomain(),
                spec.getApiName(),
                className,
                spec.getApiName(),
                className,
                fieldsDef.toString()
        );
    }

    private String renderHandlerCode(ApiCodeGenSpec spec, String handlerName, String reqName, String voName, String docName, boolean isPaged) {
        String engineType = spec.getEngineType() != null ? spec.getEngineType().toUpperCase() : "EXTERNAL_SQL";

        return switch (engineType) {
            case "EXTERNAL_SQL" -> renderExternalSqlHandler(spec, handlerName, reqName, voName, isPaged);
            case "INTERNAL_SQL" -> renderInternalSqlHandler(spec, handlerName, reqName, voName, isPaged);
            case "ES" -> renderEsHandler(spec, handlerName, reqName, docName, voName);
            case "HTTP_PROXY" -> renderHttpProxyHandler(spec, handlerName, reqName, voName);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的引擎类型: " + engineType);
        };
    }

    private String renderExternalSqlHandler(ApiCodeGenSpec spec, String handlerName, String reqName, String voName, boolean isPaged) {
        String respType = isPaged ? "PageResult<" + voName + ">" : voName;
        String dsKey = spec.getTargetDataSourceKey() != null && !spec.getTargetDataSourceKey().isBlank()
                ? spec.getTargetDataSourceKey().trim()
                : "pg-local";
        String cleanSql = spec.getCustomSql() != null && !spec.getCustomSql().isBlank()
                ? spec.getCustomSql().trim()
                : "SELECT id, status, create_time FROM orders WHERE 1=1";

        StringBuilder rowMapping = new StringBuilder();
        List<ApiCodeGenSpec.FieldSpec> fields = spec.getResponseFields() != null ? spec.getResponseFields() : Collections.emptyList();
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            rowMapping.append(String.format("                    %s%s\n",
                    renderRowMapperExtractor(f.getName(), f.getType()),
                    i == fields.size() - 1 ? "" : ","));
        }

        String queryExecution;
        if (isPaged) {
            queryExecution = String.format("""
                    return queryForPage(sql, paramMap, request, (rs, rowNum) -> new %s(
            %s        ));""", voName, rowMapping.toString());
        } else {
            queryExecution = String.format("""
                    java.util.List<%s> list = queryForList(sql, paramMap, (rs, rowNum) -> new %s(
            %s        ));
                    return list.isEmpty() ? null : list.get(0);""", voName, voName, rowMapping.toString());
        }

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import com.example.api_service.common.PageResult;
            import com.example.api_service.engine.AbstractExternalSqlApiHandler;
            import org.springframework.stereotype.Component;

            import java.math.BigDecimal;
            import java.util.HashMap;
            import java.util.Map;

            /**
             * %s
             */
            @Component
            public class %s extends AbstractExternalSqlApiHandler<%s, %s> {

                @Override
                public String getEndpointPath() {
                    return "%s";
                }

                @Override
                protected String getTargetDataSourceKey() {
                    return "%s";
                }

                @Override
                protected %s doExecute(%s request) {
                    // >>>>> AUTO_SQL_TEMPLATE_START >>>>>
                    String sql = \"\"\"
            %s
                            \"\"\";
                    // <<<<< AUTO_SQL_TEMPLATE_END <<<<<

                    Map<String, Object> paramMap = new HashMap<>();
                    // TODO: 按需根据 request 填充 paramMap 与查询条件

            %s
                }
            }
            """,
                spec.getDomain(),
                spec.getApiName(),
                handlerName,
                reqName, respType,
                spec.getEndpointPath(),
                dsKey,
                respType, reqName,
                indent(cleanSql, 12),
                indent(queryExecution, 8)
        );
    }

    private String renderInternalSqlHandler(ApiCodeGenSpec spec, String handlerName, String reqName, String voName, boolean isPaged) {
        String respType = isPaged ? "PageResult<" + voName + ">" : voName;
        String cleanSql = spec.getCustomSql() != null && !spec.getCustomSql().isBlank()
                ? spec.getCustomSql().trim()
                : "SELECT id, status, create_time FROM orders WHERE 1=1";

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import com.example.api_service.common.PageResult;
            import com.example.api_service.engine.AbstractSqlApiHandler;
            import lombok.RequiredArgsConstructor;
            import org.springframework.stereotype.Component;

            /**
             * %s
             */
            @Component
            @RequiredArgsConstructor
            public class %s extends AbstractSqlApiHandler<%s, %s> {

                @Override
                public String getEndpointPath() {
                    return "%s";
                }

                @Override
                protected %s doExecute(%s request) {
                    // >>>>> AUTO_SQL_TEMPLATE_START >>>>>
                    String sql = \"\"\"
            %s
                            \"\"\";
                    // <<<<< AUTO_SQL_TEMPLATE_END <<<<<

                    // TODO: 注入并调用本地 MyBatis-Plus Mapper 进行业务查询
                    throw new UnsupportedOperationException("请注入对应本地 Mapper 完成查询");
                }
            }
            """,
                spec.getDomain(),
                spec.getApiName(),
                handlerName,
                reqName, respType,
                spec.getEndpointPath(),
                respType, reqName,
                indent(cleanSql, 12)
        );
    }

    private String renderEsHandler(ApiCodeGenSpec spec, String handlerName, String reqName, String docName, String voName) {
        String indexName = spec.getEsIndexName() != null && !spec.getEsIndexName().isBlank()
                ? spec.getEsIndexName().trim()
                : spec.getDomain();

        StringBuilder voMapping = new StringBuilder();
        List<ApiCodeGenSpec.FieldSpec> fields = spec.getResponseFields() != null ? spec.getResponseFields() : Collections.emptyList();
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            String getter = getterName(f.getName(), f.getType());
            voMapping.append(String.format("                doc.%s()%s\n", getter, i == fields.size() - 1 ? "" : ","));
        }

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import co.elastic.clients.elasticsearch.ElasticsearchClient;
            import co.elastic.clients.elasticsearch._types.query_dsl.Query;
            import com.example.api_service.engine.AbstractEsApiHandler;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Component;

            /**
             * %s
             */
            @Component
            public class %s extends AbstractEsApiHandler<%s, %s, %s> {

                private final String indexName;

                public %s(
                        ElasticsearchClient elasticsearchClient,
                        @Value("${app.search.%s-index:%s}") String indexName) {
                    super(elasticsearchClient);
                    this.indexName = indexName;
                }

                @Override
                public String getEndpointPath() {
                    return "%s";
                }

                @Override
                protected String getIndexName() {
                    return indexName;
                }

                @Override
                protected Class<%s> getDocumentClass() {
                    return %s.class;
                }

                @Override
                protected Query buildQuery(%s request) {
                    // TODO: 按需构造 ES Query 条件；返回 null 时默认 match_all
                    return null;
                }

                @Override
                protected %s convertDocToResp(%s doc) {
                    return new %s(
            %s        );
                }
            }
            """,
                spec.getDomain(),
                spec.getApiName(),
                handlerName,
                reqName, docName, voName,
                handlerName,
                spec.getDomain(), indexName,
                spec.getEndpointPath(),
                docName, docName,
                reqName,
                voName, docName, voName,
                voMapping.toString()
        );
    }

    private String renderHttpProxyHandler(ApiCodeGenSpec spec, String handlerName, String reqName, String voName) {
        String baseUrl = spec.getUpstreamBaseUrl() != null && !spec.getUpstreamBaseUrl().isBlank()
                ? spec.getUpstreamBaseUrl().trim()
                : "https://api.example.com";
        String httpMethod = spec.getUpstreamHttpMethod() != null && !spec.getUpstreamHttpMethod().isBlank()
                ? spec.getUpstreamHttpMethod().toUpperCase()
                : "GET";

        StringBuilder voMapping = new StringBuilder();
        List<ApiCodeGenSpec.FieldSpec> fields = spec.getResponseFields() != null ? spec.getResponseFields() : Collections.emptyList();
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            String extractor = renderJsonExtractor(f.getName(), f.getType());
            voMapping.append(String.format("                        %s%s\n", extractor, i == fields.size() - 1 ? "" : ","));
        }

        return String.format("""
            package com.example.api_service.engine.handlers.%s;

            import com.example.api_service.common.BusinessException;
            import com.example.api_service.common.ErrorCode;
            import com.example.api_service.engine.AbstractHttpProxyApiHandler;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.http.HttpMethod;
            import org.springframework.stereotype.Component;
            import tools.jackson.databind.JsonNode;
            import tools.jackson.databind.ObjectMapper;

            import java.math.BigDecimal;
            import java.util.Map;

            /**
             * %s
             */
            @Component
            public class %s extends AbstractHttpProxyApiHandler<%s, %s> {

                private final ObjectMapper objectMapper;
                private final String baseUrl;

                public %s(
                        ObjectMapper objectMapper,
                        @Value("${app.proxy.%s.base-url:%s}") String baseUrl) {
                    this.objectMapper = objectMapper;
                    this.baseUrl = baseUrl;
                }

                @Override
                public String getEndpointPath() {
                    return "%s";
                }

                @Override
                protected String buildTargetUrl(%s request) {
                    // TODO: 按需根据 request 拼装下游 API 目标路径与 Query 参数
                    return baseUrl;
                }

                @Override
                protected Map<String, String> buildHeaders() {
                    return Map.of(
                            "Accept", "application/json"
                    );
                }

                @Override
                protected HttpMethod resolveHttpMethod(%s request) {
                    return HttpMethod.%s;
                }

                @Override
                protected %s transformResponse(String rawJson) {
                    try {
                        JsonNode root = objectMapper.readTree(rawJson);
                        if (root == null) {
                            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回空响应");
                        }
                        return new %s(
            %s            );
                    } catch (BusinessException be) {
                        throw be;
                    } catch (Exception e) {
                        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "解析上游响应失败: " + e.getMessage(), e);
                    }
                }
            }
            """,
                spec.getDomain(),
                spec.getApiName(),
                handlerName,
                reqName, voName,
                handlerName,
                spec.getDomain(), baseUrl,
                spec.getEndpointPath(),
                reqName,
                reqName,
                httpMethod,
                voName,
                voName,
                voMapping.toString()
        );
    }

    private String renderJsonExtractor(String name, String type) {
        return switch (type.toLowerCase()) {
            case "string" -> String.format("root.hasNonNull(\"%s\") ? root.get(\"%s\").asText() : null", name, name);
            case "int", "integer" -> String.format("root.hasNonNull(\"%s\") ? root.get(\"%s\").asInt() : null", name, name);
            case "long" -> String.format("root.hasNonNull(\"%s\") ? root.get(\"%s\").asLong() : null", name, name);
            case "boolean", "bool" -> String.format("root.hasNonNull(\"%s\") && root.get(\"%s\").asBoolean()", name, name);
            case "decimal", "bigdecimal" -> String.format("root.hasNonNull(\"%s\") ? new BigDecimal(root.get(\"%s\").asText()) : null", name, name);
            case "double" -> String.format("root.hasNonNull(\"%s\") ? root.get(\"%s\").asDouble() : null", name, name);
            default -> String.format("root.hasNonNull(\"%s\") ? root.get(\"%s\").asText() : null", name, name);
        };
    }

    private String renderRowMapperExtractor(String name, String type) {
        return switch (type.toLowerCase()) {
            case "string" -> String.format("rs.getString(\"%s\")", name);
            case "int", "integer" -> String.format("rs.getObject(\"%s\") != null ? rs.getInt(\"%s\") : null", name, name);
            case "long" -> String.format("rs.getObject(\"%s\") != null ? rs.getLong(\"%s\") : null", name, name);
            case "boolean", "bool" -> String.format("rs.getObject(\"%s\") != null && rs.getBoolean(\"%s\")", name, name);
            case "decimal", "bigdecimal" -> String.format("rs.getBigDecimal(\"%s\")", name);
            case "double" -> String.format("rs.getObject(\"%s\") != null ? rs.getDouble(\"%s\") : null", name, name);
            default -> String.format("(%s) rs.getObject(\"%s\")", normalizeType(type), name);
        };
    }

    private String getterName(String fieldName, String type) {
        if (fieldName == null || fieldName.isEmpty()) return "get";
        String cap = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return "get" + cap;
    }

    private String renderMetaSql(ApiCodeGenSpec spec, String reqClassName) {
        String sampleJson = buildSampleJson(spec);
        String engineConfigJson = "{}";
        try {
            engineConfigJson = new ObjectMapper().writeValueAsString(spec);
        } catch (Exception ignored) {
        }

        return String.format("""
            -- ==========================================
            -- 接口: %s
            -- 路径: %s
            -- 引擎: %s
            -- ==========================================
            INSERT IGNORE INTO `api_endpoint_meta`
                (`name`, `path`, `method`, `engine_type`, `cost_per_call`, `description`, `request_schema`, `engine_config`, `status`)
            VALUES
                ('%s', '%s', 'POST', '%s', %d, '%s', '%s', '%s', 1);
            """, spec.getApiName(), spec.getEndpointPath(), spec.getEngineType(),
                spec.getApiName(), spec.getEndpointPath(), spec.getEngineType(), spec.getCostPerCall(),
                spec.getDescription() != null ? spec.getDescription() : spec.getApiName(),
                sampleJson.replace("'", "\\'"),
                engineConfigJson.replace("'", "\\'"));
    }

    private String buildSampleJson(ApiCodeGenSpec spec) {
        Map<String, Object> map = new LinkedHashMap<>();
        String engineType = spec.getEngineType() != null ? spec.getEngineType().toUpperCase() : "EXTERNAL_SQL";
        boolean isPaged = !"ES".equals(engineType) && (spec.getPaged() == null || spec.getPaged()) || "ES".equals(engineType);

        if (isPaged) {
            map.put("pageNum", 1);
            map.put("pageSize", 20);
        }
        if (spec.getRequestFields() != null) {
            for (var f : spec.getRequestFields()) {
                map.put(f.getName(), f.getExample() != null ? f.getExample() : "demo");
            }
        }
        try {
            return new ObjectMapper().writeValueAsString(map);
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
