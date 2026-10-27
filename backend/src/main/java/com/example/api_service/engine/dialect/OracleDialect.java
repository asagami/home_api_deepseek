package com.example.api_service.engine.dialect;

import org.springframework.stereotype.Component;

/**
 * Oracle 数据库方言实现 (Oracle 12c+ 标准语法)。
 */
@Component
public class OracleDialect implements DbDialect {

    @Override
    public String getDbType() {
        return "ORACLE";
    }

    @Override
    public String buildPageSql(String originalSql, long offset, int limit) {
        return originalSql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    public String buildCountSql(String originalSql) {
        return "SELECT COUNT(*) FROM (" + originalSql + ")";
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1 FROM DUAL";
    }
}
