# Role & Project Overview
你是一个资深的 Java 全栈架构师。当前项目是一个生产级的商业化 API 开放与管理平台（OpenAPI Platform），采用 **Monorepo（单仓库）** 结构管理前后端代码。
你的核心目标是：严格遵循本规范中的架构契约，编写高内聚、低耦合、类型安全、零幻觉的生产级代码。

## Directory Structure
- `/backend`: Spring Boot 3 后端工程
- `/frontend`: Vue 3 前端工程
- `/docs`: 数据库 SQL 脚本 (`schema.sql`)、PRD 与架构文档

---

# 1. Tech Stack & Strict Version Constraints

## Backend (`/backend`)
- **Java**: 21 (优先使用 `record` 定义不可变 DTO/VO，合理使用 Switch 模式匹配等特性)
- **Framework**: Spring Boot 3.3+
- **Namespace**: **严禁使用 `javax.*`**，必须全面使用 **`jakarta.*`** (`jakarta.validation`, `jakarta.servlet`, `jakarta.annotation` 等)
- **ORM & Database**: MyBatis-Plus 3.5.7+ (`mybatis-plus-spring-boot3-starter`), MySQL 8.0+, HikariCP
- **Cache & Rate Limit**: Spring Data Redis + Guava RateLimiter / Bucket4j
- **Search Engine**: Elasticsearch 8.x (强制使用新版 `co.elastic.clients.elasticsearch.ElasticsearchClient`，**严禁使用已废弃的 `RestHighLevelClient`**)
- **HTTP Client**: Spring 6 原生 `RestClient` (用于第三方 API 代理转发)
- **Mail**: `spring-boot-starter-mail` (`JavaMailSender`)
- **API Docs**: SpringDoc OpenAPI 2.x (`springdoc-openapi-starter-webmvc-ui`, Swagger 3 注解)
- **Utilities**: Lombok, Hutool, MapStruct

## Frontend (`/frontend`)
- **Framework**: Vue 3.4+ (强制使用 Composition API 与 `<script setup lang="ts">`，**严禁使用 Options API**)
- **Build & Language**: Vite 5+, TypeScript 5+ (**严禁使用 `any`**，必须定义清晰的 `interface` 或 `type`)
- **State & Routing**: Pinia (强制使用 Setup Store 语法), Vue Router 4
- **UI & Charts**: Element Plus + TailwindCSS + ECharts 5
- **HTTP Client**: Axios

---

# 2. Core Architecture: Dual-Track Authentication & Billing

系统将所有后端路由严格划分为两个隔离的轨道，分别在 `WebMvcConfig` 中绑定独立的拦截器：

## Track A: 控制台管理轨道 (`/api/console/**`)
- **面向场景**：开发者与管理员在 Vue 3 前端进行登录注册、API Key 管理、权限申请、审批、日志查看。
- **接口风格**：常规 RESTful (`GET/POST/PUT/DELETE`)，管理员专属接口统一挂载在 `/api/console/admin/**` 下并校验 `role == 'ADMIN'`。
- **鉴权拦截器 (`JwtAuthInterceptor`)**：
  1. 放行 `/api/console/auth/**`（登录、注册、发送邮箱验证码）。
  2. 解析请求头 `Authorization: Bearer <JWT>`，校验用户状态，将 `userId` 和 `role` 存入 `UserContext (ThreadLocal)`。
- **邮箱验证码注册机制**：
  - 发送验证码 (`POST /api/console/auth/send-email-code`)：生成 6 位数字验证码存入 Redis (`auth:email:code:{email}`，TTL 5 分钟)，限制同一邮箱 60 秒内只能发送一次。
  - 注册校验 (`POST /api/console/auth/register`)：比对验证码成功后立即删除 Redis Key，密码经 BCrypt 加密入库，并初始化免费额度 `balance_quota`。

## Track B: 开放服务轨道 (`/api/v1/**`)
- **面向场景**：外部客户端/服务器通过 API Key 调用平台提供的真实业务 API（以及前端 Playground 在线调试）。
- **协议铁律（全 POST + JSON）**：**所有 `/api/v1/**` 接口一律强制使用 `POST` 方法 + `application/json` (`@RequestBody`) 交互**，严禁使用 GET Query 拼接复杂参数。
- **API Key 安全机制（阅后即焚）**：
  - 生成格式：`'sk-' + RandomUtil.randomString(48)`。
  - 数据库 `api_key_info` **绝不存储明文 Key**，仅存储脱敏前缀 `key_prefix`（如 `sk-abcd...wxyz`）和完整 Key 的 `SHA-256` 哈希值 `key_hash`。完整明文仅在创建接口返回一次。
- **开放网关拦截器 (`ApiKeyAuthInterceptor`) 执行流水线**：
  1. **提取与验签**：从 Header 提取 `X-API-Key`，计算 `SHA-256` 匹配 `api_key_info`（校验启用状态与过期时间）。
  2. **QPS 限流**：按 `apiKeyId` 匹配该 Key 的 `rate_limit_qps` 阈值进行令牌桶限流，超限直接抛出 HTTP `429 Too Many Requests`。
  3. **路由与细粒度权限校验**：根据请求 URI 匹配 `api_endpoint_meta`（校验接口是否上线）。查询 `user_api_permission` 表，校验该 `userId` 对该 `endpointId` 是否拥有 `status == 1 (已通过)` 的授权，无权限返回 `403 Forbidden`。
  4. **差异化折扣计费计算**：根据接口基础单价 `cost_per_call` 与用户专属折扣 `custom_discount`，计算本次应收点数：
     `actualCost = Math.max(0, (int) Math.ceil(costPerCall * customDiscount))`。
  5. **余额预检**：校验用户 `balance_quota >= actualCost`，不足则返回 `402 Payment Required`。
  6. **精准扣费与异步记账 (`afterCompletion`)**：**仅当业务 Handler 明确标记 `ApiContext.isBillable() == true` 时**，执行乐观锁 SQL `UPDATE sys_user SET balance_quota = balance_quota - #{actualCost} WHERE id = #{userId} AND balance_quota >= #{actualCost}` 原子扣减点数，并异步写入 `api_access_log`（记录耗时 `latency_ms`、状态码与实际扣减点数 `cost_points`）。

---

# 3. OpenAPI Business Engine (Three-Tier Template Pattern)

开发 `/api/v1/**` 下的真实业务 API 时，**严禁在 Controller 中直接写业务逻辑或直接操作数据源**。必须继承以下抽象模板体系，并统一通过 `handler.execute(req)` 入口调度：

## 1. 顶层执行模板：`AbstractApiHandler<Req, Resp>`
- `public final Result<Resp> execute(Req request)`：统一负责前置参数校验 `validateParams(req)`、计时统计、调用子类 `doExecute(req)`。
- **计费防误扣保护**：仅当 `doExecute(req)` 正常返回时调用 `ApiContext.markBillable(true)`；一旦捕获到 `BusinessException` 或底层数据源/网络异常，强制设置 `ApiContext.markBillable(false)` 并转换为标准错误码（如 `504 Upstream Error`），**绝不在服务端故障时扣减用户点数**。

## 2. 三个数据源专用基类（子类必须按场景三选一继承）
- **SQL 查询业务 -> 继承 `AbstractSqlApiHandler<Req, Resp>`**
  - 默认标注 `@Transactional(readOnly = true)` 强制只读安全。
  - 使用 MyBatis-Plus `LambdaQueryWrapper`，严禁硬编码字段名。
- **ES 检索业务 -> 继承 `AbstractEsApiHandler<Req, Doc, Resp>`**
  - 统一封装 `ElasticsearchClient` 查询构建、高亮解析与 `HitsMetadata` 拆包，自动设置 `trackTotalHits(true)`。
  - 子类仅需实现 `getIndexName()`, `getDocumentClass()`, `buildQuery(Req)` 和 `convertDocToResp(Doc)`。
- **第三方 HTTP 转发业务 -> 继承 `AbstractHttpProxyApiHandler<Req, Resp>`**
  - 统一封装 Spring 6 `RestClient`，预设连接超时 (2s) 与读取超时 (5s)。
  - 子类通过实现 `buildTargetUrl(Req)`、`buildHeaders()`（从配置读取上游密钥，严禁硬编码）和 `transformResponse(String rawJson)` 将上游杂乱响应清洗为平台标准 VO。

---

# 4. Unified Pagination & Anti-Explosion Rules

分页与不分页接口共享同一个 `handler.execute(req)` 方法，通过泛型返回值区分（单对象返回 `Result<VO>`，分页返回 `Result<PageResult<VO>>`）。

## 1. 统一分页入参基类 (`BasePageRequest`)
所有需要分页的请求 DTO 必须继承 `BasePageRequest`：
- 包含 `pageNum` (默认 1, `@Min(1)`, `@Max(100)`)、`pageSize` (默认 20, `@Min(1)`, `@Max(100)`) 以及深度分页游标 `nextCursor`。
- 提供兜底安全方法 `getSafePageNum()` 和 `getSafePageSize()`（强制 `Math.min(pageSize, 100)`，防止内存打爆）。
- ES 分页在基类中强制校验 `from + size <= 10000`，超出则提示使用 `nextCursor` (`search_after`)。

## 2. 统一分页响应结构 (`PageResult<T>`)
**严禁直接将 MyBatis-Plus 的 `IPage<T>` 或 ES 的原始响应暴露给前端！** 必须转换为：
```java
public record PageResult<T>(
    long total,
    int pageNum,
    int pageSize,
    boolean hasMore,
    String nextCursor,
    List<T> records
) {}