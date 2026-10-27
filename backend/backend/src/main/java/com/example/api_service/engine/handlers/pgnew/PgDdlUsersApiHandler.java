package com.example.api_service.engine.handlers.pgnew;

import com.example.api_service.common.PageResult;
import com.example.api_service.engine.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * PostgreSQL用户分页查询
 */
@Component
@RequiredArgsConstructor
public class PgDdlUsersApiHandler extends AbstractExternalSqlApiHandler<PgDdlUsersPageReq, PageResult<PgDdlUsersOpenVO>> {

    @Override
    public String getEndpointPath() {
        return "/api/v1/pg/ddl-users";
    }

    @Override
    protected String getTargetDataSourceKey() {
        return "pg-local";
    }

    @Override
    protected PageResult<PgDdlUsersOpenVO> doExecute(PgDdlUsersPageReq request) throws Exception {
        String sql = "SELECT usesysid, usename, usesuper, valuntil FROM pg_catalog.pg_user WHERE 1=1";
        Map<String, Object> paramMap = new HashMap<>();
        // TODO: 按需根据 request 填充 paramMap 与拼接查询条件
        
        return queryForPage(sql, paramMap, request, (rs, rowNum) -> new PgDdlUsersOpenVO(
                // TODO: 映射字段
        ));
    }
}
