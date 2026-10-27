package com.example.api_service.engine.dialect;

import org.springframework.stereotype.Component;

/**
 * MySQL 数据库方言实现。
 */
@Component
public class MySqlDialect implements DbDialect {

    @Override
    public String getDbType() {
        return "MYSQL";
    }

    @Override
    public String buildPageSql(String originalSql, long offset, int limit) {
        return originalSql + " LIMIT " + offset + ", " + limit;
    }

    @Override
    public String buildCountSql(String originalSql) {
        return "SELECT COUNT(*) FROM (" + originalSql + ") AS _tbl_count";
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1";
    }
}
