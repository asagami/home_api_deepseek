package com.example.api_service.engine.external;

import com.example.api_service.common.BusinessException;
import com.example.api_service.engine.dialect.PostgreSqlDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("外部 SQL 核心组件单元测试")
class ExternalSqlComponentsTest {

    private final SqlSecurityValidator validator = new SqlSecurityValidator();
    private final PostgreSqlDialect pgDialect = new PostgreSqlDialect();

    @Test
    @DisplayName("只读 SELECT 语句应通过安全审计")
    void shouldPassValidSelectSql() {
        assertDoesNotThrow(() -> validator.validateSelectOnly("SELECT id, name FROM users WHERE status = 1"));
        assertDoesNotThrow(() -> validator.validateSelectOnly("SELECT u.id, o.amount FROM users u LEFT JOIN orders o ON u.id = o.user_id"));
    }

    @Test
    @DisplayName("非 SELECT 语句应被安全审计拦截")
    void shouldRejectNonSelectSql() {
        assertThrows(BusinessException.class, () -> validator.validateSelectOnly("INSERT INTO users(id, name) VALUES(1, 'hack')"));
        assertThrows(BusinessException.class, () -> validator.validateSelectOnly("UPDATE users SET status = 0"));
        assertThrows(BusinessException.class, () -> validator.validateSelectOnly("DELETE FROM users WHERE id = 1"));
        assertThrows(BusinessException.class, () -> validator.validateSelectOnly("DROP TABLE users"));
        assertThrows(BusinessException.class, () -> validator.validateSelectOnly("ALTER TABLE users ADD COLUMN dummy VARCHAR(32)"));
    }

    @Test
    @DisplayName("多语句分号组合执行应被拦截")
    void shouldRejectMultiStatements() {
        assertThrows(BusinessException.class, () -> validator.validateSelectOnly("SELECT * FROM users; DROP TABLE orders;"));
    }

    @Test
    @DisplayName("PG 方言应生成合法的 LIMIT OFFSET 分页与带派生别名的 COUNT 语句")
    void shouldGenerateCorrectPgSql() {
        String baseSql = "SELECT id, name FROM pg_user WHERE 1=1";
        String pageSql = pgDialect.buildPageSql(baseSql, 20, 10);
        assertEquals("SELECT id, name FROM pg_user WHERE 1=1 LIMIT 10 OFFSET 20", pageSql);

        String countSql = pgDialect.buildCountSql(baseSql);
        assertEquals("SELECT COUNT(*) FROM (SELECT id, name FROM pg_user WHERE 1=1) AS _tbl_count", countSql);
    }
}
