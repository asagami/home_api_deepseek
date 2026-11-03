package com.example.api_service.engine.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("MySQL / Oracle 方言 SQL 生成单元测试")
class DialectSqlGenerationTest {

    private static final String BASE_SQL = "SELECT id, name FROM users WHERE status = 1";

    private final MySqlDialect mySqlDialect = new MySqlDialect();
    private final OracleDialect oracleDialect = new OracleDialect();

    @Test
    @DisplayName("方言类型标识应正确")
    void shouldIdentifyDbTypes() {
        assertEquals("MYSQL", mySqlDialect.getDbType());
        assertEquals("ORACLE", oracleDialect.getDbType());
    }

    @Test
    @DisplayName("MySQL 方言应生成 LIMIT offset, limit 分页与带派生别名的 COUNT")
    void shouldBuildMySqlPageAndCountSql() {
        assertEquals(BASE_SQL + " LIMIT 20, 10", mySqlDialect.buildPageSql(BASE_SQL, 20, 10));
        assertEquals("SELECT COUNT(*) FROM (" + BASE_SQL + ") AS _tbl_count",
                mySqlDialect.buildCountSql(BASE_SQL));
        assertEquals("SELECT 1", mySqlDialect.getValidationQuery());
    }

    @Test
    @DisplayName("Oracle 12c+ 方言应生成 OFFSET/FETCH 分页与无别名 COUNT")
    void shouldBuildOraclePageAndCountSql() {
        assertEquals(BASE_SQL + " OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY",
                oracleDialect.buildPageSql(BASE_SQL, 20, 10));
        assertEquals("SELECT COUNT(*) FROM (" + BASE_SQL + ")",
                oracleDialect.buildCountSql(BASE_SQL));
        assertEquals("SELECT 1 FROM DUAL", oracleDialect.getValidationQuery());
    }

    @Test
    @DisplayName("偏移为 0 与 limit=1 的边界值应原样拼接")
    void shouldHandleBoundaryOffsetAndLimit() {
        assertEquals(BASE_SQL + " LIMIT 0, 1", mySqlDialect.buildPageSql(BASE_SQL, 0, 1));
        assertEquals(BASE_SQL + " OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY",
                oracleDialect.buildPageSql(BASE_SQL, 0, 1));
    }
}
