package com.example.api_service.engine.dialect;

import org.springframework.stereotype.Component;

/**
 * PostgreSQL 数据库方言实现。
 */
@Component
public class PostgreSqlDialect implements DbDialect {

    @Override
    public String getDbType() {
        return "POSTGRESQL";
    }

    @Override
    public String buildPageSql(String originalSql, long offset, int limit) {
        return originalSql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String buildCountSql(String originalSql) {
        // PostgreSQL 强制要求派生子查询必须指定别名
        return "SELECT COUNT(*) FROM (" + originalSql + ") AS _tbl_count";
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1";
    }
}
