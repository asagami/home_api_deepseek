package com.example.api_service.engine.handlers.mysqlqry;

import com.example.api_service.common.PageResult;
import com.example.api_service.engine.AbstractSqlApiHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * mysql用户分页查询
 */
@Component
@RequiredArgsConstructor
public class PgMysqlqryApiHandler extends AbstractSqlApiHandler<PgMysqlqryPageReq, PageResult<PgMysqlqryOpenVO>> {

    @Override
    public String getEndpointPath() {
        return "/api/v1/pg/mysqlqry";
    }

    @Override
    protected PageResult<PgMysqlqryOpenVO> doExecute(PgMysqlqryPageReq request) {
        // >>>>> AUTO_SQL_TEMPLATE_START >>>>>
        String sql = """
            select a,b from c where 1=1
                """;
        // <<<<< AUTO_SQL_TEMPLATE_END <<<<<

        // TODO: 注入并调用本地 MyBatis-Plus Mapper 进行业务查询
        throw new UnsupportedOperationException("请注入对应本地 Mapper 完成查询");
    }
}
