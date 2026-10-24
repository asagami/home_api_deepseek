# OpenAPI Platform · 商业化 API 开放与管理平台

一个前后端一体的 Monorepo 项目：后端提供 **控制台管理** 与 **开放 API 网关** 双轨服务，前端提供 API Key 管理、接口市场、权限申请、在线调试、调用日志、点数充值与管理员后台。

本文档覆盖四部分内容：

1. 项目整体结构与技术栈
2. 统一报文规范（响应、分页、错误码、请求约定）
3. 新增 **SQL 查询接口 / ES 检索接口 / 第三方 API 转发接口** 的开发规范
4. 可直接参照的示例代码与落地检查清单

---

## 1. 项目结构

```
home_api_deepseek/
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/com/example/api_service/
│   │   ├── common/           # Result、PageResult、ErrorCode、BusinessException、全局异常处理
│   │   ├── config/           # WebMvcConfig、MyBatis-Plus、Elasticsearch、Swagger 配置
│   │   │   └── interceptor/  # 开放网关拦截器（计费上下文 + 调用记账）
│   │   ├── security/         # JWT、API Key 工具、鉴权拦截器、UserContext、@RequireAdmin
│   │   ├── controller/       # 控制台 REST 接口
│   │   │   └── gateway/      # /api/v1/** 动态统一网关
│   │   ├── service/          # 业务接口 + impl/
│   │   ├── mapper/           # MyBatis-Plus Mapper
│   │   ├── model/
│   │   │   ├── entity/       # 数据库实体
│   │   │   ├── dto/          # 入参
│   │   │   ├── vo/           # 出参
│   │   │   └── enums/        # 状态、套餐、支付渠道等枚举
│   │   ├── engine/           # 开放 API 三层模板引擎
│   │   │   ├── AbstractApiHandler.java
│   │   │   ├── AbstractSqlApiHandler.java
│   │   │   ├── AbstractEsApiHandler.java
│   │   │   ├── AbstractHttpProxyApiHandler.java
│   │   │   ├── context/ApiContext.java
│   │   │   └── handlers/     # 参考实现：geo / news / log
│   │   └── ApiServiceApplication.java
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application.yml
│   │   └── mapper/ApiAccessLogMapper.xml
│   ├── src/test/             # MockMvc + H2 集成测试
│   └── pom.xml
├── frontend/                 # Vue 3 控制台
│   └── src/
│       ├── api/              # 按领域拆分的类型化接口模块
│       ├── composables/      # usePagination、useCountdown
│       ├── layout/           # 控制台布局
│       ├── router/           # 路由与登录/管理员守卫
│       ├── stores/           # Pinia Setup Store
│       ├── utils/            # request.ts、auth.ts、requestSample.ts 等
│       └── views/            # auth + console 各页面
├── docs/
│   └── schema.sql            # 全量建表脚本与示例接口目录
└── MEMORY.md                 # 架构规范（部分内容被截断，以代码为准）
```

### 1.1 后端分层

| 层 | 职责 | 约定 |
|---|---|---|
| `controller` | 路由、参数接收、调用 Service | 不写业务逻辑；不直接返回 Entity |
| `service` | 业务编排、事务边界 | 接口 + `impl/`；构造器注入 |
| `mapper` | 数据访问 | 继承 `BaseMapper<T>`；复杂 SQL 放 `resources/mapper/*.xml` |
| `model` | 数据模型 | `entity` / `dto` / `vo` 严格分离 |
| `common` | 公共契约 | `Result`、`PageResult`、`ErrorCode`、`BusinessException` |
| `engine` | 开放 API 业务模板 | 所有 `/api/v1/**` 的真实业务只写在这里 |

依赖注入统一使用 `@RequiredArgsConstructor` + `private final`，禁止字段注入 `@Autowired`。所有 `javax.*` 一律不得使用，必须为 `jakarta.*`。

### 1.2 数据表

`docs/schema.sql` 包含 7 张表：

| 表 | 用途 |
|---|---|
| `sys_user` | 开发者/管理员账号、余额点数、封禁状态 |
| `api_key_info` | API Key（仅存 SHA-256 与脱敏前缀） |
| `api_endpoint_meta` | 对外接口目录（路径、方法、单价、请求/响应示例、上下线） |
| `user_api_permission` | 用户对接口的申请、审批状态与专属折扣 |
| `api_access_log` | 调用流水（状态码、耗时、实际扣点） |
| `recharge_order` | 点数充值订单 |
| `sys_email_code` | 邮箱验证码临时表（当前实现以 Redis 为准） |

---

## 2. 技术栈（以实际依赖为准）

> 根目录 `MEMORY.md` 的技术栈描述早于脚手架实际版本，以下为 **代码中的真实版本**。

**后端**

- Java 21（必须；JDK 27 会触发 Lombok 崩溃）
- Spring Boot **4.1.1**（Spring Web 7.0.x、Jackson **3**，即 `tools.jackson.*`）
- MyBatis-Plus **3.5.17**（`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser`）
- MySQL 8 / MariaDB（本地 HikariCP 连接 `open_api_platform`）
- Elasticsearch Java Client **9.4.5**（`Rest5Client` + `Jackson3JsonpMapper`）
- SpringDoc **3.1.1**
- Redis（验证码）、Guava RateLimiter（QPS）、Hutool、Lombok、spring-security-crypto（BCrypt）

**前端**

- Vue **3.5** + TypeScript **6** + Vite **8**
- Vue Router **5**、Pinia **4**
- Element Plus + Tailwind CSS **4** + ECharts **5.6**
- Axios **1.20**

---

## 3. 快速开始

### 3.1 环境要求

- JDK 21（Temurin 21）
- MySQL 8 / MariaDB，已执行 `docs/schema.sql`
- Redis（注册验证码需要）
- Elasticsearch 9（可选；未启动不影响应用启动，仅在调用 ES 接口时返回 `50400`）
- Node.js 22.18+ / 24.12+，npm

### 3.2 启动后端

```bash
cd backend

# 必须使用 JDK 21
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

# 本地数据库专用账号（root 与 OS 用户无法通过 TCP 登录）
export DB_USERNAME=api_service
export DB_PASSWORD=api_service_pass

./mvnw spring-boot:run
```

- 服务端口：`8080`
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 健康检查：`GET /api/health`

邮件相关环境变量（仅注册发码时需要真实 SMTP）：

```bash
export MAIL_HOST=smtp.example.com
export MAIL_PORT=465
export MAIL_USERNAME=no-reply@example.com
export MAIL_PASSWORD=******
```

### 3.3 启动前端

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173，/api 代理到 8080
npm run build    # 类型检查 + 构建
npm run lint
```

### 3.4 运行测试

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./mvnw test
```

测试使用内存 H2（`src/test/resources/schema-h2.sql`），不依赖本地 MySQL。

---

## 4. 双轨路由与鉴权

| 轨道 | 路径 | 鉴权 | 风格 |
|---|---|---|---|
| 控制台轨道 | `/api/console/**` | `Authorization: Bearer <JWT>` | 常规 REST，`GET/POST/PUT/PATCH/DELETE` |
| 管理端 | `/api/console/admin/**` | JWT + `@RequireAdmin` | REST |
| 开放轨道 | `/api/v1/**` | `X-API-Key: sk-...` | **只允许 POST + JSON** |
| 过渡期旧接口 | `/api/v1/services/echo`、`/text-stats` | 旧版拦截器 | 历史遗留，勿再新增 |

### 4.1 控制台鉴权

`JwtAuthInterceptor` 拦截 `/api/console/**`，放行：

- `POST /api/console/auth/register`
- `POST /api/console/auth/login`
- `POST /api/console/auth/send-email-code`

解析成功后写入 `UserContext`（ThreadLocal），业务代码通过 `UserContext.getUserId()` 取当前用户。`AdminAuthInterceptor` 在其后执行，只拦截标注了 `@RequireAdmin` 的类或方法。

### 4.2 开放网关流水线

`config/interceptor/ApiKeyAuthInterceptor`（Bean 名 `gatewayApiKeyAuthInterceptor`）对 `/api/v1/**` 依次执行：

1. 非 `POST` → `40500`（HTTP 405）
2. 提取 `X-API-Key`（格式 `sk-` + 48 位随机串，数据库只存 SHA-256 与脱敏前缀），SHA-256 后查 `api_key_info`，校验启用/过期
3. 校验用户存在且未封禁
4. 按 `rate_limit_qps` 做进程内 Guava `RateLimiter` 限流 → 超限 `42900`（HTTP 429）
5. 按路径 + `POST` 匹配 `api_endpoint_meta`，未登记或未上线 → `40404`
6. 校验 `user_api_permission` 存在 `status=1` 的授权 → 否则 `40304`
7. 写入 `ApiContext`：用户、Key、接口、基础单价、专属折扣、余额
8. 请求完成后，仅当 Handler 标记 `billable=true` 才扣费并异步写 `api_access_log`

### 4.3 计费规则

```text
billingMultiplier = ceil(safePageSize / 20)        // 分页接口，每 20 条一个计费单元；非分页固定 1
finalCost         = max(0, ceil(baseCostPerCall × customDiscount × billingMultiplier))
```

- 接口基础单价来自 `api_endpoint_meta.cost_per_call`。
- 折扣来自审批通过的 `user_api_permission.custom_discount`（`0.00` 为免费特权）。
- 执行前做余额预检，不足返回 `40201` / HTTP 402。
- **服务端故障绝不扣费**：`AbstractApiHandler` 在捕获任何异常时强制 `markBillable(false)`。
- 扣费使用条件更新：`UPDATE sys_user SET balance_quota = balance_quota - ? WHERE id = ? AND balance_quota >= ?`。
- 调用日志异步写入，失败不影响主流程。

---

## 5. 报文规范

### 5.1 统一响应 `Result<T>`

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1761292800000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | **业务码，0 表示成功**（不是 HTTP 200） |
| `message` | string | 提示信息，失败时为可展示的原因 |
| `data` | T | 业务数据，失败时为 `null` |
| `timestamp` | long | 服务端毫秒时间戳 |

构造优先使用工厂方法，禁止手写 `new Result<>(...)`：

```java
Result.success(data);                  // 成功 + 数据
Result.success();                      // 成功 + 无数据
Result.of(code, message, data);        // 自定义业务码
Result.failure(ErrorCode.PARAM_ERROR); // 失败
```

### 5.2 统一分页 `PageResult<T>`

作为 `Result.data` 使用，**字段顺序固定**：

```json
{
  "total": 42,
  "pageNum": 1,
  "pageSize": 20,
  "hasMore": true,
  "nextCursor": null,
  "records": []
}
```

- 页码从 `1` 开始，`pageSize` 上限 100。
- `hasMore` 默认由 `pageNum × pageSize < total` 推导。
- `nextCursor` 预留给 `search_after` 深分页。
- 前端 TypeScript 镜像结构位于 `src/api/types.ts`。
- **严禁** 把 MyBatis-Plus `IPage` 或 ES 原始响应直接返回。

分页入参统一继承 `BasePageRequest`：

| 字段 | 默认 | 约束 |
|---|---|---|
| `pageNum` | 1 | `@Min(1)`、`@Max(100)` |
| `pageSize` | 20 | `@Min(1)`、`@Max(100)` |
| `nextCursor` | null | 深分页游标 |

业务代码使用 `getSafePageNum()` / `getSafePageSize()` / `getOffset()`，不要直接信任入参。

### 5.3 错误码

业务码与 HTTP 状态码**双轨**：业务码前缀即 HTTP 语义，但两者独立返回。新增错误码必须遵循 `<HTTP><2位>` 编号规则，并同时指定 `HttpStatus`。

| 区间 | 含义 | 代表 |
|---|---|---|
| `0` | 成功 | `0 / success` |
| `400xx` | 参数/请求体 | `40000` 参数错误、`40001` 验证码错误 |
| `401xx` | 未认证 | `40100` 未登录、`40101` 缺少 Key、`40102` Key 无效、`40105` 缺少令牌 |
| `402xx` | 余额不足 | `40200` 额度不足、`40201` 本次调用点数不足 |
| `403xx` | 无权限 | `40300` 禁止访问、`40301` 账号封禁、`40304` 无接口权限 |
| `404xx` | 资源不存在 | `40400` 资源不存在、`40404` 接口未登记 |
| `405xx` | 方法不允许 | `40500` 非 POST 调用开放接口 |
| `409xx` | 状态冲突 | `40900` 冲突、`40902` 已有待审批申请、`40904` 订单已支付 |
| `429xx` | 限流 | `42900` 请求过频、`42901` 验证码发送过频 |
| `500xx` | 服务端 | `50000` 内部错误、`50001` 邮件发送失败 |
| `504xx` | 上游异常 | `50400` 第三方/ES 调用失败 |

完整枚举见 `common/ErrorCode.java`。

失败响应示例：

```json
{
  "code": 40304,
  "message": "未获得该接口的调用权限，请先在控制台提交申请",
  "data": null,
  "timestamp": 1761292800000
}
```

### 5.4 请求报文

**开放接口（`/api/v1/**`）**

- 方法：只允许 `POST`
- `Content-Type: application/json`
- 认证：`X-API-Key: sk-xxxxxxxx`
- 请求体：JSON 对象；空请求体按 `{}` 处理
- 字段校验失败返回 `40000`，消息格式为 `字段名: 校验信息`（多字段用 `;` 连接）
- 请求体不是合法 JSON 或字段类型不匹配 → `40000`
- 未在网关注册的路径 → `40404`

```bash
curl -X POST 'http://localhost:8080/api/v1/search/news' \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: sk-your-api-key' \
  -d '{"keyword":"人工智能","pageNum":1,"pageSize":20}'
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 128,
    "pageNum": 1,
    "pageSize": 20,
    "hasMore": true,
    "nextCursor": null,
    "records": [
      { "id": "n-1", "title": "人工智能行业观察", "source": "example" }
    ]
  },
  "timestamp": 1761292800000
}
```

**控制台接口（`/api/console/**`）**

- 认证：`Authorization: Bearer <JWT>`
- 入参：`@Valid @RequestBody` 或带校验注解的 Query 参数
- 成功同样返回 `Result<T>`；列表返回 `Result<PageResult<T>>`
- 调用方通过 `consoleHttp` 访问，响应拦截器会自动解包 `data`

---

## 6. 开放 API 引擎：三层模板

所有 `/api/v1/**` 业务都通过 `engine` 下的四个基类实现，**禁止在 Controller 中写业务逻辑或直接操作数据源**。

```text
AbstractApiHandler<Req, Resp>                 // 执行入口：上下文、计费倍率、余额预检、计时、异常兜底
├── AbstractSqlApiHandler<Req, Resp>          // SQL 查询：只读事务 + MP 分页 + PageResult 转换
├── AbstractEsApiHandler<Req, Doc, Resp>      // ES 检索：防深分页 + trackTotalHits + 命中转换
└── AbstractHttpProxyApiHandler<Req, Resp>    // 第三方转发：RestClient + 超时 + 上游响应清洗
```

### 6.1 模板契约

| 基类 | 子类必须实现 | 基类已兜底 |
|---|---|---|
| `AbstractApiHandler` | `getEndpointPath()`、`doExecute()` | 上下文校验、分页计费倍率、余额预检、参数校验钩子、耗时统计、异常→标准错误码、失败不扣费 |
| `AbstractSqlApiHandler` | 同上 | 类级 `@Transactional(readOnly = true)`、`buildMpPage()`、`toPageResult()` |
| `AbstractEsApiHandler` | `getEndpointPath()`、`getIndexName()`、`getDocumentClass()`、`buildQuery()`、`convertDocToResp()` | 执行查询、`from + size > 10000` 拦截、`trackTotalHits(true)`、空 `_source` 过滤、`PageResult` 组装、异常→`50400` |
| `AbstractHttpProxyApiHandler` | `getEndpointPath()`、`buildTargetUrl()`、`buildHeaders()`、`transformResponse()` | 2s 连接 / 5s 读取超时、GET/POST 分发、4xx/5xx/超时/空响应→`50400` |

### 6.2 通用开发流程

1. 在 `engine/handlers/<domain>/` 下新建请求 DTO、响应 VO（ES 另需文档类）。
2. 继承对应基类，加 `@Component`，实现抽象方法。
3. `getEndpointPath()` 返回值必须与 `api_endpoint_meta.path` **逐字符一致**，且以 `/api/v1/` 开头。
4. 在 `docs/schema.sql` 的 `api_endpoint_meta` 中登记目录行（`method` 必须为 `POST`，`status=1`）。
5. 为调用者写入一条 `status=1` 的 `user_api_permission`（本地可走管理端审批或直接 SQL）。
6. 前端调试台可选：在 `frontend/src/utils/requestSample.ts` 增加兜底示例，或在目录行维护 `request_schema`。
7. 补单元测试 / 集成测试。

> 启动时网关会扫描所有 `AbstractApiHandler` Bean：路径重复、路径不以 `/api/v1/` 开头都会**直接导致启动失败**。

### 6.3 必读安全规则

- **失败不扣费**：不要在 `doExecute` 里捕获异常后仍正常返回；抛出 `BusinessException` 或任意异常，模板会清除扣费标记。
- **不要自己扣费**：新接口禁止调用 `QuotaService.consume`，计费统一由 `afterCompletion` 完成。
- **不要暴露上游字段**：第三方响应必须清洗为标准 VO。
- **敏感配置不硬编码**：上游密钥从 `application.properties` / 环境变量读取。
- **分页必须安全**：使用 `getSafePageNum()` / `getSafePageSize()`，不要直接 `new Page<>(request.getPageNum(), ...)`。
- **SQL 只读**：`AbstractSqlApiHandler` 已加只读事务，需要写操作的接口不应放在开放轨道。

---

## 7. 新增 SQL 查询接口

### 7.1 适用场景

查询平台 MySQL 数据，例如个人调用日志、订单流水、点数明细、业务报表等。

### 7.2 代码结构

```text
engine/handlers/point/
├── MyPointRecordsApiHandler.java
├── PointRecordPageReq.java
└── PointRecordOpenVO.java
```

### 7.3 示例：分页查询当前用户的点数流水

> 下例中的 `PointRecord`、`PointRecordMapper` 是按现有命名规范假想的实体与 Mapper，实际接入时替换为项目中已存在的 Entity / Mapper 即可。

**请求 DTO**（继承 `BasePageRequest`）

```java
package com.example.api_service.engine.handlers.point;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(name = "PointRecordPageReq", description = "点数流水分页查询参数")
public class PointRecordPageReq extends BasePageRequest {

    /** 1-充值，2-消费；null 表示不筛选 */
    @Schema(description = "流水类型：1-充值，2-消费", example = "2")
    @Min(value = 1, message = "recordType 只能为 1 或 2")
    @Max(value = 2, message = "recordType 只能为 1 或 2")
    private Integer recordType;

    public Integer getRecordType() {
        return recordType;
    }

    public void setRecordType(Integer recordType) {
        this.recordType = recordType;
    }
}
```

**响应 VO**

```java
package com.example.api_service.engine.handlers.point;

import com.example.api_service.model.entity.PointRecord;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PointRecordOpenVO", description = "点数流水")
public record PointRecordOpenVO(
        @Schema(description = "流水 ID") String id,
        @Schema(description = "类型：1-充值，2-消费") Integer recordType,
        @Schema(description = "变动点数，消耗为负数") Integer changePoints,
        @Schema(description = "变动后余额") Long balanceAfter,
        @Schema(description = "创建时间") String createTime
) {

    public static PointRecordOpenVO from(PointRecord entity) {
        return new PointRecordOpenVO(
                String.valueOf(entity.getId()),   // 雪花 ID 以字符串下发，避免 JS 精度丢失
                entity.getRecordType(),
                entity.getChangePoints(),
                entity.getBalanceAfter(),
                entity.getCreateTime() == null ? null : entity.getCreateTime().toString()
        );
    }
}
```

**Handler**

```java
package com.example.api_service.engine.handlers.point;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.PageResult;
import com.example.api_service.engine.AbstractSqlApiHandler;
import com.example.api_service.engine.context.ApiContext;
import com.example.api_service.mapper.PointRecordMapper;
import com.example.api_service.model.entity.PointRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SQL 分页查询示例：查询当前用户的点数流水。
 * 请求继承 BasePageRequest，模板会按 pageSize 自动设置计费倍率（每 20 条一个单元）。
 */
@Component
@RequiredArgsConstructor
public class MyPointRecordsApiHandler
        extends AbstractSqlApiHandler<PointRecordPageReq, PageResult<PointRecordOpenVO>> {

    private final PointRecordMapper pointRecordMapper;

    @Override
    public String getEndpointPath() {
        return "/api/v1/data/my-points";
    }

    @Override
    protected PageResult<PointRecordOpenVO> doExecute(PointRecordPageReq request) {
        Long userId = ApiContext.getRequired().getUserId();

        Page<PointRecord> page = buildMpPage(request);
        Page<PointRecord> result = pointRecordMapper.selectPage(page,
                Wrappers.<PointRecord>lambdaQuery()
                        .eq(PointRecord::getUserId, userId)
                        .eq(request.getRecordType() != null,
                                PointRecord::getRecordType, request.getRecordType())
                        .orderByDesc(PointRecord::getCreateTime));

        return toPageResult(result, PointRecordOpenVO::from);
    }
}
```

**非分页查询**只需把响应泛型改为 `VO`，`doExecute` 直接返回对象：

```java
@Component
@RequiredArgsConstructor
public class PointBalanceApiHandler extends AbstractSqlApiHandler<PointBalanceReq, PointBalanceVO> {

    private final SysUserMapper sysUserMapper;

    @Override
    public String getEndpointPath() {
        return "/api/v1/data/balance";
    }

    @Override
    protected PointBalanceVO doExecute(PointBalanceReq request) {
        Long userId = ApiContext.getRequired().getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        return PointBalanceVO.from(user);
    }
}
```

### 7.4 复杂 SQL

聚合、联表、排行等查询放在 `src/main/resources/mapper/XxxMapper.xml`，Mapper 接口声明方法并用 `@Param` 绑定。参考 `ApiAccessLogMapper.xml`：

```xml
<select id="selectDailyStats"
        resultType="com.example.api_service.model.dto.log.LogDailyStatRow">
    SELECT CAST(create_time AS DATE) AS stat_date,
           COUNT(*)                  AS total_calls
    FROM api_access_log
    WHERE user_id = #{userId}
      AND create_time >= #{startTime}
      AND create_time < #{endTime}
    GROUP BY CAST(create_time AS DATE)
</select>
```

编写要求：

- WHERE 条件必须能命中索引；时间范围统一左闭右开 `>= start AND < end`。
- 不要在 XML 中拼接 `${}`，一律使用 `#{}` 预编译参数。
- 排序字段放在索引覆盖范围内，避免全表排序。

### 7.5 登记接口目录

```sql
INSERT INTO `api_endpoint_meta`
    (`id`, `name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    (1006, '我的点数流水', '/api/v1/data/my-points', 'POST', 1,
     '按类型分页查询当前用户的点数流水，每 20 条一个计费单元',
     '{"pageNum":1,"pageSize":20,"recordType":2}', 1);
```

### 7.6 调用

```bash
curl -X POST 'http://localhost:8080/api/v1/data/my-points' \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: sk-your-api-key' \
  -d '{"pageNum":1,"pageSize":20,"recordType":2}'
```

---

## 8. 新增 ES 检索接口

### 8.1 适用场景

全文检索、关键词匹配、日志检索、新闻/商品搜索等。

### 8.2 代码结构

```text
engine/handlers/article/
├── ArticleSearchApiHandler.java
├── ArticleSearchReq.java
├── ArticleEsDoc.java
└── ArticleOpenVO.java
```

### 8.3 示例：新闻/文章关键词检索

> 下例中的 `Article*` 类为新建示例；索引名通过 `app.search.article-index` 配置。ES 模板的返回类型固定为 `PageResult<VO>`，因此它只适合检索类接口。

**请求 DTO**（必须继承 `BasePageRequest`）

```java
package com.example.api_service.engine.handlers.article;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "ArticleSearchReq", description = "文章检索参数")
public class ArticleSearchReq extends BasePageRequest {

    @Schema(description = "关键词，匹配标题与正文；为空时返回最新文章", example = "人工智能")
    @Size(max = 100, message = "keyword 长度不能超过 100")
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
```

**ES 文档类**

```java
package com.example.api_service.engine.handlers.article;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "ArticleEsDoc", description = "ES 文章文档，字段需与 _source 同名")
public class ArticleEsDoc {

    @Schema(description = "文档 ID")
    private String id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "来源")
    private String source;

    /** 用别名兼容下划线命名 */
    @JsonAlias("publish_time")
    @Schema(description = "发布时间")
    private String publishTime;
}
```

**响应 VO**

```java
package com.example.api_service.engine.handlers.article;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ArticleOpenVO", description = "文章检索结果")
public record ArticleOpenVO(
        @Schema(description = "文档 ID") String id,
        @Schema(description = "标题") String title,
        @Schema(description = "正文") String content,
        @Schema(description = "来源") String source,
        @Schema(description = "发布时间") String publishTime
) {

    public static ArticleOpenVO from(ArticleEsDoc doc) {
        return new ArticleOpenVO(doc.getId(), doc.getTitle(), doc.getContent(),
                doc.getSource(), doc.getPublishTime());
    }
}
```

**Handler**

```java
package com.example.api_service.engine.handlers.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.api_service.engine.AbstractEsApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ES 检索示例：关键词全文检索。
 * 分页窗口、trackTotalHits、异常转换与 PageResult 组装均由基类完成。
 */
@Component
public class ArticleSearchApiHandler
        extends AbstractEsApiHandler<ArticleSearchReq, ArticleEsDoc, ArticleOpenVO> {

    private final String indexName;

    public ArticleSearchApiHandler(
            ElasticsearchClient elasticsearchClient,
            @Value("${app.search.article-index:article}") String indexName) {
        super(elasticsearchClient);   // 必须显式调用，注入 ES 客户端
        this.indexName = indexName;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/search/article";
    }

    @Override
    protected String getIndexName() {
        return indexName;
    }

    @Override
    protected Class<ArticleEsDoc> getDocumentClass() {
        return ArticleEsDoc.class;
    }

    @Override
    protected Query buildQuery(ArticleSearchReq request) {
        String keyword = request.getKeyword();
        if (!StringUtils.hasText(keyword)) {
            return null;   // null = match_all
        }
        return Query.of(query -> query.bool(bool -> bool
                .must(must -> must.multiMatch(multiMatch -> multiMatch
                        .query(keyword.trim())
                        .fields("title^3", "content")))
                .filter(filter -> filter.term(term -> term
                        .field("status")
                        .value(1)))));
    }

    @Override
    protected ArticleOpenVO convertDocToResp(ArticleEsDoc document) {
        return ArticleOpenVO.from(document);
    }
}
```

### 8.4 索引与配置

`application.properties`：

```properties
app.elasticsearch.url=${ELASTICSEARCH_URL:http://localhost:9200}
app.elasticsearch.username=${ELASTICSEARCH_USERNAME:}
app.elasticsearch.password=${ELASTICSEARCH_PASSWORD:}
app.search.article-index=${ARTICLE_INDEX:article}
```

索引需提前创建，字段名与 `ArticleEsDoc` 的 `_source` 对应。例如：

```bash
curl -X PUT 'http://localhost:9200/article' -H 'Content-Type: application/json' -d '
{
  "mappings": {
    "properties": {
      "id":           { "type": "keyword" },
      "title":        { "type": "text" },
      "content":      { "type": "text" },
      "source":       { "type": "keyword" },
      "status":       { "type": "integer" },
      "publish_time": { "type": "date" }
    }
  }
}'
```

### 8.5 分页边界

- `from + size > 10000` 时基类直接返回 `40000`，提示改用 `nextCursor`。
- `pageSize` 上限 100，超过会被 `getSafePageSize()` 截断。
- 深分页应使用 `search_after` + `nextCursor`；当前基类已预留 `nextCursor` 字段，但 `AbstractEsApiHandler` 尚未实现 `search_after`，需要时应在基类中扩展而不是在子类各自实现。

### 8.6 登记接口目录

```sql
INSERT INTO `api_endpoint_meta`
    (`id`, `name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    (1007, '文章搜索', '/api/v1/search/article', 'POST', 1,
     '基于 Elasticsearch 的文章标题与正文检索',
     '{"keyword":"人工智能","pageNum":1,"pageSize":20}', 1);
```

---

## 9. 新增第三方 API 调用接口

### 9.1 适用场景

平台代调用第三方 HTTP 服务，并把上游响应清洗为平台标准结构（如 IP 归属地、天气、汇率、短信）。

### 9.2 代码结构

```text
engine/handlers/weather/
├── WeatherQueryApiHandler.java
├── WeatherQueryReq.java
└── WeatherVO.java
```

### 9.3 示例：天气查询

> 下例中的 `Weather*` 类为新建示例，上游地址与密钥均通过配置注入。

**请求 DTO**（非分页接口不继承 `BasePageRequest`，计费倍率固定 1）

```java
package com.example.api_service.engine.handlers.weather;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "WeatherQueryReq", description = "天气查询请求")
public class WeatherQueryReq {

    @Schema(description = "城市名称", example = "上海")
    @NotBlank(message = "city 不能为空")
    @Size(max = 64, message = "city 长度不能超过 64")
    private String city;

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
```

**响应 VO**

```java
package com.example.api_service.engine.handlers.weather;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WeatherVO", description = "清洗后的天气结果")
public record WeatherVO(
        @Schema(description = "城市") String city,
        @Schema(description = "天气") String condition,
        @Schema(description = "温度（摄氏度）") Double temperature,
        @Schema(description = "湿度（百分比）") Integer humidity,
        @Schema(description = "观测时间") String observedAt
) {
}
```

**Handler**

```java
package com.example.api_service.engine.handlers.weather;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.AbstractHttpProxyApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * 第三方 HTTP 转发示例：天气查询。
 * 超时、非 2xx、空响应由基类统一转为 50400。
 */
@Component
public class WeatherQueryApiHandler
        extends AbstractHttpProxyApiHandler<WeatherQueryReq, WeatherVO> {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public WeatherQueryApiHandler(
            ObjectMapper objectMapper,   // Boot 4 下是 tools.jackson.databind.ObjectMapper
            @Value("${app.proxy.weather.base-url:https://api.example.com}") String baseUrl,
            @Value("${app.proxy.weather.api-key:}") String apiKey) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/tools/weather";
    }

    @Override
    protected String buildTargetUrl(WeatherQueryReq request) {
        return baseUrl + "/weather?city="
                + UriUtils.encodeQueryParam(request.getCity().trim(), StandardCharsets.UTF_8);
    }

    @Override
    protected Map<String, String> buildHeaders() {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "天气服务尚未配置访问密钥");
        }
        return Map.of(
                "Accept", "application/json",
                "X-Api-Key", apiKey
        );
    }

    /** 只读查询用 GET；默认是 POST。 */
    @Override
    protected HttpMethod resolveHttpMethod(WeatherQueryReq request) {
        return HttpMethod.GET;
    }

    @Override
    protected WeatherVO transformResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回格式不正确");
            }
            if (root.hasNonNull("error")) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                        "天气查询失败: " + root.get("error").asString());
            }
            return new WeatherVO(
                    text(root, "city"),
                    text(root, "condition"),
                    number(root, "temperature"),
                    integer(root, "humidity"),
                    text(root, "observed_at")
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回格式无法解析", ex);
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asString();
    }

    private Double number(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    private Integer integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.asInt() : null;
    }
}
```

### 9.4 配置

```properties
# 上游地址与密钥一律通过环境变量覆盖，禁止写死在代码中
app.proxy.weather.base-url=${WEATHER_BASE_URL:https://api.example.com}
app.proxy.weather.api-key=${WEATHER_API_KEY:}
```

### 9.5 关键约束

- 默认连接超时 2s、读取超时 5s；确有长耗时上游时，通过第二个构造器注入自定义 `RestClient`，不要修改全局默认值。
- 上游 `4xx/5xx`、连接失败、超时、空响应全部转为 `50400`，并保证不扣费。
- `transformResponse` 必须输出平台 VO，禁止把第三方 `rawJson` 原样返回。
- 上游密钥缺失时应在 `buildHeaders()` 直接抛 `BusinessException`，不要发出无密钥请求。
- POST 上游默认发送整个请求 DTO；需要改写请求体时覆盖 `buildRequestBody()`。
- 需要鉴权 Header 的 GET 场景同样通过 `buildHeaders()` 注入。

### 9.6 登记接口目录

```sql
INSERT INTO `api_endpoint_meta`
    (`id`, `name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    (1008, '天气查询', '/api/v1/tools/weather', 'POST', 1,
     '代理第三方天气服务并清洗为标准结果',
     '{"city":"上海"}', 1);
```

> 注意：`api_endpoint_meta.method` 记录的是**平台对外暴露的调用方式**，统一为 `POST`；与 Handler 内部调用上游使用的 GET/POST 无关。

---

## 10. 新增开放接口检查清单

- [ ] Handler 位于 `engine/handlers/<domain>/`，带 `@Component`
- [ ] 只继承一个数据源基类，实现了全部抽象方法
- [ ] `getEndpointPath()` 与目录 `path` 完全一致，且以 `/api/v1/` 开头
- [ ] 请求 DTO 的分页字段使用 `BasePageRequest`；非分页接口不继承
- [ ] 参数校验注解齐全（`@NotBlank`、`@Size`、`@Min/@Max` 等）
- [ ] 响应使用 VO，不直接返回 Entity / `IPage` / ES 原始响应
- [ ] 第三方密钥来自配置，无硬编码
- [ ] 异常路径不会扣费（不捕获后正常返回）
- [ ] `docs/schema.sql` 已登记目录行：`method='POST'`、`status=1`、`cost_per_call` 合理
- [ ] 调用者的 `user_api_permission.status = 1`
- [ ] 前端调试台示例：`request_schema` 或 `src/utils/requestSample.ts` 兜底
- [ ] 测试：H2 环境下新增目录行时同步调整受影响的断言
- [ ] `./mvnw test` 与 `npm run type-check` 通过

---

## 11. 控制台接口开发规范

控制台接口用于前端控制台，不经过开放网关。

### 11.1 Controller

```java
@Validated
@RestController
@RequestMapping("/api/console/example")
@RequiredArgsConstructor
@Tag(name = "Console - Example", description = "示例模块")
public class ExampleController {

    private final ExampleService exampleService;

    @GetMapping
    @Operation(summary = "分页查询")
    public Result<PageResult<ExampleVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.success(exampleService.list(UserContext.getUserId(), page, size));
    }

    @PostMapping
    @Operation(summary = "新建")
    public Result<ExampleVO> create(@Valid @RequestBody ExampleCreateRequest request) {
        return Result.success(exampleService.create(UserContext.getUserId(), request));
    }
}
```

要求：

- 类上 `@RestController`、`@RequestMapping("/api/console/...")`、`@Validated`、`@RequiredArgsConstructor`
- 入参 `@Valid @RequestBody` 或带约束注解的 `@RequestParam`
- 成功统一 `Result.success(...)`；无返回值用 `Result.success()`
- 列表返回 `Result<PageResult<VO>>`
- 管理员接口放在 `/api/console/admin/**`，类或方法加 `@RequireAdmin`
- 业务错误抛 `BusinessException(ErrorCode.XXX, "可展示的信息")`，由 `GlobalExceptionHandler` 统一转换
- 当前用户通过 `UserContext.getUserId()`，禁止从请求体读取 `userId`
- Entity 永不直接作为出参

### 11.2 分页约定

控制台列表存在两种入参风格，跟随所在模块即可：

- Query 风格：`GET ?page=1&size=10`（如 `/api/console/apikeys`）
- Body 风格：`POST` 请求体中 `pageNum/pageSize`（如 `/api/console/recharge/orders`、`/api/console/admin/logs/page`）

响应始终是 `PageResult` 的 6 字段结构。

### 11.3 前端配套

**接口模块** `src/api/example.ts`：

```ts
import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

export interface ExampleItem {
  id: string                 // 雪花 ID 一律用 string
  name: string
  createTime: string
}

export interface ExampleListQuery {
  page: number
  size: number
}

export function listExamples(query: ExampleListQuery): Promise<PageResult<ExampleItem>> {
  return consoleHttp.get<PageResult<ExampleItem>>('/example', { params: query })
}

export function createExample(payload: { name: string }): Promise<ExampleItem> {
  return consoleHttp.post<ExampleItem>('/example', payload)
}
```

**页面** `src/views/console/example/Index.vue`：

- 使用 `<script setup lang="ts">` + Composition API
- 必须 `defineOptions({ name: 'ExampleIndex' })`，否则 `vue/multi-word-component-names` 报错
- 列表优先使用 `src/composables/usePagination.ts`
- UI 只使用 Element Plus + Tailwind，不引入新的组件库
- 禁止 `any`；接口返回类型必须显式声明
- 雪花 ID 以字符串处理，避免 JS 精度丢失

**路由** `src/router/index.ts`：

```ts
{
  path: 'example',
  name: 'example',
  component: () => import('@/views/console/example/Index.vue'),
  meta: { requiresAuth: true, title: '示例页面' },
}
```

管理员页面额外加 `requiresAdmin: true`。

---

## 12. 本地联调与测试注意事项

### 12.1 JDK

必须使用 JDK 21。默认 JDK 27 会让 Lombok 在编译期崩溃：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

修改代码后必须重启后端，旧进程会继续提供旧字节码。

### 12.2 数据库

本地 MariaDB 的 root 与操作系统用户无法通过 TCP 登录，使用专用账号：

```bash
export DB_USERNAME=api_service
export DB_PASSWORD=api_service_pass
```

若出现 `Failed to obtain JDBC Connection` / `Access denied for user`，先检查数据库账号，不要先怀疑拦截器。

### 12.3 测试数据与断言

- 测试使用 `src/test/resources/schema-h2.sql`。
- `PermissionApiIntegrationTest` 断言接口市场只返回 **2** 个接口；向 H2 增加目录行会破坏该断言，需要同步修改测试预期。
- `docs/schema.sql` 中的 1003–1005（个人日志、新闻搜索、IP 归属地）只存在于真实数据库脚本，不在 H2 中。
- Guava `RateLimiter` 只保留约 1 秒的令牌存量，连续 MockMvc 调用低 QPS 的 Key 会返回 429；多调用测试请使用高 `rateLimitQps`。

### 12.4 ES

- 构建客户端不会立即建立连接，ES 未启动时应用仍可启动。
- 真正调用检索接口且 ES 不可用时会返回 `50400`，属于预期行为。

### 12.5 前端

- `frontend/.gitignore` 中的 `logs` 规则必须写成 `/logs`，否则会忽略 `src/views/console/admin/logs/`，导致该目录的 Tailwind 类不生成。
- 测试登录页前先清除 `localStorage` 中的 `api_console_access_token`，否则 `guestOnly` 守卫会重定向。
- 调试台只展示 `applyStatus === 1 && endpointStatus === 1` 的接口，新增接口必须先审批通过。

### 12.6 已知约束

- 新网关结算只更新 `sys_user.balance_quota` 并写入 `api_access_log`，**不更新** `api_key_info.used_quota` / `last_used_time`。
- `/api/v1/services/echo`、`/text-stats` 是过渡期遗留的手写 Controller，仍走旧拦截器与 `QuotaService`；新接口不要再仿照。
- 优惠折扣计算必须使用整数百分位，避免 `Math.ceil(10 * 0.8)` 这类浮点误差。

---

## 13. 常见问题速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 编译报 Lombok `EndPosTable` | 使用了 JDK 27 | 切换 JDK 21 |
| `40105` 缺少令牌 | 后端仍是旧进程，或未携带 `Authorization` | 重启后端，检查前端 token |
| `40404` 接口不存在 | `api_endpoint_meta` 缺少 `path + POST` 行或 `status != 1` | 补目录行 |
| `40304` 无接口权限 | 缺少 `status=1` 的 `user_api_permission` | 提交申请并审批 |
| `40500` 方法不允许 | 对 `/api/v1/**` 使用了 GET | 改为 POST + JSON |
| `40201` 点数不足 | 余额低于本次计费 | 充值或调整 pageSize |
| `50400` | ES 未启动 / 第三方异常 / 超时 | 检查上游与网络 |
| 启动失败“重复的开放接口路径” | 两个 Handler 返回了同一路径 | 检查 `getEndpointPath()` |
| 启动失败“路径必须以 /api/v1/ 开头” | Handler 路径不合法 | 修正路径 |
| 前端 Tailwind 类不生效 | 目录被 `.gitignore` 忽略 | 检查 `.gitignore` |
| 500 且日志 `Access denied for user` | 数据库账号错误 | 使用 `api_service` 账号 |

---

## 14. 参考实现

直接对照现有代码可以最快理解模板用法：

| 类型 | 文件 |
|---|---|
| SQL 分页 | `backend/src/main/java/com/example/api_service/engine/handlers/log/AccessLogExportApiHandler.java` |
| ES 检索 | `backend/src/main/java/com/example/api_service/engine/handlers/news/PublicNewsSearchApiHandler.java` |
| 第三方转发 | `backend/src/main/java/com/example/api_service/engine/handlers/geo/IpGeoLocateApiHandler.java` |
| 动态网关 | `backend/src/main/java/com/example/api_service/controller/gateway/OpenApiGatewayController.java` |
| 计费拦截器 | `backend/src/main/java/com/example/api_service/config/interceptor/ApiKeyAuthInterceptor.java` |
| 公共契约 | `backend/src/main/java/com/example/api_service/common/` |
| 目录与索引 | `docs/schema.sql` |
| 前端请求封装 | `frontend/src/utils/request.ts` |
| 前端分页组合式函数 | `frontend/src/composables/usePagination.ts` |
