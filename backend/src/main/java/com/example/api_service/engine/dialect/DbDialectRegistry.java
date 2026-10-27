package com.example.api_service.engine.dialect;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库方言注册中心。
 */
@Component
public class DbDialectRegistry {

    private final Map<String, DbDialect> dialectMap = new ConcurrentHashMap<>();
    private final DbDialect defaultDialect;

    public DbDialectRegistry(List<DbDialect> dialects) {
        DbDialect fallback = null;
        for (DbDialect dialect : dialects) {
            String key = dialect.getDbType().toUpperCase();
            dialectMap.put(key, dialect);
            if ("MYSQL".equalsIgnoreCase(key)) {
                fallback = dialect;
            }
        }
        this.defaultDialect = fallback != null ? fallback : (dialects.isEmpty() ? null : dialects.getFirst());
    }

    public DbDialect getDialect(String dbType) {
        if (dbType == null) {
            return defaultDialect;
        }
        return dialectMap.getOrDefault(dbType.trim().toUpperCase(), defaultDialect);
    }
}
