-- ==========================================
-- 接口: PostgreSQL用户分页查询
-- 路径: /api/v1/pg/ddl-users
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    ('PostgreSQL用户分页查询', '/api/v1/pg/ddl-users', 'POST', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"username":"postgres","superuser":"true"}', 1);


-- ==========================================
-- 接口: PostgreSQL用户分页查询
-- 路径: /api/v1/pg/ddl-users
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    ('PostgreSQL用户分页查询', '/api/v1/pg/ddl-users', 'POST', 2, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"username":"postgres","superuser":"true"}', 1);


-- ==========================================
-- 接口: PostgreSQL用户分页查询
-- 路径: /api/v1/pg/ddl-users1
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    ('PostgreSQL用户分页查询', '/api/v1/pg/ddl-users1', 'POST', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"username":"postgres","superuser":"true"}', 1);


