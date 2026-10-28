package com.example.api_service.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库表结构平滑迁移与自愈组件。
 *
 * <p>在服务启动阶段自动检测并补齐系统核心元数据表（如 api_endpoint_meta）所需的列，
 * 避免旧环境未手动执行 ALTER TABLE 时抛出 BadSqlGrammarException (Unknown column)。</p>
 */
@Slf4j
@Component
@Order(-100)
@RequiredArgsConstructor
public class DatabaseSchemaMigrator {

    private final DataSource dataSource;

    @PostConstruct
    public void migrate() {
        if (dataSource == null) {
            return;
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            log.info("正在检测数据库表结构完整性: catalog={}, product={}", catalog, metaData.getDatabaseProductName());

            // 1. 检查并补充 api_endpoint_meta.engine_type
            if (!hasColumn(metaData, catalog, "api_endpoint_meta", "engine_type")) {
                addColumn(conn, "ALTER TABLE `api_endpoint_meta` ADD COLUMN `engine_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_SQL' AFTER `method`");
                log.info("已自动为 api_endpoint_meta 补充 engine_type 列");
            }

            // 2. 检查并补充 api_endpoint_meta.engine_config
            if (!hasColumn(metaData, catalog, "api_endpoint_meta", "engine_config")) {
                addColumn(conn, "ALTER TABLE `api_endpoint_meta` ADD COLUMN `engine_config` TEXT NULL AFTER `response_schema`");
                log.info("已自动为 api_endpoint_meta 补充 engine_config 列");
            }
        } catch (Exception e) {
            log.warn("执行数据库元数据表结构自愈检测时跳过或遇到提示: {}", e.getMessage());
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String catalog, String tableName, String columnName) {
        try (ResultSet rs = metaData.getColumns(catalog, null, tableName, columnName)) {
            if (rs.next()) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try (ResultSet rs = metaData.getColumns(catalog, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void addColumn(Connection conn, String ddl) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (Exception e) {
            log.warn("执行表结构补齐 DDL 提示: [{}], 详情: {}", ddl, e.getMessage());
        }
    }
}
