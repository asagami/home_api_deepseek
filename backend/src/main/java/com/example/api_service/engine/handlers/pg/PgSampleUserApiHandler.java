package com.example.api_service.engine.handlers.pg;

import com.example.api_service.common.PageResult;
import com.example.api_service.engine.AbstractExternalSqlApiHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * PostgreSQL 外部数据源分页查询标杆示例。
 * <p>
 * 目标库：外部本地 PostgreSQL (默认连接 pg-local)
 * 目标表：PostgreSQL 内置系统视图 pg_catalog.pg_user（天然存在于所有 PG 实例，无需建表即可验证）
 * </p>
 */
@Component
public class PgSampleUserApiHandler
        extends AbstractExternalSqlApiHandler<PgSampleUserPageReq, PageResult<PgSampleUserOpenVO>> {

    @Override
    public String getEndpointPath() {
        return "/api/v1/pg/sample-users";
    }

    @Override
    protected String getTargetDataSourceKey() {
        return "pg-local";
    }

    @Override
    protected PageResult<PgSampleUserOpenVO> doExecute(PgSampleUserPageReq request) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                CAST(usesysid AS text) AS user_id,
                usename AS username,
                usesuper AS is_superuser,
                usecatupd AS can_update_catalog,
                CAST(valuntil AS text) AS valid_until
            FROM pg_catalog.pg_user
            WHERE 1=1
            """);

        Map<String, Object> paramMap = new HashMap<>();

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            sql.append(" AND usename LIKE :username");
            paramMap.put("username", "%" + request.getUsername().trim() + "%");
        }

        if (request.getSuperuser() != null) {
            sql.append(" AND usesuper = :superuser");
            paramMap.put("superuser", request.getSuperuser());
        }

        sql.append(" ORDER BY usesysid ASC");

        return queryForPage(sql.toString(), paramMap, request, (rs, rowNum) -> new PgSampleUserOpenVO(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getObject("is_superuser") != null && rs.getBoolean("is_superuser"),
                rs.getObject("can_update_catalog") != null && rs.getBoolean("can_update_catalog"),
                rs.getString("valid_until")
        ));
    }
}
