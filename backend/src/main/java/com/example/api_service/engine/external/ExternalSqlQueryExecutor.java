package com.example.api_service.engine.external;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.engine.dialect.DbDialect;
import com.example.api_service.engine.dialect.DbDialectRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 外部异构数据库只读查询执行器。
 * <p>
 * 负责前置安全审计、方言分页包装、连接池调用与统一异常转换为网关契约。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalSqlQueryExecutor {

    private final DynamicDataSourceManager dataSourceManager;
    private final DbDialectRegistry dialectRegistry;
    private final SqlSecurityValidator sqlSecurityValidator;

    /**
     * 执行外部异构数据库分页查询。
     *
     * @param sourceKey   数据源唯一标识
     * @param originalSql 业务 SELECT SQL（含命名参数如 :username）
     * @param paramMap    查询参数映射
     * @param pageNum     页码 (>=1)
     * @param pageSize    每页大小
     * @param rowMapper   结果映射器
     * @param <R>         返回结果记录类型
     * @return 标准统一分页报文
     */
    public <R> PageResult<R> executePage(String sourceKey,
                                         String originalSql,
                                         Map<String, Object> paramMap,
                                         int pageNum,
                                         int pageSize,
                                         RowMapper<R> rowMapper) {
        // 1. 强制 AST 安全审计，严格只允许单条 SELECT
        sqlSecurityValidator.validateSelectOnly(originalSql);

        // 2. 获取数据源连接池与方言
        DynamicDataSourceManager.DataSourceHolder holder = dataSourceManager.getHolder(sourceKey);
        DbDialect dialect = dialectRegistry.getDialect(holder.getDbType());

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(holder.getDataSource());
        jdbcTemplate.getJdbcTemplate().setQueryTimeout(holder.getQueryTimeoutSec());

        Map<String, Object> safeParams = paramMap != null ? paramMap : Collections.emptyMap();

        try {
            // 3. 执行总条数统计
            String countSql = dialect.buildCountSql(originalSql);
            Long total = jdbcTemplate.queryForObject(countSql, safeParams, Long.class);
            if (total == null || total == 0L) {
                return PageResult.of(Collections.emptyList(), 0L, pageNum, pageSize);
            }

            // 4. 执行方言分页数据查询
            long offset = (long) (pageNum - 1) * pageSize;
            String pageSql = dialect.buildPageSql(originalSql, offset, pageSize);
            List<R> records = jdbcTemplate.query(pageSql, safeParams, rowMapper);

            return PageResult.of(records, total, pageNum, pageSize);
        } catch (DataAccessException dae) {
            log.error("外部数据源 [{}] 查询异常: sql={}, error={}", sourceKey, originalSql, dae.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "外部数据库 [" + holder.getName() + "] 查询失败，请检查目标数据库服务状态与连接配置: " + dae.getMostSpecificCause().getMessage(),
                    dae);
        } catch (Exception e) {
            log.error("外部数据源未知执行错误: sourceKey={}", sourceKey, e);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "外部数据源执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行单条或列表查询（非分页）。
     */
    public <R> List<R> executeList(String sourceKey,
                                   String originalSql,
                                   Map<String, Object> paramMap,
                                   RowMapper<R> rowMapper) {
        sqlSecurityValidator.validateSelectOnly(originalSql);
        DynamicDataSourceManager.DataSourceHolder holder = dataSourceManager.getHolder(sourceKey);

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(holder.getDataSource());
        jdbcTemplate.getJdbcTemplate().setQueryTimeout(holder.getQueryTimeoutSec());

        try {
            return jdbcTemplate.query(originalSql, paramMap != null ? paramMap : Collections.emptyMap(), rowMapper);
        } catch (Exception e) {
            log.error("外部数据源列表查询失败: sourceKey={}", sourceKey, e);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "外部数据源查询失败: " + e.getMessage(), e);
        }
    }
}
