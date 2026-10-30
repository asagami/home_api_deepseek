DROP TABLE IF EXISTS recharge_order;
DROP TABLE IF EXISTS user_api_permission;
DROP TABLE IF EXISTS api_access_log;
DROP TABLE IF EXISTS api_endpoint_meta;
DROP TABLE IF EXISTS password_reset_token;
DROP TABLE IF EXISTS api_key_info;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    balance_quota BIGINT NOT NULL DEFAULT 1000,
    status TINYINT NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_username UNIQUE (username)
);

CREATE TABLE password_reset_token (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    request_ip VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_reset_user ON password_reset_token (user_id);

CREATE TABLE api_key_info (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    key_name VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    rate_limit_qps INT NOT NULL DEFAULT 5,
    total_quota BIGINT NOT NULL DEFAULT -1,
    used_quota BIGINT NOT NULL DEFAULT 0,
    expired_time TIMESTAMP,
    last_used_time TIMESTAMP,
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_key_user_id ON api_key_info (user_id);

CREATE TABLE api_endpoint_meta (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    path VARCHAR(128) NOT NULL,
    method VARCHAR(16) NOT NULL DEFAULT 'POST',
    engine_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_SQL',
    cost_per_call INT NOT NULL DEFAULT 1,
    description VARCHAR(512),
    request_schema VARCHAR(4000),
    response_schema VARCHAR(4000),
    engine_config VARCHAR(4000),
    status TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_path_method UNIQUE (path, method)
);

CREATE TABLE api_access_log (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    api_key_id BIGINT NOT NULL,
    endpoint_path VARCHAR(128) NOT NULL,
    http_status INT NOT NULL,
    cost_points INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL,
    client_ip VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_log_user_time ON api_access_log (user_id, create_time);
CREATE INDEX idx_log_key_time ON api_access_log (api_key_id, create_time);

CREATE TABLE user_api_permission (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    endpoint_id BIGINT NOT NULL,
    apply_reason VARCHAR(512) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    custom_discount DECIMAL(3, 2) NOT NULL DEFAULT 1.00,
    audit_remark VARCHAR(512),
    auditor_id BIGINT,
    audit_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_endpoint UNIQUE (user_id, endpoint_id, is_deleted)
);

CREATE INDEX idx_permission_status ON user_api_permission (status);

CREATE TABLE recharge_order (
    id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    package_id VARCHAR(32) NOT NULL,
    amount_fen INT NOT NULL,
    total_points BIGINT NOT NULL,
    pay_channel VARCHAR(16) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    qr_code_url VARCHAR(512),
    trade_no VARCHAR(64),
    pay_time TIMESTAMP,
    expire_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_no UNIQUE (order_no)
);

CREATE INDEX idx_recharge_user_status ON recharge_order (user_id, status, create_time);

-- 集成测试使用的接口目录；ID 固定便于测试授权
INSERT INTO api_endpoint_meta (id, name, path, method, cost_per_call, description, status, is_deleted)
VALUES (1001, '回声测试', '/api/v1/services/echo', 'GET', 1, '原样返回 message', 1, 0),
       (1002, '文本统计', '/api/v1/services/text-stats', 'POST', 1, '统计字符与词频', 1, 0);
