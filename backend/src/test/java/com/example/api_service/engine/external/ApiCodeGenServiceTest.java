package com.example.api_service.engine.external;

import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.service.impl.ApiCodeGenServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiCodeGenService 单元测试")
class ApiCodeGenServiceTest {

    private final ApiCodeGenServiceImpl codeGenService = new ApiCodeGenServiceImpl(null, null, null);

    @Test
    @DisplayName("preview 应该能正确生成 External SQL 的 DTO/VO/Handler 与 SQL (带 SQL 锚点)")
    void shouldRenderExternalSqlCorrectly() {
        ApiCodeGenSpec spec = ApiCodeGenSpec.builder()
                .domain("order")
                .apiName("海外订单分页查询")
                .description("按状态分页检索海外PG订单")
                .endpointPath("/api/v1/pg/orders")
                .costPerCall(2)
                .engineType("EXTERNAL_SQL")
                .targetDataSourceKey("pg-local")
                .paged(true)
                .customSql("SELECT id, order_id, amount FROM orders WHERE status = :status")
                .requestFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("status").type("String").description("状态").build()
                ))
                .responseFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("orderId").type("String").description("订单ID").build(),
                        ApiCodeGenSpec.FieldSpec.builder().name("amount").type("BigDecimal").description("金额").build()
                ))
                .build();

        CodeGenPreviewVO previewVO = codeGenService.preview(spec);

        assertNotNull(previewVO);
        assertEquals("PgOrdersPageReq", previewVO.getReqClassName());
        assertEquals("PgOrdersOpenVO", previewVO.getVoClassName());
        assertEquals("PgOrdersApiHandler", previewVO.getHandlerClassName());
        assertNull(previewVO.getDocClassName());

        assertTrue(previewVO.getReqCode().contains("public class PgOrdersPageReq extends BasePageRequest"));
        assertTrue(previewVO.getReqCode().contains("private String status;"));

        assertTrue(previewVO.getVoCode().contains("public record PgOrdersOpenVO("));
        assertTrue(previewVO.getVoCode().contains("String orderId"));
        assertTrue(previewVO.getVoCode().contains("BigDecimal amount"));

        assertTrue(previewVO.getHandlerCode().contains("extends AbstractExternalSqlApiHandler<PgOrdersPageReq, PageResult<PgOrdersOpenVO>>"));
        assertTrue(previewVO.getHandlerCode().contains("return \"/api/v1/pg/orders\";"));
        assertTrue(previewVO.getHandlerCode().contains("return \"pg-local\";"));
        assertTrue(previewVO.getHandlerCode().contains(ApiCodeGenServiceImpl.SQL_ANCHOR_START));
        assertTrue(previewVO.getHandlerCode().contains(ApiCodeGenServiceImpl.SQL_ANCHOR_END));
        assertTrue(previewVO.getHandlerCode().contains("SELECT id, order_id, amount FROM orders WHERE status = :status"));

        assertTrue(previewVO.getMetaSql().contains("INSERT IGNORE INTO `api_endpoint_meta`"));
        assertTrue(previewVO.getMetaSql().contains("'/api/v1/pg/orders'"));
        // 新增接口默认初始化 status = 0（未发布），需管理员在 API 发布管理中上线
        assertTrue(previewVO.getMetaSql().contains(", 0);"));
        assertTrue(previewVO.getMetaSql().contains("status = 0"));
    }

    @Test
    @DisplayName("preview 应该能正确生成 ES 检索 Handler 与 EsDoc，且包含所有 protected 方法而不包含 doExecute")
    void shouldRenderEsHandlerAndDocCorrectly() {
        ApiCodeGenSpec spec = ApiCodeGenSpec.builder()
                .domain("news")
                .apiName("全球新闻检索")
                .description("从 ES 检索新闻标题与内容")
                .endpointPath("/api/v1/news/search")
                .costPerCall(3)
                .engineType("ES")
                .esIndexName("global_news")
                .paged(true)
                .requestFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("keyword").type("String").description("搜索词").build()
                ))
                .responseFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("title").type("String").description("标题").build(),
                        ApiCodeGenSpec.FieldSpec.builder().name("content").type("String").description("内容").build()
                ))
                .build();

        CodeGenPreviewVO previewVO = codeGenService.preview(spec);

        assertNotNull(previewVO);
        assertEquals("NewsSearchPageReq", previewVO.getReqClassName());
        assertEquals("NewsSearchOpenVO", previewVO.getVoClassName());
        assertEquals("NewsSearchApiHandler", previewVO.getHandlerClassName());
        assertEquals("NewsSearchEsDoc", previewVO.getDocClassName());

        // EsDoc 验证
        assertNotNull(previewVO.getDocCode());
        assertTrue(previewVO.getDocCode().contains("public class NewsSearchEsDoc"));
        assertTrue(previewVO.getDocCode().contains("private String title;"));
        assertTrue(previewVO.getDocCode().contains("private String content;"));

        // Handler 验证：必须继承 AbstractEsApiHandler，必须调用 super(elasticsearchClient)，必须实现四个 protected 方法，严禁覆盖 doExecute
        String handlerCode = previewVO.getHandlerCode();
        assertTrue(handlerCode.contains("extends AbstractEsApiHandler<NewsSearchPageReq, NewsSearchEsDoc, NewsSearchOpenVO>"));
        assertTrue(handlerCode.contains("super(elasticsearchClient);"));
        assertTrue(handlerCode.contains("protected String getIndexName()"));
        assertTrue(handlerCode.contains("protected Class<NewsSearchEsDoc> getDocumentClass()"));
        assertTrue(handlerCode.contains("protected Query buildQuery(NewsSearchPageReq request)"));
        assertTrue(handlerCode.contains("protected NewsSearchOpenVO convertDocToResp(NewsSearchEsDoc doc)"));
        assertFalse(handlerCode.contains("doExecute"), "ES Handler 的 doExecute 为 final，子类绝不能包含 doExecute");
    }

    @Test
    @DisplayName("preview 应该能正确生成 HTTP_PROXY Handler，且包含所有 protected 方法而不包含 doExecute")
    void shouldRenderHttpProxyHandlerCorrectly() {
        ApiCodeGenSpec spec = ApiCodeGenSpec.builder()
                .domain("weather")
                .apiName("天气查询代理")
                .description("转发第三方天气 API")
                .endpointPath("/api/v1/weather/realtime")
                .costPerCall(1)
                .engineType("HTTP_PROXY")
                .upstreamBaseUrl("https://api.weather.com/v1")
                .upstreamHttpMethod("GET")
                .paged(false)
                .requestFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("city").type("String").description("城市").build()
                ))
                .responseFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("temp").type("String").description("温度").build(),
                        ApiCodeGenSpec.FieldSpec.builder().name("humidity").type("Integer").description("湿度").build()
                ))
                .build();

        CodeGenPreviewVO previewVO = codeGenService.preview(spec);

        assertNotNull(previewVO);
        assertEquals("WeatherRealtimeReq", previewVO.getReqClassName());
        assertEquals("WeatherRealtimeOpenVO", previewVO.getVoClassName());
        assertEquals("WeatherRealtimeApiHandler", previewVO.getHandlerClassName());

        String handlerCode = previewVO.getHandlerCode();
        assertTrue(handlerCode.contains("extends AbstractHttpProxyApiHandler<WeatherRealtimeReq, WeatherRealtimeOpenVO>"));
        assertTrue(handlerCode.contains("protected String buildTargetUrl(WeatherRealtimeReq request)"));
        assertTrue(handlerCode.contains("protected Map<String, String> buildHeaders()"));
        assertTrue(handlerCode.contains("protected HttpMethod resolveHttpMethod(WeatherRealtimeReq request)"));
        assertTrue(handlerCode.contains("protected WeatherRealtimeOpenVO transformResponse(String rawJson)"));
        assertFalse(handlerCode.contains("doExecute"), "HTTP Proxy Handler 的 doExecute 为 final，子类绝不能包含 doExecute");
    }

    @Test
    @DisplayName("reverseEngineerFromWorkspace 应该能从已有源码中精准逆向出老接口的 SQL、入参和出参")
    void shouldReverseEngineerOldEndpointCorrectly() {
        com.example.api_service.model.entity.ApiEndpointMeta meta = new com.example.api_service.model.entity.ApiEndpointMeta();
        meta.setName("PostgreSQL系统用户查询");
        meta.setPath("/api/v1/pg/sample-users");
        meta.setCostPerCall(1);
        meta.setDescription("外部 PostgreSQL 数据库分页查询示例接口");
        meta.setEngineType("INTERNAL_SQL"); // 模拟老库历史默认值

        ApiCodeGenSpec spec = codeGenService.reverseEngineerFromWorkspace("/api/v1/pg/sample-users", meta);

        assertNotNull(spec);
        assertEquals("pg", spec.getDomain());
        assertEquals("EXTERNAL_SQL", spec.getEngineType());
        assertEquals("pg-local", spec.getTargetDataSourceKey());
        assertTrue(spec.getPaged());
        assertNotNull(spec.getCustomSql());
        assertTrue(spec.getCustomSql().contains("SELECT") && spec.getCustomSql().contains("pg_catalog.pg_user"));

        // 验证入参逆向解析结果
        assertNotNull(spec.getRequestFields());
        assertFalse(spec.getRequestFields().isEmpty());
        assertTrue(spec.getRequestFields().stream().anyMatch(f -> "username".equals(f.getName())));
        assertTrue(spec.getRequestFields().stream().anyMatch(f -> "superuser".equals(f.getName())));

        // 验证出参逆向解析结果
        assertNotNull(spec.getResponseFields());
        assertFalse(spec.getResponseFields().isEmpty());
        assertTrue(spec.getResponseFields().stream().anyMatch(f -> "userId".equals(f.getName())));
        assertTrue(spec.getResponseFields().stream().anyMatch(f -> "username".equals(f.getName())));
        assertTrue(spec.getResponseFields().stream().anyMatch(f -> "validUntil".equals(f.getName())));
    }

    @Test
    @DisplayName("路径解析器应精准定位到 backend 源码目录与 docs 目录")
    void shouldResolveCorrectPaths() {
        java.nio.file.Path handlersDir = codeGenService.resolveHandlersBaseDir();
        assertNotNull(handlersDir);
        assertTrue(handlersDir.endsWith("backend/src/main/java/com/example/api_service/engine/handlers")
                || handlersDir.endsWith("src/main/java/com/example/api_service/engine/handlers"));
        assertTrue(java.nio.file.Files.exists(handlersDir));

        java.nio.file.Path docsDir = codeGenService.resolveDocsBaseDir();
        assertNotNull(docsDir);
        assertTrue(docsDir.endsWith("docs/generated_sql"));
    }
}
