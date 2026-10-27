package com.example.api_service.engine;

import com.example.api_service.common.PageResult;
import com.example.api_service.engine.external.ExternalSqlQueryExecutor;
import com.example.api_service.model.dto.BasePageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

/**
 * 外部异构数据库 (PostgreSQL, Oracle, 外部 MySQL 等) 只读查询业务模板。
 *
 * <p>继承自 {@link AbstractApiHandler}，天然享受以下网关能力：</p>
 * <ul>
 *     <li>API Key 权限校验与 QPS 限流</li>
 *     <li>按分页大小自动计算计费倍率与原子预扣费</li>
 *     <li>执行异常（如外部库断连或超时）时<b>全额退款、不记账</b></li>
 *     <li>耗时与状态统计回写</li>
 * </ul>
 *
 * @param <Req>  请求入参类型 (分页时继承 {@link BasePageRequest})
 * @param <Resp> 响应出参类型 (分页时通常为 {@code PageResult<VO>})
 */
public abstract class AbstractExternalSqlApiHandler<Req, Resp> extends AbstractApiHandler<Req, Resp> {

    @Autowired
    private ExternalSqlQueryExecutor queryExecutor;

    /**
     * 子类指定目标外部数据源 Key (如 "pg-local", "oracle_erp")。
     */
    protected abstract String getTargetDataSourceKey();

    /**
     * 执行外部异构库安全分页查询。
     *
     * @param baseSql   业务 SQL（SELECT ... WHERE ...）
     * @param paramMap  命名参数 Map (对应 SQL 中的 :paramName)
     * @param pageReq   安全分页请求对象
     * @param rowMapper 结果行映射器
     * @param <R>       单条记录 VO 类型
     * @return 标准统一分页报文
     */
    protected <R> PageResult<R> queryForPage(String baseSql,
                                             Map<String, Object> paramMap,
                                             BasePageRequest pageReq,
                                             RowMapper<R> rowMapper) {
        return queryExecutor.executePage(
                getTargetDataSourceKey(),
                baseSql,
                paramMap,
                pageReq.getSafePageNum(),
                pageReq.getSafePageSize(),
                rowMapper
        );
    }

    /**
     * 执行外部库列表查询 (非分页)。
     */
    protected <R> List<R> queryForList(String baseSql,
                                       Map<String, Object> paramMap,
                                       RowMapper<R> rowMapper) {
        return queryExecutor.executeList(getTargetDataSourceKey(), baseSql, paramMap, rowMapper);
    }
}
