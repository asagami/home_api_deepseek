CREATE DATABASE IF NOT EXISTS `open_api_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `open_api_platform`;

-- 1. 开发者用户表
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID (雪花算法)',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名/账号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色: USER-普通开发者, ADMIN-管理员',
    `balance_quota` BIGINT NOT NULL DEFAULT 1000 COMMENT '账户剩余免费/充值调用总额度(次)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-封禁',
    `token_version` INT NOT NULL DEFAULT 0 COMMENT '会话版本: 密码重置时+1, 历史 JWT 全部失效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删, 1-已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='开发者用户表';

-- 1.1 密码重置令牌表：只存令牌哈希，一次性使用
CREATE TABLE IF NOT EXISTS `password_reset_token` (
    `id` BIGINT NOT NULL COMMENT '主键ID (雪花算法)',
    `user_id` BIGINT NOT NULL COMMENT '申请重置的用户ID',
    `token_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256(令牌) 十六进制',
    `expires_at` DATETIME NOT NULL COMMENT '令牌过期时间',
    `used_at` DATETIME DEFAULT NULL COMMENT '校验换票时间, 非空表示已使用',
    `request_ip` VARCHAR(64) DEFAULT NULL COMMENT '申请时的客户端IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reset_token_hash` (`token_hash`),
    KEY `idx_reset_user` (`user_id`)
) ENGINE=InnoDB COMMENT='密码重置令牌表';

-- 2. API Key 密钥管理表 (核心：不存明文，只存 Hash 与脱敏前缀)
CREATE TABLE `api_key_info` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `key_name` VARCHAR(64) NOT NULL COMMENT '密钥备注名称 (如: 生产环境Key)',
    `key_prefix` VARCHAR(32) NOT NULL COMMENT '脱敏展示前缀 (如: sk-proj...8f9a)',
    `key_hash` VARCHAR(64) NOT NULL COMMENT 'API Key 的 SHA-256 哈希值 (用于鉴权检索)',
    `rate_limit_qps` INT NOT NULL DEFAULT 5 COMMENT '每秒最大请求数 (QPS限流阈值)',
    `total_quota` BIGINT NOT NULL DEFAULT -1 COMMENT '该Key总额度限制 (-1表示共享账户额度)',
    `used_quota` BIGINT NOT NULL DEFAULT 0 COMMENT '该Key已使用次数',
    `expired_time` DATETIME DEFAULT NULL COMMENT '过期时间 (NULL表示永不过期)',
    `last_used_time` DATETIME DEFAULT NULL COMMENT '最后调用时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_key_hash` (`key_hash`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='API密钥表';

-- 3. 对外提供的 API 服务目录表 (用于前端在线文档与调试台)
CREATE TABLE `api_endpoint_meta` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(64) NOT NULL COMMENT '接口名称 (如: IP归属地查询)',
    `path` VARCHAR(128) NOT NULL COMMENT '接口路径 (如: /api/v1/tools/ip-locate)',
    `method` VARCHAR(16) NOT NULL DEFAULT 'POST' COMMENT '请求方式: GET/POST',
    `engine_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_SQL' COMMENT '引擎类型: INTERNAL_SQL, EXTERNAL_SQL, ES, HTTP_PROXY',
    `cost_per_call` INT NOT NULL DEFAULT 1 COMMENT '单次调用扣减额度',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '接口描述',
    `request_schema` JSON DEFAULT NULL COMMENT '请求参数示例或JSON Schema',
    `response_schema` JSON DEFAULT NULL COMMENT '返回结果示例',
    `engine_config` JSON DEFAULT NULL COMMENT '引擎专属配置(JSON)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 1-上线, 0-已下线 (新增接口默认 0, 需管理员上线)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_path_method` (`path`, `method`)
) ENGINE=InnoDB COMMENT='对外API服务目录表';

-- 4. API 调用日志流水表 (用于统计图表与计费对账)
CREATE TABLE `api_access_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '调用者UserID',
    `api_key_id` BIGINT NOT NULL COMMENT '使用的API Key ID',
    `endpoint_path` VARCHAR(128) NOT NULL COMMENT '调用的接口路径',
    `http_status` INT NOT NULL COMMENT '响应状态码 (200, 429, 500等)',
    `latency_ms` INT NOT NULL COMMENT '耗时(毫秒)',
    `client_ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_key_time` (`api_key_id`, `create_time`)
) ENGINE=InnoDB COMMENT='API调用日志流水表';


CREATE TABLE `sys_email_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(128) NOT NULL COMMENT '接收邮箱',
    `code` VARCHAR(10) NOT NULL COMMENT '验证码',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间 (生成时间 + 5分钟)',
    `is_used` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用: 0-否, 1-是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_email_code` (`email`, `code`, `expire_time`)
) ENGINE=InnoDB COMMENT='邮箱验证码临时表';


-- 5. 用户 API 权限申请与专属计费表
CREATE TABLE `user_api_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '申请人UserID',
    `endpoint_id` BIGINT NOT NULL COMMENT '申请的API服务ID (关联 api_endpoint_meta.id)',
    `apply_reason` VARCHAR(512) NOT NULL COMMENT '申请理由与业务场景说明',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '审批状态: 0-待审批(PENDING), 1-已通过(APPROVED), 2-已驳回(REJECTED), 3-已收回权限(REVOKED)',
    `custom_discount` DECIMAL(3, 2) NOT NULL DEFAULT 1.00 COMMENT '专属折扣率 (1.00为原价, 0.80为8折, 0.00为免费特权, 审批时由Admin设定)',
    `audit_remark` VARCHAR(512) DEFAULT NULL COMMENT '管理员审批意见/驳回原因',
    `auditor_id` BIGINT DEFAULT NULL COMMENT '审批管理员ID',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审批时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_endpoint` (`user_id`, `endpoint_id`, `is_deleted`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='用户API权限申请与专属折扣表';

-- 6. 修改原有的 API 调用日志表，增加“实际扣减点数”字段
ALTER TABLE `api_access_log` 
ADD COLUMN `cost_points` INT NOT NULL DEFAULT 0 COMMENT '本次调用实际扣减点数' AFTER `http_status`;

-- 7. 初始化示例接口目录
-- 细粒度权限校验依赖 api_endpoint_meta 中的 path + method，缺少目录记录时 /api/v1 请求会被拒绝。
INSERT IGNORE INTO `api_endpoint_meta`
    (`id`, `name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    (1001, '回声测试', '/api/v1/services/echo', 'GET', 1, '原样返回 message，用于验证鉴权与计费链路',
     '{"message":"Hello, API!"}', 1),
    (1002, '文本统计', '/api/v1/services/text-stats', 'POST', 1, '统计字符数、单词数、去重单词数及 Top 10 词频',
     '{"text":"Hello world, hello API! hello"}', 1),
    (1003, '个人调用日志', '/api/v1/data/my-logs', 'POST', 1, '按 HTTP 状态筛选当前用户的调用日志，按页大小阶梯计费',
     '{"pageNum":1,"pageSize":20,"httpStatus":200}', 1),
    (1004, '新闻搜索', '/api/v1/search/news', 'POST', 1, '基于 Elasticsearch 的新闻标题与正文检索',
     '{"keyword":"人工智能","pageNum":1,"pageSize":20}', 1),
    (1005, 'IP 归属地', '/api/v1/tools/ip-locate', 'POST', 1, '代理第三方 IP 归属地查询并清洗为标准结果',
     '{"ip":"8.8.8.8"}', 1),
    (1008, 'PostgreSQL系统用户查询', '/api/v1/pg/sample-users', 'POST', 1, '外部 PostgreSQL 数据库分页查询示例接口',
     '{"pageNum":1,"pageSize":20,"username":"postgres"}', 1);

-- 供管理员按时间范围统计全站调用量、成功率、总消耗点数（覆盖索引，无需回表）
ALTER TABLE `api_access_log`
    ADD INDEX `idx_time_status_cost` (`create_time`, `http_status`, `cost_points`, `latency_ms`);

-- 供管理员按接口路径排行、排查慢接口与高故障接口
ALTER TABLE `api_access_log`
    ADD INDEX `idx_endpoint_time` (`endpoint_path`, `create_time`, `http_status`);

-- 7. 开发者点数充值订单表（用户支付 + 管理员直接充值共用一本流水）
CREATE TABLE `recharge_order` (
                                  `id` BIGINT NOT NULL COMMENT '主键ID (雪花算法)',
                                  `order_no` VARCHAR(64) NOT NULL COMMENT '平台商户订单号 (唯一, 用户单 R 开头, 管理员单 A 开头)',
                                  `user_id` BIGINT NOT NULL COMMENT '充值用户ID',
                                  `package_id` VARCHAR(32) NOT NULL COMMENT '套餐档位ID (如: PKG_10)；管理员充值为 ADMIN_GRANT',
                                  `amount_fen` INT NOT NULL COMMENT '实付金额(单位:分)；管理员充值为 0',
                                  `total_points` BIGINT NOT NULL COMMENT '到账总点数 (基础点数 + 赠送点数)',
                                  `pay_channel` VARCHAR(16) NOT NULL COMMENT '支付渠道: ALIPAY-支付宝, WECHAT-微信支付；管理员充值为 ADMIN',
                                  `source` TINYINT NOT NULL DEFAULT 1 COMMENT '订单来源: 1-用户支付充值, 2-管理员直接充值',
                                  `operator_id` BIGINT DEFAULT NULL COMMENT '操作管理员ID (source=2 时有值)',
                                  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作管理员用户名快照',
                                  `remark` VARCHAR(255) DEFAULT NULL COMMENT '充值原因/备注',
                                  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态: 0-待支付(PENDING), 1-已支付/已到账(PAID), 2-已关闭/超时(CLOSED)',
                                  `qr_code_url` VARCHAR(512) DEFAULT NULL COMMENT '支付二维码内容串 (供前端生成二维码)',
                                  `trade_no` VARCHAR(64) DEFAULT NULL COMMENT '第三方支付流水号 (支付宝/微信返回)',
                                  `pay_time` DATETIME DEFAULT NULL COMMENT '实际支付/到账时间',
                                  `expire_time` DATETIME NOT NULL COMMENT '订单二维码过期时间 (通常为创建时间+15分钟)',
                                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  `is_deleted` TINYINT NOT NULL DEFAULT 0,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_order_no` (`order_no`),
                                  KEY `idx_user_status` (`user_id`, `status`, `create_time`),
                                  KEY `idx_source_time` (`source`, `create_time`)
) ENGINE=InnoDB COMMENT='点数充值订单表';

-- 7.1 管理员月度充值限额使用表：同一管理员对同一账号每个自然月累计上限
CREATE TABLE IF NOT EXISTS `admin_recharge_monthly_usage` (
                                  `id` BIGINT NOT NULL COMMENT '主键ID (雪花算法)',
                                  `operator_id` BIGINT NOT NULL COMMENT '操作管理员ID',
                                  `user_id` BIGINT NOT NULL COMMENT '被充值用户ID',
                                  `month_key` VARCHAR(7) NOT NULL COMMENT '自然月, 格式 yyyy-MM',
                                  `granted_points` BIGINT NOT NULL DEFAULT 0 COMMENT '本月已充值点数',
                                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_admin_recharge_month` (`operator_id`, `user_id`, `month_key`)
) ENGINE=InnoDB COMMENT='管理员月度充值限额使用表';

-- ========================================================
-- 8. 历史数据库增量平滑迁移补丁
-- 若已有旧版 open_api_platform 库，请手动执行以下 DDL（新版服务已内置自动检测与迁移组件）：
-- ========================================================
ALTER TABLE `api_endpoint_meta`
    ADD COLUMN IF NOT EXISTS `engine_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_SQL' AFTER `method`,
    ADD COLUMN IF NOT EXISTS `engine_config` TEXT NULL AFTER `response_schema`;

-- 新增接口默认下线，需管理员在「API 管理 → API 发布管理」中上线（改为 1）后才可调用
ALTER TABLE `api_endpoint_meta`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 1-上线, 0-已下线 (新增接口默认 0, 需管理员上线)';

-- 密码找回：会话版本列（旧库补列；表结构由服务启动时自愈或本文件创建）
ALTER TABLE `sys_user`
    ADD COLUMN IF NOT EXISTS `token_version` INT NOT NULL DEFAULT 0
    COMMENT '会话版本: 密码重置时+1, 历史 JWT 全部失效' AFTER `status`;

-- 管理员充值留痕：旧库补列。
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；可直接依赖服务启动时的 DatabaseSchemaMigrator 自动补列。
ALTER TABLE `recharge_order`
    ADD COLUMN IF NOT EXISTS `source` TINYINT NOT NULL DEFAULT 1
    COMMENT '订单来源: 1-用户支付充值, 2-管理员直接充值' AFTER `pay_channel`,
    ADD COLUMN IF NOT EXISTS `operator_id` BIGINT DEFAULT NULL
    COMMENT '操作管理员ID' AFTER `source`,
    ADD COLUMN IF NOT EXISTS `operator_name` VARCHAR(64) DEFAULT NULL
    COMMENT '操作管理员用户名快照' AFTER `operator_id`,
    ADD COLUMN IF NOT EXISTS `remark` VARCHAR(255) DEFAULT NULL
    COMMENT '充值原因/备注' AFTER `operator_name`;

-- 旧库补索引（若已存在会报 Duplicate key name，可忽略；自愈组件会先检测）
ALTER TABLE `recharge_order`
    ADD INDEX `idx_source_time` (`source`, `create_time`);
