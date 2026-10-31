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
│   ├── schema.sql            # 全量建表脚本与示例接口目录
│   └── generated_sql/
│       └── init_endpoints.sql # 接口生成器追加的初始化 SQL（status 默认 0）
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

`docs/schema.sql` 包含 9 张表：

| 表 | 用途 |
|---|---|
| `sys_user` | 开发者/管理员账号、余额点数、封禁状态 |
| `password_reset_token` | 找回密码令牌（只存 SHA-256 哈希，一次性使用） |
| `api_key_info` | API Key（仅存 SHA-256 与脱敏前缀） |
| `api_endpoint_meta` | 对外接口目录（路径、方法、单价、请求/响应示例、上下线；`status` 默认 `0`，上线后为 `1`） |
| `user_api_permission` | 用户对接口的申请、审批状态与专属折扣 |
| `api_access_log` | 调用流水（状态码、耗时、实际扣点） |
| `recharge_order` | 点数充值订单（用户支付 + 管理员发放共用流水，`source` 区分） |
| `admin_recharge_monthly_usage` | 管理员月度充值限额计数（按 管理员 + 目标账号 + 自然月 唯一） |
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
- Redis（验证码、QPS 与控制台固定窗口限流）、Hutool、Lombok、spring-security-crypto（BCrypt）

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

### 3.5 本地开发需修改的参数

配置优先级为 **环境变量 > `application.properties` / `application.yml`**。本地调试建议用环境变量覆盖，不要把自己的数据库密码、SMTP 口令提交到仓库。

#### 后端参数

| 参数（环境变量） | 默认值 | 本地建议 | 说明 |
|---|---|---|---|
| `JAVA_HOME` | 系统 JDK | Temurin 21 | 必须 JDK 21；JDK 27 会导致 Lombok 编译崩溃 |
| `DB_URL` | `jdbc:mysql://localhost:3306/open_api_platform?...` | 保持默认；改端口或库名时修改 | 必须已执行 `docs/schema.sql` |
| `DB_USERNAME` | `root` | `api_service` | 本地 MariaDB 的 root / OS 用户无法通过 TCP 登录，需专用账号 |
| `DB_PASSWORD` | 空 | `api_service_pass` | 与 `DB_USERNAME` 对应 |
| `JWT_SECRET` | 开发用默认串 | 本地可保持默认 | HS256 密钥，**至少 32 字节**；生产必须替换 |
| `JWT_EXPIRATION_MS` | `86400000`（24 小时） | 按需 | 控制台 Token 有效期 |
| `REDIS_HOST` | `localhost` | 默认即可 | 注册验证码存储；未启动时注册会失败 |
| `REDIS_PORT` | `6379` | 默认即可 | |
| `REDIS_PASSWORD` | 空 | 本地 Redis 有密码时填写 | |
| `REDIS_DATABASE` | `0` | 按需 | |
| `MAIL_HOST` | `smtp.example.com` | 填真实 SMTP 地址 | **仅发送注册验证码时需要**；假值会发信失败 |
| `MAIL_PORT` | `465` | 按需 | 默认按 SSL SMTP 配置 |
| `MAIL_USERNAME` | `no-reply@example.com` | 真实发件账号 | |
| `MAIL_PASSWORD` | `change-me` | SMTP 密码 / 授权码 | |
| `PASSWORD_RESET_LINK_BASE_URL` | `http://localhost:5173/reset-password` | 本地默认即可 | 找回密码邮件中的重置链接前缀；**生产必须改为公网前端地址**，prod 启动校验会拒绝 localhost |
| `PASSWORD_RESET_TOKEN_TTL_MINUTES` | `30` | 按需 | 重置链接有效期（分钟） |
| `PASSWORD_RESET_TICKET_TTL_MINUTES` | `10` | 按需 | 令牌校验通过后一次性票据有效期（分钟） |
| `PASSWORD_RESET_RESEND_COOLING_SECONDS` | `60` | 按需 | 同一账号两次重置邮件的最小间隔（秒），0 表示不限制 |
| `RATELIMIT_PASSWORD_CHANGE_MAX_FAILURES` | `5` | 按需 | 修改密码时旧密码允许连续输错的次数 |
| `RATELIMIT_PASSWORD_CHANGE_WINDOW_MINUTES` | `15` | 按需 | 旧密码错误计数的统计窗口（分钟） |
| `RATELIMIT_PASSWORD_CHANGE_LOCK_MINUTES` | `30` | 按需 | 达到错误上限后修改密码功能的锁定时间（分钟） |
| `RATELIMIT_PASSWORD_CHANGE_IP_PER_15_MINUTES` | `10` | 按需 | 修改密码接口每 IP 每 15 分钟的上限 |
| `ELASTICSEARCH_URL` | `http://localhost:9200` | 默认即可 | 新闻检索使用；未启动不影响应用启动，调用时返回 `50400` |
| `ELASTICSEARCH_USERNAME` | 空 | 本地 ES 开启认证时填写 | |
| `ELASTICSEARCH_PASSWORD` | 空 | 同上 | |
| `NEWS_INDEX` | `news` | 与本地索引名一致 | 新闻检索索引名 |
| `IP_GEO_BASE_URL` | `https://ipapi.co` | 默认即可（无需密钥） | IP 归属地上游地址 |
| `RECHARGE_QR_BASE_URL` | `https://pay.example.com/qr` | 本地保持默认即可 | 充值二维码地址前缀 |
| `RECHARGE_ORDER_TTL_MINUTES` | `15` | 按需 | 充值订单有效期 |
| `RECHARGE_MOCK_PAY_ENABLED` | `false` | 本地联调充值流程时设为 `true`，生产必须 `false` | 是否启用模拟支付 |
| `RECHARGE_ADMIN_MONTHLY_LIMIT_PER_USER` | `5000` | 按需 | 同一管理员对同一账号每自然月累计充值上限（点），`0` 表示禁用管理员充值 |
| `SERVER_PORT` | `8080` | 前端代理需同步修改 | 后端端口 |
| `app.cors.allowed-origin-patterns` | `http://localhost:5173,http://127.0.0.1:5173` | 前端换端口时修改 | 跨域白名单；需直接改 `application.properties` |

本地数据库账号（与上面的 `DB_USERNAME` / `DB_PASSWORD` 对应）：

```bash
mysql -uroot -p < docs/schema.sql

mysql -uroot -p <<'SQL'
CREATE USER IF NOT EXISTS 'api_service'@'localhost' IDENTIFIED BY 'api_service_pass';
CREATE USER IF NOT EXISTS 'api_service'@'127.0.0.1' IDENTIFIED BY 'api_service_pass';
GRANT ALL PRIVILEGES ON open_api_platform.* TO 'api_service'@'localhost';
GRANT ALL PRIVILEGES ON open_api_platform.* TO 'api_service'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
```

注册用户默认为 `USER` 角色、初始点数 1000。要测试管理端审批和后台页面，需要手工提升为管理员：

```sql
UPDATE sys_user SET role = 'ADMIN' WHERE username = '你的账号';
```

#### 前端参数

| 参数 | 位置 / 默认值 | 本地建议 | 说明 |
|---|---|---|---|
| `VITE_API_BASE_URL` | 由 `import.meta.env` 读取，默认 `/api` | **开发环境不要设置** | 设置后走直连；使用 Vite 代理时保持默认，只有前端直连后端时才设为完整地址，如 `http://localhost:8080/api` |
| `server.port` | `vite.config.ts`，`5173` | 与后端 CORS 白名单保持一致 | 前端端口一旦修改，需同步更新 `app.cors.allowed-origin-patterns` |
| `server.proxy./api.target` | `vite.config.ts`，`http://localhost:8080` | 后端换端口时同步修改 | 开发环境 `/api` 代理目标 |
| Node.js | — | 22.18+ / 24.12+ | Vite 8 的最低版本要求 |

前端本地覆盖变量写入 `frontend/.env.local`（已被 `.gitignore` 的 `*.local` 忽略）：

```bash
# 仅在前端需要直连其他后端地址时使用；使用 Vite 代理时不要创建该变量
VITE_API_BASE_URL=http://localhost:8080/api
```

#### 参数修改注意事项

- 后端默认值统一在 `backend/src/main/resources/application.properties` 与 `application.yml`；后端**不会读取 `.env` 文件**，本地覆盖请用 `export`、IDE Run Configuration 或 Maven 启动参数。
- 只改后端端口时，需要同时改 `SERVER_PORT`、`vite.config.ts` 的代理目标和 `app.cors.allowed-origin-patterns` 三处。
- `./mvnw test` 使用 H2，不需要配置 `DB_*`；测试环境的 JWT 密钥在 `src/test/resources/application.properties` 中单独维护。
- 邮件与 Redis 只影响注册发码；不调试注册流程时可以暂时忽略，但用假 SMTP 发码一定失败。
- ES 相关参数只影响新闻检索接口；ES 未启动时应用仍可启动，调用检索接口返回 `50400` 属于预期行为。

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
- `POST /api/console/auth/forgot-password`
- `POST /api/console/auth/reset-password/verify`
- `POST /api/console/auth/reset-password`

解析成功后写入 `UserContext`（ThreadLocal），业务代码通过 `UserContext.getUserId()` 取当前用户。`AdminAuthInterceptor` 在其后执行，只拦截标注了 `@RequireAdmin` 的类或方法。修改密码 `POST /api/console/auth/change-password` 需要登录，用户身份只从 `UserContext` 获取。

### 4.2 开放网关流水线

`config/interceptor/ApiKeyAuthInterceptor`（Bean 名 `gatewayApiKeyAuthInterceptor`）对 `/api/v1/**` 依次执行：

1. 非 `POST` → `40500`（HTTP 405）
2. 提取 `X-API-Key`（格式 `sk-` + 48 位随机串，数据库只存 SHA-256 与脱敏前缀），SHA-256 后查 `api_key_info`，校验启用/过期
3. 校验用户存在且未封禁
4. 按 `rate_limit_qps` 做 Redis 固定窗口限流（跨实例共享）→ 超限 `42900`（HTTP 429）
5. 按路径 + `POST` 匹配 `api_endpoint_meta`，未登记 → `40404`；`status = 0`（未发布/已下线）→ `40305`「API 已下线」
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
| `403xx` | 无权限 | `40300` 禁止访问、`40301` 账号封禁、`40304` 无接口权限、`40305` API 已下线、`40306` 管理员月度充值额度超限 |
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
4. 在 `docs/schema.sql`（或生成器追加的 `docs/generated_sql/init_endpoints.sql`）中登记目录行：`method` 必须为 `POST`，新接口的 `status` 用 `0`，上线后改为 `1`。
5. 为调用者写入一条 `status=1` 的 `user_api_permission`（本地可走管理端审批或直接 SQL）。
6. 前端调试台可选：在 `frontend/src/utils/requestSample.ts` 增加兜底示例，或在目录行维护 `request_schema`。
7. 补单元测试 / 集成测试。

> 以上步骤可改用第 6.4 节的「接口生成器」自动完成 1-4 步（含目录登记），再手工补业务细节。
>
> 启动时网关会扫描所有 `AbstractApiHandler` Bean：路径重复、路径不以 `/api/v1/` 开头都会**直接导致启动失败**。

### 6.3 必读安全规则

- **失败不扣费**：不要在 `doExecute` 里捕获异常后仍正常返回；抛出 `BusinessException` 或任意异常，模板会清除扣费标记。
- **不要自己扣费**：新接口禁止调用 `QuotaService.consume`，计费统一由 `afterCompletion` 完成。
- **不要暴露上游字段**：第三方响应必须清洗为标准 VO。
- **敏感配置不硬编码**：上游密钥从 `application.properties` / 环境变量读取。
- **分页必须安全**：使用 `getSafePageNum()` / `getSafePageSize()`，不要直接 `new Page<>(request.getPageNum(), ...)`。
- **SQL 只读**：`AbstractSqlApiHandler` 已加只读事务，需要写操作的接口不应放在开放轨道。

### 6.4 接口生成器（可视化脚手架）

管理端「接口生成器」（前端路由 `/console/admin/generator`）把 6.2 的前 4 步变成表单操作：配置契约 → 定义入参/出参 → 实时预览 → 一键落盘，同时生成初始化 SQL 并自动登记 `api_endpoint_meta`。仅 ADMIN 角色可用，后端实现是 `AdminCodeGenController` + `ApiCodeGenServiceImpl`。

#### 6.4.1 两种模式

| 模式 | 用途 | 行为 |
|---|---|---|
| 创建新接口 | 从零生成一套 Handler / DTO / VO | 新建文件；目标文件已存在时直接拒绝，**绝不覆盖** |
| 修改已有接口 | 调整已生成接口的入参、出参与 SQL | 只覆写 Req、VO（ES 另含 EsDoc），并仅替换 Handler 中的 SQL 锚点区 |

修改模式的下拉会列出 `api_endpoint_meta` 中**全部**接口（包括未发布，带「未发布」标签）。选中后按 `path` 读取 `engine_config` 回显；老接口没有 `engine_config` 时，会从 Handler 源码逆向推导 SQL、数据源、索引与字段。

#### 6.4.2 操作步骤

1. 选择模式：创建新接口 / 修改已有接口（修改模式先选目标接口）。
2. 「基础契约配置」：接口中文名称、`domain` 包名、单价、对外路径、引擎类型及引擎专属配置。
3. 「请求入参定义 (DTO)」：用「添加参数」逐条维护，或用「从 JSON 导入」粘贴示例 JSON 自动生成字段。
4. 「响应出参定义 (VO)」：用「添加字段」维护，或用「从 SQL 推导」按 SELECT 列生成出参。
5. 右侧预览区随表单实时刷新（约 400ms 防抖），可查看 Req / VO / EsDoc / Handler / 初始化 SQL 五份产物，并支持单份复制。
6. 点击「写入本地项目工程」（修改模式为「原地更新接口代码」），确认后落盘。
7. 重新编译并重启后端，让新 Handler 被网关扫描注册。
8. 进入「API管理 → API发布管理」把接口上线（`status: 0 → 1`）。
9. 调用方在接口市场提交申请，管理员在「API权限审批工单」通过后即可调用。

#### 6.4.3 引擎与必填项

| 引擎 | 必填 / 常用配置 | 生成的 Handler |
|---|---|---|
| `EXTERNAL_SQL` | 目标外部数据源 key、只读 SQL | `AbstractExternalSqlApiHandler`，内置分页与行映射 |
| `INTERNAL_SQL` | 只读 SQL | `AbstractSqlApiHandler`，仅生成骨架，`doExecute` 抛 `UnsupportedOperationException`，需手工注入本地 Mapper |
| `ES` | 索引名 | `AbstractEsApiHandler` + `EsDoc`，强制分页 |
| `HTTP_PROXY` | 上游 base URL、HTTP 方法、请求头 | `AbstractHttpProxyApiHandler`，响应清洗在 `transformResponse` |

校验规则：路径必须以 `/api/v1/` 开头；`domain` 只能是小写字母、数字与下划线；`EXTERNAL_SQL` 必须选择数据源。查本地或异构库优先用 `EXTERNAL_SQL`，`INTERNAL_SQL` 生成后仍需补 Mapper 代码。

#### 6.4.4 产物

以路径 `/api/v1/pg/orders` 为例，类名前缀为 `PgOrders`：

| 产物 | 位置 / 命名 |
|---|---|
| 请求 DTO | `engine/handlers/<domain>/PgOrdersPageReq.java`（分页继承 `BasePageRequest`；非分页为 `PgOrdersReq`） |
| 响应 VO | `engine/handlers/<domain>/PgOrdersOpenVO.java`（`record`） |
| ES 文档 | `engine/handlers/<domain>/PgOrdersEsDoc.java`（仅 ES） |
| Handler | `engine/handlers/<domain>/PgOrdersApiHandler.java`（`@Component`） |
| 初始化 SQL | 追加到 `docs/generated_sql/init_endpoints.sql`，`INSERT IGNORE` 且 `status = 0` |
| 目录登记 | 自动写入 `api_endpoint_meta`（含 `engine_config`，供生成器下次回显） |

#### 6.4.5 安全与注意事项

- **严格防覆盖**：创建模式以 `CREATE_NEW` 写文件，同名文件存在时直接报错，不会改动已有业务代码。
- **修改范围受限**：修改模式只覆写 Req / VO / EsDoc，并仅替换 Handler 中 `// >>>>> AUTO_SQL_TEMPLATE_START >>>>>` 与 `// <<<<< AUTO_SQL_TEMPLATE_END <<<<<` 之间的 SQL；锚点之外的自定义方法、日志、参数处理全部保留。
- **路由与包名锁定**：修改模式禁止改路径与 domain，避免与已注册的 Handler 冲突。
- **新接口默认下线**：生成的初始化 SQL 与数据库登记都是 `status = 0`，必须在「API发布管理」上线；未上线调用统一返回 `40305`「API 已下线」。
- **写盘依赖进程工作目录**：生成器会从后端进程的工作目录向上查找 `backend/src/main/java/com/example/api_service/engine/handlers`，请从仓库目录内启动后端（`cd backend && ./mvnw spring-boot:run`）。
- **必须重启**：Handler 路由在启动时一次性注册，写完代码需重新编译并重启后端，否则网关匹配不到 Handler，调用返回 `40400`。
- **生成后自检**：至少执行 `./mvnw -q -DskipTests compile` 确认可编译，再重启联调。

#### 6.4.6 生成器后端接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/console/admin/codegen/preview` | 内存渲染预览，不落盘 |
| `POST` | `/api/console/admin/codegen/write-disk` | 创建并落盘，返回目标目录绝对路径 |
| `POST` | `/api/console/admin/codegen/update` | 原地更新已有接口 |
| `GET` | `/api/console/admin/codegen/endpoints` | 已登记接口列表（含未发布） |
| `GET` | `/api/console/admin/codegen/endpoint-detail?path=...` | 读取生成规格供表单回显 |
| `GET` | `/api/console/admin/codegen/datasources` | 可用外部数据源 |

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
     '{"pageNum":1,"pageSize":20,"recordType":2}', 0);
```

> 新增接口默认 `status = 0`（未发布），需在「API管理 → API发布管理」上线后调用方才能访问；直接写 `1` 等同于立即上线。

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
     '{"keyword":"人工智能","pageNum":1,"pageSize":20}', 0);
```

> 新增接口默认 `status = 0`（未发布），需在「API管理 → API发布管理」上线后调用方才能访问；直接写 `1` 等同于立即上线。

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
     '{"city":"上海"}', 0);
```

> 新增接口默认 `status = 0`（未发布），需在「API管理 → API发布管理」上线后调用方才能访问；直接写 `1` 等同于立即上线。
>
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
- [ ] `docs/schema.sql`（或 `docs/generated_sql/init_endpoints.sql`）已登记目录行：`method='POST'`、`cost_per_call` 合理
- [ ] 目录状态已处理：新接口 `status=0`，在「API管理 → API发布管理」上线后为 `1`
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

### 11.4 管理员「API管理」模块

前端菜单「API管理」（路由 `/console/admin/api`，需 ADMIN）包含三个子功能，对应后端两组接口：

| 子功能 | 作用 | 后端接口 |
|---|---|---|
| API权限审批工单 | 审批调用申请、设置专属折扣、收回权限 | `GET /api/console/admin/permissions/list`、`PUT /api/console/admin/permissions/{id}/audit` |
| API发布管理 | 查看接口目录、上线/下线 | `GET /api/console/admin/endpoints/list`、`PUT /api/console/admin/endpoints/{endpointId}/status` |
| API定价管理 | 调整接口基础点数 | `PUT /api/console/admin/permissions/endpoints/{endpointId}/cost` |

- 审批动作：`APPROVE`（可带 `customDiscount`）、`REJECT`（必须带 `auditRemark`）、`REVOKE`。
- 发布动作：body `{ "status": 0 | 1 }`；`0` 为下线（调用方收到 `40305`「API 已下线」），`1` 为上线。
- 接口目录由代码生成器（见 6.4）或手工 SQL 新增，默认 `status = 0`，必须经「API发布管理」上线。
- 旧路由 `/console/admin/audit` 已更名为 `/console/admin/api`，页面组件仍是 `views/console/admin/api/Index.vue`。

### 11.5 找回密码

登录页底部的「忘记密码」入口打开申请弹窗：输入账号 → 人机校验与频控 → 如果账号存在，
向注册邮箱发送带签名令牌的重置链接 → 用户打开 `/reset-password?token=...`，点击「验证重置链接」
后才校验换票（避免邮件安全网关预取链接提前消耗令牌）→ 提交新密码。

| 步骤 | 后端接口 | 说明 |
|---|---|---|
| 申请重置 | `POST /api/console/auth/forgot-password` | body `{ "account": "alice", "captchaToken": "..." }`；无论账号是否存在都返回成功 |
| 校验令牌 | `POST /api/console/auth/reset-password/verify` | body `{ "token": "..." }`；返回 `ticket`（10 分钟一次性票据）与脱敏邮箱 |
| 提交新密码 | `POST /api/console/auth/reset-password` | body `{ "ticket": "...", "newPassword": "..." }` |

实现要点：

- 令牌为「32 字节随机 nonce + HMAC-SHA256 签名」，数据库 `password_reset_token` 只存 SHA-256 哈希；
  令牌校验通过即写入 `used_at`，不可重复换票。
- 换发的票据绑定签发时的 `token_version`：重置成功后会话版本 +1，重置前换出的旧票据提交时因版本不匹配被拒绝，
  无法二次重置密码。
- 重置成功后 `sys_user.token_version + 1`，`JwtAuthInterceptor` 比对令牌里的 `ver` 声明与数据库版本，
  所有历史 JWT 立即失效（前端会清掉本地令牌，需重新登录）。
- 同一用户全部未使用的重置令牌一并作废；登录失败锁定计数同时清零，避免用户被旧密码爆破锁定挡住。
- 申请接口的邮件发送在独立线程池异步执行，账号存在与不存在两条路径都不等待 SMTP，响应耗时一致，
  避免通过时序差异枚举账号。
- Redis 故障时的补偿：换票先写票据再消费令牌（Redis 不可用时令牌保持可用，可凭原链接重试）；
  重置事务失败会把票据写回 Redis，用户无需重新走邮件流程。
- 频控三层：控制台匿名接口每 IP 限流、找回密码申请每 IP 每分钟 5 次、同一 IP + 账号 15 分钟 3 次；
  另有 60 秒邮件冷却与 Turnstile 人机校验。
- 相关错误码：`40005` 重置链接无效或已过期、`40006` 重置票据失效、`42904` 请求过于频繁。

### 11.6 修改密码

入口在控制台左上角用户下拉框的「修改密码」，弹窗包含「当前密码 + 新密码 + 确认新密码」，
新密码输入时实时展示强度条与规则勾选；浏览器 `autocomplete` 分别为 `current-password` /
`new-password`，且不禁止粘贴，兼容密码管理器。

| 步骤 | 后端接口 | 说明 |
|---|---|---|
| 修改密码 | `POST /api/console/auth/change-password` | body `{ "oldPassword": "...", "newPassword": "..." }`；需登录，用户身份只取自 JWT |

实现要点：

- 密码策略（注册、找回密码、修改密码共用，`security/PasswordPolicy`）：8-64 位，UTF-8 编码后
  不超过 72 字节（BCrypt 硬上限，一个汉字约占 3 字节），同时包含大写字母、小写字母和数字（符号可选）；
  拒绝常见弱口令与「常见词 + 纯数字」、「包含用户名」、4 位以上连续字符或 3 个以上重复字符。
- 修改成功后 `sys_user.token_version + 1`，并返回当前设备的新 JWT；其他设备的历史令牌因
  `ver` 不匹配立即失效，当前设备无需重新登录。
- 旧密码连续输错 5 次（15 分钟窗口）锁定修改功能 30 分钟（`42905`），成功或被锁定后清零计数；
  另有每 IP 每 15 分钟 10 次的窗口限流（`42900`）。
- 新密码不合规或与旧密码相同时直接拒绝（`40007` / `40009`），不消耗旧密码试错次数。

### 11.7 管理员充值留痕与月度限额

前端「用户管理」页的「充值额度」弹窗与「充值记录」抽屉：

| 功能 | 后端接口 | 说明 |
|---|---|---|
| 管理员充值 | `POST /api/console/admin/users/{id}/quota` | body `{ "amount": 1000, "remark": "活动补偿" }`；`remark` 选填，最多 255 字 |
| 当月额度 | `GET /api/console/admin/users/{id}/recharge-quota` | 返回当前管理员本月已充值、剩余额度与月度上限 |
| 充值记录 | `GET /api/console/admin/users/{id}/recharge-orders?page=1&size=10&source=2&status=1` | 管理员视角查看该用户的用户支付与管理员发放流水 |

实现要点：

- **一本流水**：管理员充值不新建表，而是在 `recharge_order` 写一条 `source=2`、`package_id=ADMIN_GRANT`、
  `amount_fen=0`、`pay_channel=ADMIN`、`status=1` 的已到账订单，并记录 `operator_id`、`operator_name` 快照与 `remark`。
  订单号以 `A` 开头，与用户支付的 `R` 单区分。
- **与充值中心融合**：用户自己的「充值记录」会同时展示管理员发放流水，渠道显示「管理员发放」、状态显示「已到账」、
  金额显示「—」；用户能解释余额来源，不需要第二套查询入口。
- **月度限额**：同一管理员对同一账号每个自然月累计不超过 `app.recharge.admin-monthly-limit-per-user`
  （默认 5000 点，`0` 表示禁用）。计数表 `admin_recharge_monthly_usage` 按
  `(operator_id, user_id, month_key)` 唯一；充值事务内先 `SELECT ... FOR UPDATE` 锁定计数行再校验、累加，
  订单、计数、余额三者同事务提交，并发请求不会超发。用户自己支付充值不占用管理员额度。
- **审计**：管理员可以在用户管理页直接打开「充值记录」抽屉，查看订单号、来源、点数、金额、状态、操作人、
  备注与时间；充值超限返回 `40306`，余额与计数均不变。
- **账户余额与 Redis**：管理员充值与支付入账都走 `QuotaLedger.credit`，MySQL 提交后再同步 Redis 热账本；
  月度限额只约束管理员发放，不影响用户支付充值。
- **分页契约**：`GET /recharge-orders` 沿用用户管理列表的 query 风格 `page/size`；前端
  `listUserRechargeOrders` 负责把 `usePagination` 的 `pageNum/pageSize` 映射过去，漏映射会导致后端忽略分页。
- **测试**：`AdminRechargeAuditApiIntegrationTest` 覆盖接口留痕与限额；`AdminRechargeConcurrencyIntegrationTest`
  用 10 个线程同抢 5000 点，断言只成功 5 次且订单、计数、余额一致；`AdminRechargeRollbackIntegrationTest`
  在 `credit` 失败时断言订单、计数、余额一起回滚；`AdminRechargeLimitServiceTest` 覆盖 `addGranted` 命中 0 行。

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

旧库升级时执行 `docs/schema.sql` 第 8 节的迁移段，其中会把 `api_endpoint_meta.status` 的默认值改为 `0`（新增接口默认未发布）。

### 12.3 测试数据与断言

- 测试使用 `src/test/resources/schema-h2.sql`。
- `PermissionApiIntegrationTest` 断言接口市场只返回 **2** 个接口；向 H2 增加目录行会破坏该断言，需要同步修改测试预期。
- H2 中 `api_endpoint_meta.status` 的建表默认值同样是 `0`；种子数据显式写 `1`。新增测试若插入目录行，需要显式设置期望的状态。
- `PermissionApiIntegrationTest#shouldPublishAndOfflineEndpointWithUnifiedOfflineError` 会在事务内把接口 1001 下线再上线，方法结束回滚，不影响其他用例。
- `PasswordResetApiIntegrationTest` 用 `@MockitoBean EmailService` 截获重置链接，覆盖“统一响应 + 只存哈希 + 换票 + 重置 + 旧 JWT 失效”全链路；
  测试环境通过 `app.password-reset.resend-cooling-seconds=0` 关闭邮件冷却，便于连续申请；邮件由
  `ResetMailDispatcher` 异步发送，断言需用 `verify(emailService, timeout(2000))` 等待调用到达。
- 注册已执行密码强度策略，集成测试统一使用 `Str0ngPass2026`；`"password123"` 这类密码会返回 `40007`。
- `ChangePasswordApiIntegrationTest` 覆盖改密后当前设备换发新令牌、旧令牌与旧密码失效、旧密码连续错误锁定、
  弱密码/同密码拒绝；`PasswordPolicyTest` 覆盖策略边界（长度、字符类别、弱口令、连续/重复、包含用户名）。
- `docs/schema.sql` 中的 1003–1005（个人日志、新闻搜索、IP 归属地）只存在于真实数据库脚本，不在 H2 中。
- 限流基于 Redis 1 秒固定窗口；连续 MockMvc 调用低 `rateLimitQps` 的 Key 会返回 429，多调用测试请使用高 `rateLimitQps`。

### 12.4 ES

- 构建客户端不会立即建立连接，ES 未启动时应用仍可启动。
- 真正调用检索接口且 ES 不可用时会返回 `50400`，属于预期行为。

### 12.5 前端

- `frontend/.gitignore` 中的 `logs` 规则必须写成 `/logs`，否则会忽略 `src/views/console/admin/logs/`，导致该目录的 Tailwind 类不生成。
- 测试登录页前先清除 `localStorage` 中的 `api_console_access_token`，否则 `guestOnly` 守卫会重定向。
- 调试台只展示 `applyStatus === 1 && endpointStatus === 1` 的接口，新增接口必须先上线并审批通过。
- 市场页、定价页对 `status = 0` 的接口显示「已下线」；市场仍允许提交申请，但实际调用返回 `40305`。
- 管理端「API管理」路由已从 `/console/admin/audit` 改为 `/console/admin/api`；旧书签会命中兜底重定向回首页。

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
| `40404` 接口不存在 | `api_endpoint_meta` 缺少 `path + POST` 行 | 补目录行 |
| `40305` API 已下线 | 接口 `status = 0`（未发布 / 被下线） | 在「API管理 → API发布管理」上线 |
| `40005` 重置链接无效 | 链接超过 30 分钟、已被使用，或签发后账号密码又被重置过 | 重新在登录页申请找回密码 |
| `40006` 重置会话失效 | 票据超过 10 分钟、已使用，或票据签发后密码已被重新重置 | 重新打开重置邮件中的新链接 |
| `40007` 密码不符合安全要求 | 不足 8 位、编码后超 72 字节、缺少大小写字母/数字、常见弱口令或包含用户名 | 按强度条提示更换密码 |
| `40008` 旧密码不正确 | 修改密码时当前密码输错 | 重新输入；连续 5 次会锁定 30 分钟 |
| `40009` 新旧密码相同 | 新密码与当前密码一致 | 更换一个未使用过的新密码 |
| `42905` 修改密码已锁定 | 旧密码连续输错 5 次 | 等待 30 分钟后重试 |
| 生成器提示“目标文件已存在” | 防覆盖保护 | 改用「修改已有接口」，或删除旧文件后重试 |
| 新生成接口调用返回 `40400`/`40404` | Handler 未注册（未重启）或目录行缺失 | 重新编译重启后端，并确认 `api_endpoint_meta` 有该行 |
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
| 接口生成器 | `backend/src/main/java/com/example/api_service/controller/AdminCodeGenController.java`、`service/impl/ApiCodeGenServiceImpl.java` |
| API 发布管理 | `backend/src/main/java/com/example/api_service/controller/AdminEndpointController.java` |
| 管理端 API 管理页 | `frontend/src/views/console/admin/api/Index.vue`、`frontend/src/api/permission.ts` |
| 计费拦截器 | `backend/src/main/java/com/example/api_service/config/interceptor/ApiKeyAuthInterceptor.java` |
| 公共契约 | `backend/src/main/java/com/example/api_service/common/` |
| 目录与索引 | `docs/schema.sql` |
| 前端请求封装 | `frontend/src/utils/request.ts` |
| 前端分页组合式函数 | `frontend/src/composables/usePagination.ts` |
