# Handler 单元测试标准

> 适用范围：`backend/src/main/java/com/example/api_service/engine/handlers/**` 下所有
> `AbstractApiHandler` 子类。新增 Handler 必须同时提交对应测试类，否则视为未完成。

## 1. 目的

统一 Handler 层测试的**职责边界、必测场景、代码结构与 Mock 方式**，确保：

- 每个 Handler 的业务接缝（URL/Query/SQL/映射/参数校验）都有字段级断言；
- 不重复测试框架模板已覆盖的计费、退款、鉴权、限流逻辑；
- 测试无网络、无真实数据库、无真实 ES/Redis，单类运行时间在毫秒级；
- 新增 Handler 的 PR 可被同一套清单审查。

## 2. 核心原则

### 2.1 责任边界：模板测一次，子类测接缝

| 职责 | 归属 | 测试位置 |
|---|---|---|
| 计费倍率识别、预扣费、失败退款、`billable` 标记、耗时回写 | `AbstractApiHandler.execute()`（final） | `AbstractApiHandlerTest`（模板契约测试，一次性） |
| HTTP 转发、超时、上游 4xx/5xx、空响应、请求头注入 | `AbstractHttpProxyApiHandler.doExecute()`（final） | `AbstractHttpProxyApiHandlerTest`（一次性） |
| ES 深分页窗口校验、`trackTotalHits`、`PageResult` 组装、ES 异常归一 | `AbstractEsApiHandler.doExecute()`（final） | `AbstractEsApiHandlerTest`（一次性） |
| 外部库分页执行、方言包装、只读 SQL 审计、连接异常归一 | `ExternalSqlQueryExecutor` | `ExternalSqlQueryExecutorTest`（独立单元测试） |
| **URL 拼装、请求头、HTTP 方法、上游 JSON → VO 映射** | HTTP 代理 Handler 子类 | `<Handler>Test`（每个 Handler 必测） |
| **索引名、查询条件构造、ES 文档 → VO 映射** | ES Handler 子类 | `<Handler>Test`（每个 Handler 必测） |
| **SQL 拼装、命名参数、`RowMapper` 映射** | 外部 SQL Handler 子类 | `<Handler>Test`（每个 Handler 必测） |
| **用户隔离、查询调用、实体 → VO 映射** | 本地 SQL Handler 子类 | `<Handler>Test`（每个 Handler 必测） |
| 路由、鉴权、限流、计费链路 | 网关 + 拦截器 | `OpenApiGatewayControllerTest` / `*IntegrationTest` |

**结论：Handler 单测只测“这个 Handler 自己的代码”，不重复测模板和网关。**

### 2.2 不测什么

- 不测 `AbstractApiHandler.execute()` 的扣费/退款顺序（模板测试覆盖）；
- 不测 API Key 鉴权、QPS、权限审批（拦截器测试覆盖）；
- 不测 `PageResult`、`BasePageRequest` 的构造与裁剪（各自有独立测试）；
- 不对第三方服务做真实网络调用，不用 `Thread.sleep` 等待。

## 3. 测试分层

| 层级 | 名称 | 是否每个 Handler 需要 | 说明 |
|---|---|---|---|
| L1 | Handler 单元测试 | **必须** | 无 Spring 上下文，直接 `new` Handler + Mockito；测纯接缝 |
| L2 | 模板契约测试 | 一次性（每个抽象基类一份） | 覆盖计费、退款、异常包装、深分页等公共逻辑 |
| L3 | 网关/集成测试 | 已有链路，按需 | `@SpringBootTest` + MockMvc/H2；不放在 Handler 测试类中 |

Handler 测试类中**禁止**出现 `@SpringBootTest`、`@AutoConfigureMockMvc`、真实 `DataSource`/`ElasticsearchClient` 连接。

## 4. 必测用例矩阵

每个 Handler 至少覆盖下表中对应引擎类型的全部 ✅ 项：

| 场景 | HTTP_PROXY | ES | EXTERNAL_SQL | INTERNAL_SQL |
|---|:---:|:---:|:---:|:---:|
| `getEndpointPath()` 与接口目录登记值一致（字面量断言） | ✅ | ✅ | ✅ | ✅ |
| 请求构造：URL / headers / HTTP method | ✅ | — | — | — |
| 请求构造：`Query` 条件（含空关键词 → `null`/`match_all`） | — | ✅ | — | — |
| 请求构造：SQL 片段 + 命名参数 + 分页参数透传 | — | — | ✅ | ✅ |
| 响应映射：字段逐一映射为 VO（`assertAll`） | ✅ | ✅ | ✅ | ✅ |
| 空/空白/缺省参数降级 | ✅ | ✅ | ✅ | ✅ |
| 上游错误载荷（如 `{"error":true}`）→ `UPSTREAM_ERROR` | ✅ | — | — | — |
| 畸形 JSON / 空响应 → `UPSTREAM_ERROR` | ✅ | — | — | — |
| 分页参数安全裁剪（`pageNum/pageSize` 越界） | 按需 | ✅ | ✅ | ✅ |
| 敏感字段不外泄（VO 白名单、Header 无硬编码密钥） | ✅ | ✅ | ✅ | ✅ |
| 每个 `throw new BusinessException(...)` 分支至少 1 条用例 | ✅ | ✅ | ✅ | ✅ |

> ES 的深分页窗口拒绝、HTTP 的 4xx/5xx/超时、外部库连接失败等属于模板/执行器职责，
> 在 L2 测试中覆盖，不在每个 Handler 测试里重复。

## 5. 编写规范

### 5.1 目录与命名

```
src/test/java/com/example/api_service/engine/handlers/<domain>/<Handler>Test.java
```

- 测试类与 Handler **同包**（这样同包的 `protected` 覆写方法可直接调用，例如
  `buildTargetUrl`、`buildQuery`、`convertDocToResp`、`doExecute`）；
- 类名 = `<Handler>Test`，方法名 `should<预期>When<条件>` 或 `should<预期>`；
- 类与方法均使用中文 `@DisplayName` 描述业务语义。

### 5.2 结构

- 遵循 Arrange-Act-Assert，一个用例只验证一个行为；
- `@BeforeEach` 中完成 Handler 构造与依赖注入（**不要用字段初始化构造依赖 Mock 的 Handler**，
  此时 `@Mock` 尚未注入）；
- `@AfterEach` 清理 `ApiContext.clear()`（仅调用 `execute()` 或依赖上下文的用例需要）。

### 5.3 断言

- 映射类断言使用 `assertAll(...)` 逐字段校验，禁止只断言 `assertNotNull`；
- 错误路径断言 **`ErrorCode`** + 消息关键片段，禁止断言整段异常文案；
- 数值比较给出容差（如 `assertEquals(37.4056, latitude, 1e-9)`）；
- 分页结果断言 `total/pageNum/pageSize/hasMore/records` 中与业务相关的字段。

### 5.4 Mock 边界

- 只 Mock **外部 IO**：`ElasticsearchClient`、`ExternalSqlQueryExecutor`、`ApiAccessLogService` 等；
- 不 Mock DTO、VO、`PageResult`、`BusinessException` 等值对象；
- 不 Mock 被测 Handler 自身；
- Mockito 使用严格模式（`@ExtendWith(MockitoExtension.class)`），未使用的 stub 要删除；
- JSON 统一使用 Jackson 3：`tools.jackson.databind.json.JsonMapper`（与生产代码一致）。

### 5.5 ApiContext 生命周期

只有需要调用 `execute()` 的用例才绑定上下文：

```java
@BeforeEach
void bindContext() {
    ApiContext.set(new ApiContext());
}

@AfterEach
void clearContext() {
    ApiContext.clear();
}
```

计费回调用匿名实现或 `mock(ApiContext.BillingCallback.class)`，断言 `charge/refund` 的调用次数与金额。

### 5.6 `execute()` 级用例适用性

| 引擎 | 能否安全调用 `execute()` | 做法 |
|---|---|---|
| EXTERNAL_SQL | ✅ | 反射注入 mock `ExternalSqlQueryExecutor`，走完整模板链路 |
| ES | ✅ | 注入 mock `ElasticsearchClient`，stub `search(...)` |
| INTERNAL_SQL | ✅ | 注入 mock Service；需 `ApiContext` 中有 `userId` |
| HTTP_PROXY | ⚠️ 仅当 Handler 暴露可注入 `RestClient` 的构造器 | 否则只测纯方法，模板链路由 `AbstractHttpProxyApiHandlerTest` 覆盖 |

> 推荐每个 Handler 至少 1 条 `execute()` 成功路径用例（可注入依赖时），用于捕获
> “Handler 返回 null / 未正确委托”这类接线错误。

## 6. 模板

### 6.1 HTTP_PROXY（以 `IpGeoLocateApiHandlerTest` 为现有标杆）

```java
package com.example.api_service.engine.handlers.geo;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IP 归属地 HTTP 代理 Handler 单元测试")
class IpGeoLocateApiHandlerTest {

    private final IpGeoLocateApiHandler handler =
            new IpGeoLocateApiHandler(JsonMapper.builder().build(), "https://ipapi.co");

    @Test
    @DisplayName("端点路径必须与接口目录登记一致")
    void shouldExposeCatalogEndpointPath() {
        assertEquals("/api/v1/tools/ip-locate", handler.getEndpointPath());
    }

    @Test
    @DisplayName("应按参数拼装 GET URL 并做路径编码")
    void shouldBuildTargetUrl() {
        IpLocateReq request = new IpLocateReq();
        request.setIp(" 8.8.8.8 ");

        assertEquals("https://ipapi.co/8.8.8.8/json/", handler.buildTargetUrl(request));
    }

    @Test
    @DisplayName("空白 IP 应拒绝为参数错误")
    void shouldRejectBlankIp() {
        IpLocateReq request = new IpLocateReq();
        request.setIp("  ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.buildTargetUrl(request));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("请求头应只包含非敏感字段")
    void shouldBuildHeaders() {
        assertEquals("application/json", handler.buildHeaders().get("Accept"));
        assertFalse(handler.buildHeaders().containsKey("Authorization"));
    }

    @Test
    @DisplayName("上游响应应逐字段映射为标准 VO")
    void shouldMapUpstreamJsonIntoVo() {
        String rawJson = """
                {"ip":"8.8.8.8","country_name":"United States","region":"California",
                 "city":"Mountain View","org":"Google LLC","latitude":37.4056,
                 "longitude":-122.0775,"timezone":"America/Los_Angeles"}
                """;

        IpLocateVO vo = handler.transformResponse(rawJson);

        assertAll(
                () -> assertEquals("8.8.8.8", vo.ip()),
                () -> assertEquals("United States", vo.country()),
                () -> assertEquals("California", vo.region()),
                () -> assertEquals("Mountain View", vo.city()),
                () -> assertEquals("Google LLC", vo.isp()),
                () -> assertEquals(37.4056d, vo.latitude(), 1e-9),
                () -> assertEquals(-122.0775d, vo.longitude(), 1e-9),
                () -> assertEquals("America/Los_Angeles", vo.timezone())
        );
    }

    @Test
    @DisplayName("上游错误载荷应转换为 UPSTREAM_ERROR")
    void shouldTranslateUpstreamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.transformResponse("{\"error\":true,\"reason\":\"Invalid IP Address\"}"));

        assertEquals(ErrorCode.UPSTREAM_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Invalid IP Address"));
    }

    @Test
    @DisplayName("畸形 JSON 应转换为 UPSTREAM_ERROR")
    void shouldRejectMalformedJson() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.transformResponse("not-json"));

        assertEquals(ErrorCode.UPSTREAM_ERROR, ex.getErrorCode());
    }
}
```

### 6.2 ES（以 `PublicNewsSearchApiHandler` 为例）

```java
package com.example.api_service.engine.handlers.news;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("新闻检索 ES Handler 单元测试")
class PublicNewsSearchApiHandlerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private PublicNewsSearchApiHandler handler;

    @BeforeEach
    void setUp() {
        // 依赖 @Mock 已注入后再构造，避免 Objects.requireNonNull 提前失败
        handler = new PublicNewsSearchApiHandler(elasticsearchClient, "news-test");
    }

    @Test
    @DisplayName("端点路径必须与接口目录登记一致")
    void shouldExposeCatalogEndpointPath() {
        assertEquals("/api/v1/search/news", handler.getEndpointPath());
    }

    @Test
    @DisplayName("无关键词时应返回 null 表示 match_all")
    void shouldReturnNullQueryWhenKeywordBlank() {
        NewsSearchPageReq request = new NewsSearchPageReq();
        request.setKeyword("  ");

        assertNull(handler.buildQuery(request));
    }

    @Test
    @DisplayName("有关键词时应构造 title/content 的 multi_match 查询")
    void shouldBuildMultiMatchQuery() {
        NewsSearchPageReq request = new NewsSearchPageReq();
        request.setKeyword(" 台风 ");

        Query query = handler.buildQuery(request);

        assertNotNull(query);
        assertTrue(query.isMultiMatch());
        assertEquals("台风", query.multiMatch().query());
        assertEquals(List.of("title", "content"), query.multiMatch().fields());
    }

    @Test
    @DisplayName("ES 文档应逐字段映射为对外 VO")
    void shouldConvertDocToVo() {
        NewsEsDoc doc = new NewsEsDoc();
        doc.setId("n-1");
        doc.setTitle("标题");
        doc.setContent("正文");
        doc.setSource("来源");
        doc.setUrl("https://example.com/n-1");
        doc.setPublishTime("2026-11-03T09:00:00");

        NewsOpenVO vo = handler.convertDocToResp(doc);

        assertAll(
                () -> assertEquals("n-1", vo.id()),
                () -> assertEquals("标题", vo.title()),
                () -> assertEquals("正文", vo.content()),
                () -> assertEquals("来源", vo.source()),
                () -> assertEquals("https://example.com/n-1", vo.url()),
                () -> assertEquals("2026-11-03T09:00:00", vo.publishTime())
        );
    }
}
```

### 6.3 EXTERNAL_SQL（以 `PgSampleUserApiHandler` 为例）

```java
package com.example.api_service.engine.handlers.pg;

import com.example.api_service.common.PageResult;
import com.example.api_service.engine.external.ExternalSqlQueryExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PostgreSQL 示例用户外部 SQL Handler 单元测试")
class PgSampleUserApiHandlerTest {

    private final PgSampleUserApiHandler handler = new PgSampleUserApiHandler();

    private ExternalSqlQueryExecutor queryExecutor;

    @BeforeEach
    void setUp() {
        queryExecutor = mock(ExternalSqlQueryExecutor.class);
        // queryExecutor 是 AbstractExternalSqlApiHandler 的私有 @Autowired 字段
        ReflectionTestUtils.setField(handler, "queryExecutor", queryExecutor);
    }

    @Test
    @DisplayName("端点路径必须与接口目录登记一致")
    void shouldExposeCatalogEndpointPath() {
        assertEquals("/api/v1/pg/sample-users", handler.getEndpointPath());
    }

    @Test
    @DisplayName("应把分页参数与筛选条件透传给执行器")
    @SuppressWarnings("unchecked")
    void shouldPassPagingAndFiltersToExecutor() throws Exception {
        PgSampleUserPageReq request = new PgSampleUserPageReq();
        request.setPageNum(2);
        request.setPageSize(50);
        request.setUsername(" admin ");
        request.setSuperuser(true);

        doReturn(PageResult.of(List.of(), 0L, 2, 50)).when(queryExecutor)
                .executePage(eq("pg-local"), anyString(), anyMap(), eq(2), eq(50), any());

        PageResult<PgSampleUserOpenVO> result = handler.doExecute(request);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryExecutor).executePage(eq("pg-local"), sqlCaptor.capture(),
                paramCaptor.capture(), eq(2), eq(50), any());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("SELECT"));
        assertTrue(sql.contains("usename LIKE :username"));
        assertTrue(sql.contains("usesuper = :superuser"));
        assertEquals("%admin%", paramCaptor.getValue().get("username"));
        assertEquals(true, paramCaptor.getValue().get("superuser"));
        assertEquals(0L, result.total());
    }

    @Test
    @DisplayName("无筛选条件时不应拼接 WHERE 子句")
    @SuppressWarnings("unchecked")
    void shouldOmitOptionalFilters() throws Exception {
        PgSampleUserPageReq request = new PgSampleUserPageReq();

        doReturn(PageResult.empty(1, 20)).when(queryExecutor)
                .executePage(eq("pg-local"), anyString(), anyMap(), eq(1), eq(20), any());

        handler.doExecute(request);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryExecutor).executePage(eq("pg-local"), sqlCaptor.capture(),
                paramCaptor.capture(), eq(1), eq(20), any());

        assertFalse(sqlCaptor.getValue().contains("LIKE"));
        assertFalse(sqlCaptor.getValue().contains("usesuper ="));
        assertTrue(paramCaptor.getValue().isEmpty());
    }

    @Test
    @DisplayName("RowMapper 应把结果集列映射为 VO")
    @SuppressWarnings("unchecked")
    void shouldMapResultSetRowToVo() throws Exception {
        PgSampleUserPageReq request = new PgSampleUserPageReq();
        ArgumentCaptor<RowMapper<PgSampleUserOpenVO>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);

        doReturn(PageResult.empty(1, 20)).when(queryExecutor)
                .executePage(eq("pg-local"), anyString(), anyMap(), anyInt(), anyInt(),
                        mapperCaptor.capture());

        handler.doExecute(request);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("10");
        when(rs.getString("username")).thenReturn("postgres");
        when(rs.getObject("is_superuser")).thenReturn(true);
        when(rs.getBoolean("is_superuser")).thenReturn(true);
        when(rs.getObject("can_update_catalog")).thenReturn(false);
        when(rs.getString("valid_until")).thenReturn("infinity");

        PgSampleUserOpenVO vo = mapperCaptor.getValue().mapRow(rs, 0);

        assertAll(
                () -> assertEquals("10", vo.userId()),
                () -> assertEquals("postgres", vo.username()),
                () -> assertTrue(vo.superuser()),
                () -> assertFalse(vo.canUpdateCatalog()),
                () -> assertEquals("infinity", vo.validUntil())
        );
    }
}
```

### 6.4 INTERNAL_SQL（以 `AccessLogExportApiHandler` 为例）

本地 SQL Handler 最容易写出“Mock 整条 MyBatis-Plus 链”的脆弱测试。**标准做法：把查询下沉为
Service 的显式方法**，Handler 只负责用户隔离与 VO 映射。下列示例**以完成该重构为前提**：

```java
// 前置重构：ApiAccessLogService 增加
// PageResult<ApiAccessLog> pageUserLogs(Long userId, Integer httpStatus, int pageNum, int pageSize);

class AccessLogExportApiHandlerTest {

    private final ApiAccessLogService apiAccessLogService = mock(ApiAccessLogService.class);
    private final AccessLogExportApiHandler handler =
            new AccessLogExportApiHandler(apiAccessLogService);

    @BeforeEach
    void bindContext() {
        ApiContext.set(new ApiContext());
    }

    @AfterEach
    void clearContext() {
        ApiContext.clear();
    }

    @Test
    @DisplayName("应按当前用户隔离查询并映射为 VO")
    void shouldScopeQueryToCurrentUser() {
        ApiContext.get().setUserId(42L);
        LogQueryPageReq request = new LogQueryPageReq();
        request.setPageNum(1);
        request.setPageSize(20);
        request.setHttpStatus(200);

        when(apiAccessLogService.pageUserLogs(42L, 200, 1, 20))
                .thenReturn(PageResult.of(List.of(accessLog()), 1L, 1, 20));

        PageResult<AccessLogOpenVO> result = handler.doExecute(request);

        assertEquals(1L, result.total());
        assertAll(
                () -> assertEquals(200, result.records().get(0).httpStatus()),
                () -> assertEquals("/api/v1/pg/sample-users", result.records().get(0).endpointPath())
        );
    }

    @Test
    @DisplayName("上下文缺失用户时应拒绝为未认证")
    void shouldRejectWhenUserMissing() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.doExecute(new LogQueryPageReq()));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}
```

> 若短期内无法重构，可退化为 `@MybatisPlusTest`/`@SpringBootTest` + H2 的集成测试，但该测试
> 应归入 L3，不应命名为 Handler 单元测试，也不应放进本标准的 L1 范围。

### 6.5 占位 Handler（自动生成、尚未实现）

`PgMysqlqryApiHandler` 这类仍抛 `UnsupportedOperationException` 的脚手架，在实现前只要求：

- `getEndpointPath()` 与目录一致；
- `doExecute` 当前明确抛出 `UnsupportedOperationException`（防止误上线）。

实现后按 6.4 补齐完整用例。

## 7. 模板契约测试（一次性）

为避免每个 Handler 重复测试公共逻辑，应补以下测试类（包与模板类同包）：

### `AbstractApiHandlerTest`

- 无 `ApiContext` 时 `execute()` → `UNAUTHORIZED`；
- 成功路径：`charge` 被调用、`billable=true`、`executionTimeMs >= 0`；
- `doExecute` 抛 `BusinessException` → 原样抛出、`refund` 被调用、`billable=false`；
- `doExecute` 抛运行时异常 → 包装为 `UPSTREAM_ERROR`、`refund` 被调用；
- `charge` 返回 false → `QUOTA_INSUFFICIENT`，`doExecute` 不被调用；
- 分页请求 `pageSize=50` → `billingMultiplier=3`。

### `AbstractHttpProxyApiHandlerTest`

使用 `MockRestServiceServer.bindTo(RestClient.builder())` 注入测试用 `RestClient`：

- GET 请求不发送 body、POST 请求序列化 body；
- 请求头被正确注入；
- 空响应体 → `UPSTREAM_ERROR`；
- 上游 4xx/5xx → `UPSTREAM_ERROR`，消息包含状态码；
- 连接超时/`ResourceAccessException` → `UPSTREAM_ERROR`。

### `AbstractEsApiHandlerTest`

- `from + size > 10000` → `PARAM_ERROR`；
- 捕获 `SearchRequest`，断言 `index/from/size/trackTotalHits/query`；
- `hits.total() == null` 时总数回退为 `records.size()`；
- `IOException` / `ElasticsearchException` → `UPSTREAM_ERROR`。

ES 响应夹具用官方 builder 构造，禁止手写 JSON：

```java
SearchResponse<NewsEsDoc> response = SearchResponse.of(r -> r
        .took(1)
        .hits(h -> h
                .total(t -> t.value(1))
                .hits(List.of(Hit.of(hit -> hit.source(doc))))));
```

## 8. 覆盖率与 CI 门禁

| 指标 | 门槛 |
|---|---|
| Handler 类行覆盖 | ≥ 90% |
| Handler 类分支覆盖 | ≥ 80% |
| 每个 `BusinessException` 抛出分支 | 100% 覆盖 |
| 单个 L1 测试类运行时间 | < 1s |
| 全量测试 | `mvn test`（JDK 21）零失败 |

当前 `pom.xml` 尚未接入 JaCoCo。建议后续在 `verify` 阶段加入覆盖率检查：

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>check</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>CLASS</element>
            <includes>
              <include>com.example.api_service.engine.handlers.*</include>
            </includes>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.90</minimum></limit>
              <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## 9. 反模式

- ❌ 用 `@SpringBootTest` 写 Handler 单元测试；
- ❌ 每个 Handler 重复测扣费/退款/鉴权/限流；
- ❌ 只断言 `assertNotNull(result)` 或 `assertTrue(result != null)`；
- ❌ 真实调用第三方 API、真实 ES、真实外部数据库；
- ❌ Mock DTO/VO/`PageResult` 等值对象；
- ❌ 在测试代码中硬编码真实密钥或生产地址；
- ❌ 断言整段异常消息（脆弱），应断言 `ErrorCode` + 关键片段；
- ❌ 用 `Thread.sleep` 等待异步结果；
- ❌ 在字段初始化阶段构造依赖 `@Mock` 的 Handler（Mock 尚未注入）。

## 10. PR 自查清单

- [ ] 新增/修改 Handler 是否同步新增 `<Handler>Test`？
- [ ] 是否覆盖 `getEndpointPath()` 字面量断言？
- [ ] 是否覆盖正常映射（`assertAll` 逐字段）？
- [ ] 是否覆盖空/空白参数与缺省值降级？
- [ ] 是否覆盖所有 `BusinessException` 分支（错误码断言）？
- [ ] 是否覆盖分页参数透传/裁剪（分页 Handler）？
- [ ] 是否确认无 Spring 上下文、无网络、无真实存储依赖？
- [ ] 是否使用 `tools.jackson` 的 `JsonMapper`？
- [ ] 是否在 `@AfterEach` 清理 `ApiContext`？
- [ ] 全量 `mvn test`（JDK 21）是否通过？

## 11. 现状与待补清单

**已有标杆**

- `engine/handlers/geo/IpGeoLocateApiHandlerTest`（4 例，HTTP_PROXY）

**待补 Handler（按本标准的 L1 要求）**

| Handler | 引擎类型 | 重点用例 |
|---|---|---|
| `PublicNewsSearchApiHandler` | ES | `buildQuery` 空/有关键词、`convertDocToResp` |
| `EsEsqryApiHandler` | ES | `buildQuery` 返回 null、文档映射 |
| `EsDetailqryApiHandler` | ES | 文档映射、索引名 |
| `EsApiqryApiHandler` | HTTP_PROXY | URL/headers、上游错误、JSON 映射 |
| `PgWeatherqryApiHandler` | HTTP_PROXY | URL/headers、上游错误、JSON 映射 |
| `PgSampleUserApiHandler` | EXTERNAL_SQL | SQL 片段、命名参数、`RowMapper` |
| `AccessLogExportApiHandler` | INTERNAL_SQL | 用户隔离、分页透传、VO 映射 |
| `PgMysqlqryApiHandler` | INTERNAL_SQL（占位） | 仅 endpointPath + 未实现保护 |

**待补模板契约测试（L2）**

- `AbstractApiHandlerTest`
- `AbstractHttpProxyApiHandlerTest`
- `AbstractEsApiHandlerTest`

**建议补的执行器测试**

- `ExternalSqlQueryExecutorTest`（只读审计、方言分页、空结果、连接异常）
