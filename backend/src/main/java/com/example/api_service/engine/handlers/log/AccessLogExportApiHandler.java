package com.example.api_service.engine.handlers.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.engine.AbstractSqlApiHandler;
import com.example.api_service.engine.context.ApiContext;
import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.service.ApiAccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SQL 分页查询标杆示例：查询当前用户的调用日志。
 *
 * <p>请求继承 {@link com.example.api_service.model.dto.BasePageRequest}，
 * 因此 {@code AbstractApiHandler} 会按 pageSize 自动写入计费倍率，
 * 实现“每 20 条一个计费单元”的阶梯扣费。</p>
 */
@Component
@RequiredArgsConstructor
public class AccessLogExportApiHandler
        extends AbstractSqlApiHandler<LogQueryPageReq, PageResult<AccessLogOpenVO>> {

    private final ApiAccessLogService apiAccessLogService;

    @Override
    public String getEndpointPath() {
        return "/api/v1/data/my-logs";
    }

    @Override
    protected PageResult<AccessLogOpenVO> doExecute(LogQueryPageReq request) {
        Long userId = ApiContext.getRequired().getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少调用者用户信息");
        }

        Page<ApiAccessLog> pageRequest = buildMpPage(request);
        Page<ApiAccessLog> result = apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, userId)
                .eq(request.getHttpStatus() != null,
                        ApiAccessLog::getHttpStatus, request.getHttpStatus())
                .orderByDesc(ApiAccessLog::getCreateTime)
                .page(pageRequest);

        return toPageResult(result, AccessLogOpenVO::from);
    }
}
