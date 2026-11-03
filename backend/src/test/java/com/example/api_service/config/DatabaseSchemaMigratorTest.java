package com.example.api_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据库表结构自愈迁移单元测试")
class DatabaseSchemaMigratorTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData metaData;
    @Mock
    private Statement statement;
    @Mock
    private ResultSet emptyResultSet;
    @Mock
    private ResultSet presentResultSet;

    @Test
    @DisplayName("DataSource 为空时应安全跳过")
    void shouldSkipWhenDataSourceIsNull() {
        assertDoesNotThrow(() -> new DatabaseSchemaMigrator(null).migrate());
    }

    @Test
    @DisplayName("缺失列/表/索引时应执行全部 10 条补齐 DDL")
    void shouldApplyAllMissingDdlStatements() throws Exception {
        stubConnection();
        stubEmptySchema();
        when(connection.createStatement()).thenReturn(statement);

        new DatabaseSchemaMigrator(dataSource).migrate();

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement, times(10)).execute(ddlCaptor.capture());
        List<String> ddl = ddlCaptor.getAllValues();

        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`engine_type`")), "应补充 engine_type 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`engine_config`")), "应补充 engine_config 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`token_version`")), "应补充 token_version 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("password_reset_token")), "应创建重置令牌表");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`source`")), "应补充 recharge_order.source 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`operator_id`")), "应补充 operator_id 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`operator_name`")), "应补充 operator_name 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("`remark`")), "应补充 remark 列");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("idx_source_time")), "应创建 idx_source_time 索引");
        assertTrue(ddl.stream().anyMatch(sql -> sql.contains("admin_recharge_monthly_usage")),
                "应创建管理员月度限额表");
    }

    @Test
    @DisplayName("表结构已完整时不应执行任何 DDL")
    void shouldNotExecuteDdlWhenSchemaIsComplete() throws Exception {
        stubConnection();
        when(metaData.getColumns(any(), any(), any(), any())).thenReturn(presentResultSet);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(presentResultSet);
        when(metaData.getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(presentResultSet);
        when(presentResultSet.next()).thenReturn(true);
        when(presentResultSet.getString("INDEX_NAME")).thenReturn("idx_source_time");

        new DatabaseSchemaMigrator(dataSource).migrate();

        verify(connection, never()).createStatement();
    }

    @Test
    @DisplayName("数据库连接失败时应吞掉异常不阻断启动")
    void shouldSwallowConnectionFailure() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

        assertDoesNotThrow(() -> new DatabaseSchemaMigrator(dataSource).migrate());
    }

    @Test
    @DisplayName("DDL 执行失败时应逐条吞掉异常不阻断启动")
    void shouldSwallowDdlExecutionFailure() throws Exception {
        stubConnection();
        stubEmptySchema();
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenThrow(new SQLException("ddl denied"));

        assertDoesNotThrow(() -> new DatabaseSchemaMigrator(dataSource).migrate());
        verify(statement, times(10)).execute(anyString());
    }

    private void stubConnection() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("api_service_test");
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
    }

    private void stubEmptySchema() throws SQLException {
        when(metaData.getColumns(any(), any(), any(), any())).thenReturn(emptyResultSet);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(emptyResultSet);
        when(metaData.getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(emptyResultSet);
        when(emptyResultSet.next()).thenReturn(false);
    }
}
