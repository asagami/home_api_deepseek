package com.example.api_service.engine;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.Result;
import com.example.api_service.engine.context.ApiContext;
import com.example.api_service.model.dto.BasePageRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenAPI 业务执行模板。
 *
 * <p>所有 {@code /api/v1/**} 业务 Handler 的统一入口。模板负责计费倍率识别、
 * 预扣费、计时、异常兜底与退款，子类只关注参数校验和真实业务逻辑。</p>
 *
 * @param <Req>  请求 DTO
 * @param <Resp> 响应 VO；分页场景为 {@code PageResult<VO>}
 */
@Slf4j
public abstract class AbstractApiHandler<Req, Resp> {

    /**
     * 当前 Handler 对应的开放接口路径，需与服务目录 {@code api_endpoint_meta} 中登记的一致。
     */
    public abstract String getEndpointPath();

    /**
     * 真实业务逻辑。抛出异常即视为调用失败，模板会保证不扣费。
     */
    protected abstract Resp doExecute(Req request) throws Exception;

    /**
     * 参数校验钩子，默认不校验。子类可覆盖并在参数非法时抛出 {@link BusinessException}。
     */
    protected void validateParams(Req request) {
    }

    /**
     * 统一执行入口。
     *
     * <p>执行顺序：识别分页计费倍率 → <strong>预扣费</strong>（数据库原子扣减）→
     * 参数校验 → 业务执行 → 标记可记账。任一环节异常都会退还已扣点数并清除记账标记，
     * 通过 finally 回写耗时。</p>
     *
     * <p>预扣费在业务逻辑之前完成，是并发安全的唯一扣费点：多个并发请求各自调用
     * {@link ApiContext#charge(int)}，数据库条件 UPDATE 保证只有余额真正充足的请求
     * 才能扣减成功并继续执行，其余请求被直接拒绝，从根本上消除基于内存快照的并发"白嫖"。</p>
     */
    public final Result<Resp> execute(Req request) {
        long startNanos = System.nanoTime();
        ApiContext context = ApiContext.get();
        try {
            if (context == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED,
                        "缺少调用上下文，请通过开放网关访问该接口");
            }

            int billingMultiplier = request instanceof BasePageRequest pageRequest
                    ? pageRequest.getBillingMultiplier()
                    : 1;
            context.setBillingMultiplier(billingMultiplier);

            int finalCost = ApiContext.calculateFinalCost();
            if (!context.charge(finalCost)) {
                throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT,
                        "账户剩余点数不足以支付本次查询（需消耗 " + finalCost + " 点）");
            }

            validateParams(request);
            Resp data = doExecute(request);

            ApiContext.markBillable(true);
            return Result.success(data);
        } catch (BusinessException ex) {
            if (context != null) {
                context.refund();
                ApiContext.markBillable(false);
            }
            log.warn("接口业务异常: endpoint={}, code={}, message={}",
                    getEndpointPath(), ex.getErrorCode().getCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            if (context != null) {
                context.refund();
                ApiContext.markBillable(false);
            }
            log.error("接口执行失败: endpoint={}", getEndpointPath(), ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "接口执行失败，请稍后重试", ex);
        } finally {
            if (context != null) {
                context.setExecutionTimeMs((System.nanoTime() - startNanos) / 1_000_000L);
            }
        }
    }
}
