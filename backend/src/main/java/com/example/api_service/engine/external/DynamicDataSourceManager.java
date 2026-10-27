package com.example.api_service.engine.external;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部异构数据源动态连接池管理器。
 * <p>
 * 负责管理所有非本系统主数据库的连接池生命周期。
 * 强制在连接池层面配置 readOnly = true，并设置快速连接超时，避免外部库宕机拖垮网关。
 * </p>
 */
@Slf4j
@Component
public class DynamicDataSourceManager {

    private final Map<String, DataSourceHolder> poolMap = new ConcurrentHashMap<>();

    @Value("${app.datasource.external.pg-local.url:jdbc:postgresql://localhost:5432/postgres?ApplicationName=OpenApiPlatform}")
    private String defaultPgUrl;

    @Value("${app.datasource.external.pg-local.username:postgres}")
    private String defaultPgUsername;

    @Value("${app.datasource.external.pg-local.password:postgres}")
    private String defaultPgPassword;

    @PostConstruct
    public void init() {
        // 初始化默认的本地 PostgreSQL 外部数据源 (pg-local)
        try {
            registerDataSource("pg-local", "本地 PostgreSQL", "POSTGRESQL",
                    defaultPgUrl, defaultPgUsername, defaultPgPassword, 10, 5);
            log.info("外部数据源管理器初始化完成，已载入默认 PG 数据源 [pg-local]");
        } catch (Exception e) {
            log.warn("默认 PG 数据源预注册发生异常 (可能本地暂无对应驱动或网络未就绪): {}", e.getMessage());
        }
    }

    public synchronized void registerDataSource(String key, String name, String dbType,
                                               String jdbcUrl, String username, String password,
                                               int maxPoolSize, int queryTimeoutSec) {
        // 若已存在则先安全关闭老连接池
        DataSourceHolder existing = poolMap.remove(key);
        if (existing != null && existing.getDataSource() != null && !existing.getDataSource().isClosed()) {
            existing.getDataSource().close();
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("ExtPool-" + key);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true); // 重点：驱动层面强制只读
        config.setMaximumPoolSize(Math.max(2, maxPoolSize));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(3000); // 3 秒连接获取超时，防止拖死应用
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // 按类型适配 Driver
        if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
            config.setDriverClassName("org.postgresql.Driver");
        } else if ("MYSQL".equalsIgnoreCase(dbType)) {
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else if ("ORACLE".equalsIgnoreCase(dbType)) {
            config.setDriverClassName("oracle.jdbc.OracleDriver");
        }

        // 惰性构建 HikariDataSource
        HikariDataSource ds = new HikariDataSource(config);
        poolMap.put(key, new DataSourceHolder(key, name, dbType.toUpperCase(), jdbcUrl, ds, queryTimeoutSec));
        log.info("成功注册外部数据源: key={}, dbType={}, url={}", key, dbType, jdbcUrl);
    }

    public DataSourceHolder getHolder(String key) {
        DataSourceHolder holder = poolMap.get(key);
        if (holder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到已配置的外部数据源: " + key);
        }
        return holder;
    }

    public List<DataSourceHolder> listAllHolders() {
        return new ArrayList<>(poolMap.values());
    }

    @PreDestroy
    public void closeAll() {
        for (DataSourceHolder holder : poolMap.values()) {
            try {
                if (holder.getDataSource() != null && !holder.getDataSource().isClosed()) {
                    holder.getDataSource().close();
                }
            } catch (Exception e) {
                log.warn("关闭外部数据源连接池异常: {}", holder.getKey(), e);
            }
        }
        poolMap.clear();
    }

    @Getter
    public static class DataSourceHolder {
        private final String key;
        private final String name;
        private final String dbType;
        private final String jdbcUrl;
        private final HikariDataSource dataSource;
        private final int queryTimeoutSec;

        public DataSourceHolder(String key, String name, String dbType, String jdbcUrl,
                                HikariDataSource dataSource, int queryTimeoutSec) {
            this.key = key;
            this.name = name;
            this.dbType = dbType;
            this.jdbcUrl = jdbcUrl;
            this.dataSource = dataSource;
            this.queryTimeoutSec = queryTimeoutSec > 0 ? queryTimeoutSec : 5;
        }
    }
}
