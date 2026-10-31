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
                executeDdl(conn, "ALTER TABLE `api_endpoint_meta` ADD COLUMN `engine_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_SQL' AFTER `method`");
                log.info("已自动为 api_endpoint_meta 补充 engine_type 列");
            }

            // 2. 检查并补充 api_endpoint_meta.engine_config
            if (!hasColumn(metaData, catalog, "api_endpoint_meta", "engine_config")) {
                executeDdl(conn, "ALTER TABLE `api_endpoint_meta` ADD COLUMN `engine_config` TEXT NULL AFTER `response_schema`");
                log.info("已自动为 api_endpoint_meta 补充 engine_config 列");
            }

            // 3. 检查并补充 sys_user.token_version（密码重置后失效历史 JWT）
            if (!hasColumn(metaData, catalog, "sys_user", "token_version")) {
                executeDdl(conn, "ALTER TABLE `sys_user` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 AFTER `status`");
                log.info("已自动为 sys_user 补充 token_version 列");
            }

            // 4. 检查并创建密码重置令牌表
            if (!hasTable(metaData, catalog, "password_reset_token")) {
                executeDdl(conn, PASSWORD_RESET_TOKEN_DDL);
                log.info("已自动创建 password_reset_token 表");
            }

            // 5. 管理员充值留痕：recharge_order 增加来源、操作人与备注
            if (!hasColumn(metaData, catalog, "recharge_order", "source")) {
                executeDdl(conn, "ALTER TABLE `recharge_order` ADD COLUMN `source` TINYINT NOT NULL DEFAULT 1 "
                        + "COMMENT '订单来源: 1-用户支付, 2-管理员直接充值' AFTER `pay_channel`");
                log.info("已自动为 recharge_order 补充 source 列");
            }
            if (!hasColumn(metaData, catalog, "recharge_order", "operator_id")) {
                executeDdl(conn, "ALTER TABLE `recharge_order` ADD COLUMN `operator_id` BIGINT NULL "
                        + "COMMENT '操作管理员ID' AFTER `source`");
                log.info("已自动为 recharge_order 补充 operator_id 列");
            }
            if (!hasColumn(metaData, catalog, "recharge_order", "operator_name")) {
                executeDdl(conn, "ALTER TABLE `recharge_order` ADD COLUMN `operator_name` VARCHAR(64) NULL "
                        + "COMMENT '操作管理员用户名快照' AFTER `operator_id`");
                log.info("已自动为 recharge_order 补充 operator_name 列");
            }
            if (!hasColumn(metaData, catalog, "recharge_order", "remark")) {
                executeDdl(conn, "ALTER TABLE `recharge_order` ADD COLUMN `remark` VARCHAR(255) NULL "
                        + "COMMENT '充值原因/备注' AFTER `operator_name`");
                log.info("已自动为 recharge_order 补充 remark 列");
            }
            if (!hasIndex(metaData, catalog, "recharge_order", "idx_source_time")) {
                executeDdl(conn, "CREATE INDEX `idx_source_time` ON `recharge_order` (`source`, `create_time`)");
                log.info("已自动为 recharge_order 创建 idx_source_time 索引");
            }

            // 6. 检查并创建管理员月度充值限额计数表
            if (!hasTable(metaData, catalog, "admin_recharge_monthly_usage")) {
                executeDdl(conn, ADMIN_RECHARGE_USAGE_DDL);
                log.info("已自动创建 admin_recharge_monthly_usage 表");
            }
        } catch (Exception e) {
            log.warn("执行数据库元数据表结构自愈检测时跳过或遇到提示: {}", e.getMessage());
        }
    }

    private static final String PASSWORD_RESET_TOKEN_DDL = """
            CREATE TABLE IF NOT EXISTS `password_reset_token` (
                `id` BIGINT NOT NULL,
                `user_id` BIGINT NOT NULL,
                `token_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256(令牌) 十六进制',
                `expires_at` DATETIME NOT NULL,
                `used_at` DATETIME DEFAULT NULL,
                `request_ip` VARCHAR(64) DEFAULT NULL,
                `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (`id`),
                UNIQUE KEY `uk_reset_token_hash` (`token_hash`),
                KEY `idx_reset_user` (`user_id`)
            ) ENGINE=InnoDB COMMENT='密码重置令牌表'
            """;

    private static final String ADMIN_RECHARGE_USAGE_DDL = """
            CREATE TABLE IF NOT EXISTS `admin_recharge_monthly_usage` (
                `id` BIGINT NOT NULL,
                `operator_id` BIGINT NOT NULL COMMENT '操作管理员ID',
                `user_id` BIGINT NOT NULL COMMENT '被充值用户ID',
                `month_key` VARCHAR(7) NOT NULL COMMENT '自然月 yyyy-MM',
                `granted_points` BIGINT NOT NULL DEFAULT 0 COMMENT '本月已充值点数',
                `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (`id`),
                UNIQUE KEY `uk_admin_recharge_month` (`operator_id`, `user_id`, `month_key`)
            ) ENGINE=InnoDB COMMENT='管理员月度充值限额使用表'
            """;

    private boolean hasTable(DatabaseMetaData metaData, String catalog, String tableName) {
        for (String name : new String[]{tableName, tableName.toUpperCase()}) {
            try (ResultSet rs = metaData.getTables(catalog, null, name, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
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

    private boolean hasIndex(DatabaseMetaData metaData, String catalog, String tableName, String indexName) {
        for (String name : new String[]{tableName, tableName.toUpperCase()}) {
            try (ResultSet rs = metaData.getIndexInfo(catalog, null, name, false, false)) {
                while (rs.next()) {
                    String current = rs.getString("INDEX_NAME");
                    if (current != null && current.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void executeDdl(Connection conn, String ddl) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (Exception e) {
            log.warn("执行表结构补齐 DDL 提示: [{}], 详情: {}", ddl, e.getMessage());
        }
    }
}
