package com.example.api_service.engine.external;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

/**
 * 外部 SQL 安全审计器。
 * <p>
 * 严格限制外部数据源查询仅允许单条 SELECT 语句，禁止任何 INSERT / UPDATE / DELETE / DROP / ALTER 等破坏性指令，
 * 并拦截多语句组合执行。
 * </p>
 */
@Component
public class SqlSecurityValidator {

    public void validateSelectOnly(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "SQL 语句不能为空");
        }

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.getStatements().size() != 1) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "安全审计拦截: 仅允许单条 SQL 语句执行，禁止多语句组合");
            }

            Statement statement = statements.getStatements().getFirst();
            if (!(statement instanceof Select)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "安全审计拦截: 外部数据源仅允许执行只读 SELECT 查询，拒绝非只读操作: " + statement.getClass().getSimpleName());
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "SQL 语法解析失败或包含不安全语法: " + e.getMessage(), e);
        }
    }
}
