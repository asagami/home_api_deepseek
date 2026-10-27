package com.example.api_service.engine.dialect;

/**
 * 异构数据库方言抽象。
 */
public interface DbDialect {

    /**
     * 数据库类型标识（如 POSTGRESQL, MYSQL, ORACLE）。
     */
    String getDbType();

    /**
     * 生成分页查询包装 SQL。
     */
    String buildPageSql(String originalSql, long offset, int limit);

    /**
     * 生成求总记录数包装 SQL。
     */
    String buildCountSql(String originalSql);

    /**
     * 连接探活 SQL。
     */
    String getValidationQuery();
}
