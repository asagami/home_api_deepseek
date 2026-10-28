-- ==========================================
-- 接口: 天气查询
-- 路径: /api/v1/pg/weatherqry
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    ('天气查询', '/api/v1/pg/weatherqry', 'POST', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"local":"postgres","time":"true"}', 1);


-- ==========================================
-- 接口: 流水查询
-- 路径: /api/v1/es/detailqry
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `cost_per_call`, `description`, `request_schema`, `status`)
VALUES
    ('流水查询', '/api/v1/es/detailqry', 'POST', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"actno":"postgres","begdt":"true","enddt":""}', 1);


-- ==========================================
-- 接口: es用户分页查询
-- 路径: /api/v1/es/esqry
-- 引擎: ES
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `engine_type`, `cost_per_call`, `description`, `request_schema`, `engine_config`, `status`)
VALUES
    ('es用户分页查询', '/api/v1/es/esqry', 'POST', 'ES', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"username":"postgres","superuser":"true"}', '{"domain":"esqry","apiName":"es用户分页查询","description":"外部 PostgreSQL 数据库用户信息分页查询接口","endpointPath":"/api/v1/es/esqry","costPerCall":1,"engineType":"ES","targetDataSourceKey":"pg-local","paged":true,"customSql":"SELECT usesysid, usename, usesuper, valuntil FROM pg_catalog.pg_user WHERE 1=1","esIndexName":"sample_index","upstreamBaseUrl":"https://api.example.com/v1","upstreamHttpMethod":"GET","upstreamHeaders":"{\n  \"Accept\": \"application/json\"\n}","requestFields":[{"name":"username","type":"String","description":"用户名关键字","example":"postgres","required":false,"maxLength":null},{"name":"superuser","type":"Boolean","description":"是否超级管理员","example":"true","required":false,"maxLength":null}],"responseFields":[{"name":"userId","type":"String","description":"用户ID","example":"10","required":null,"maxLength":null},{"name":"username","type":"String","description":"用户名","example":"postgres","required":null,"maxLength":null},{"name":"superuser","type":"Boolean","description":"是否超级用户","example":"true","required":null,"maxLength":null},{"name":"validUntil","type":"String","description":"过期时间","example":"infinity","required":null,"maxLength":null}]}', 1);


-- ==========================================
-- 接口: api用户分页查询
-- 路径: /api/v1/es/apiqry
-- 引擎: HTTP_PROXY
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `engine_type`, `cost_per_call`, `description`, `request_schema`, `engine_config`, `status`)
VALUES
    ('api用户分页查询', '/api/v1/es/apiqry', 'POST', 'HTTP_PROXY', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"username":"postgres","superuser":"true"}', '{"domain":"apiqry","apiName":"api用户分页查询","description":"外部 PostgreSQL 数据库用户信息分页查询接口","endpointPath":"/api/v1/es/apiqry","costPerCall":1,"engineType":"HTTP_PROXY","targetDataSourceKey":"pg-local","paged":true,"customSql":"SELECT usesysid, usename, usesuper, valuntil FROM pg_catalog.pg_user WHERE 1=1","esIndexName":"sample_index","upstreamBaseUrl":"https://api.example.com/v1","upstreamHttpMethod":"POST","upstreamHeaders":"{\n  \"Accept\": \"application/json\"\n}","requestFields":[{"name":"username","type":"String","description":"用户名关键字","example":"postgres","required":false,"maxLength":null},{"name":"superuser","type":"Boolean","description":"是否超级管理员","example":"true","required":false,"maxLength":null}],"responseFields":[{"name":"userId","type":"String","description":"用户ID","example":"10","required":null,"maxLength":null},{"name":"username","type":"String","description":"用户名","example":"postgres","required":null,"maxLength":null},{"name":"superuser","type":"Boolean","description":"是否超级用户","example":"true","required":null,"maxLength":null},{"name":"validUntil","type":"String","description":"过期时间","example":"infinity","required":null,"maxLength":null}]}', 1);


-- ==========================================
-- 接口: mysql用户分页查询
-- 路径: /api/v1/pg/mysqlqry
-- 引擎: INTERNAL_SQL
-- ==========================================
INSERT IGNORE INTO `api_endpoint_meta`
    (`name`, `path`, `method`, `engine_type`, `cost_per_call`, `description`, `request_schema`, `engine_config`, `status`)
VALUES
    ('mysql用户分页查询', '/api/v1/pg/mysqlqry', 'POST', 'INTERNAL_SQL', 1, '外部 PostgreSQL 数据库用户信息分页查询接口', '{"pageNum":1,"pageSize":20,"username":"postgres","superuser":"true"}', '{"domain":"mysqlqry","apiName":"mysql用户分页查询","description":"外部 PostgreSQL 数据库用户信息分页查询接口","endpointPath":"/api/v1/pg/mysqlqry","costPerCall":1,"engineType":"INTERNAL_SQL","targetDataSourceKey":"pg-local","paged":true,"customSql":"select a,b from c where 1=1","esIndexName":"","upstreamBaseUrl":"","upstreamHttpMethod":"GET","upstreamHeaders":"","requestFields":[{"name":"username","type":"String","description":"username","example":"postgres","required":false,"maxLength":null},{"name":"superuser","type":"Boolean","description":"superuser","example":"true","required":false,"maxLength":null}],"responseFields":[{"name":"usesysid","type":"String","description":"usesysid","example":"","required":null,"maxLength":null},{"name":"usename","type":"String","description":"usename","example":"","required":null,"maxLength":null},{"name":"usesuper","type":"String","description":"usesuper","example":"","required":null,"maxLength":null},{"name":"valuntil","type":"String","description":"valuntil","example":"","required":null,"maxLength":null}]}', 1);


