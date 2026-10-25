package com.example.api_service.engine.context;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.billing.QuotaLedger;

/**
 * 当前 OpenAPI 请求的计费上下文。
 *
 * <p>由开放网关拦截器在鉴权、限流、权限校验通过后写入，请求结束时必须调用
 * {@link #clear()} 释放 ThreadLocal，避免线程池复用导致上下文串号。</p>
 *
 * <p>计费采用<strong>Redis 原子预扣 + 状态补偿</strong>模型：拦截器在 preHandle 注入
 * {@link BillingCallback}，业务模板 {@code AbstractApiHandler.execute()} 在执行真实业务逻辑之前
 * 调用 {@link #charge(int)} 完成 Redis Lua 原子预扣——只有预扣成功的请求才会继续执行，
 * 从根本上消除并发下基于内存快照的"白嫖"。业务成功后由拦截器异步将扣费结果增量刷回 MySQL；
 * 若业务逻辑抛出异常，模板会调用 {@link #refund()} 在 Redis 中返还预扣额度（补偿），
 * 保证服务端故障不扣费。{@link #ledgerMode} 记录本次预扣的落账轨道，供补偿与异步 settle 决策。</p>
 */
public final class ApiContext {

    private static final ThreadLocal<ApiContext> HOLDER = new ThreadLocal<>();

    /**
     * 计费回调，由网关拦截器注入，封装真正的数据库原子扣减 / 退款操作。
     *
     * <p>将计费服务（{@code SysUserService}）与业务 Handler 解耦：Handler 只通过
     * {@link ApiContext#charge(int)} / {@link ApiContext#refund()} 触发计费，
     * 无需感知具体的扣费实现。</p>
     */
    public interface BillingCallback {

        /**
         * 原子预扣费。
         *
         * @param cost 应扣点数（已为正数）
         * @return true 扣减成功；false 余额不足或账户不可用
         */
        boolean charge(int cost);

        /**
         * 退还已预扣的点数，仅在业务执行失败时调用。
         *
         * @param cost 退还点数（与 {@link #charge(int)} 成功时传入的值一致）
         */
        void refund(int cost);
    }

    /** 调用方用户 ID。 */
    private Long userId;

    /** 本次调用使用的 API Key ID。 */
    private Long apiKeyId;

    /** 命中的接口目录 ID。 */
    private Long endpointId;

    /** 接口基础点数。 */
    private int baseCostPerCall;

    /** 用户专属折扣率，默认 1.0 表示原价。 */
    private double customDiscount = 1.0d;

    /** 用户当前剩余点数快照（仅用于日志/调试，不再作为扣费判定依据）。 */
    private long userBalanceQuota;

    /** 计费倍率，默认 1；分页类接口按每 20 条一个计费单元累加。 */
    private int billingMultiplier = 1;

    /** 是否允许记账，默认 false，仅业务执行成功后可置为 true。 */
    private boolean billable = false;

    /** 业务执行耗时（毫秒），用于写入调用日志。 */
    private long executionTimeMs;

    /** 计费回调，由拦截器注入；为 null 时（测试场景）跳过真实扣费。 */
    private BillingCallback billingCallback;

    /**
     * 本次预扣的落账轨道，由 {@link BillingCallback#charge(int)} 在扣费时回写。
     * 免费接口（未触发回调）为 null；{@link #refund()} / 拦截器异步 settle 据此选择 Redis 或 MySQL。
     */
    private QuotaLedger.LedgerMode ledgerMode;

    /** 已通过 {@link #charge(int)} 预扣的点数，用于失败退款与调用日志记账。 */
    private int chargedCost = 0;

    public ApiContext() {
    }

    /**
     * 绑定当前线程的请求上下文。
     */
    public static void set(ApiContext context) {
        HOLDER.set(context);
    }

    /**
     * 获取当前线程的请求上下文，不存在时返回 null。
     */
    public static ApiContext get() {
        return HOLDER.get();
    }

    /**
     * 获取当前线程的请求上下文，不存在时视为未认证。
     */
    public static ApiContext getRequired() {
        ApiContext context = HOLDER.get();
        if (context == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return context;
    }

    /**
     * 当前线程是否已绑定请求上下文。
     */
    public static boolean isPresent() {
        return HOLDER.get() != null;
    }

    /**
     * 标记本次调用是否允许扣费。仅在业务 Handler 正常返回后置为 true。
     */
    public static void markBillable(boolean billable) {
        getRequired().billable = billable;
    }

    /**
     * 记录本次业务执行耗时。
     */
    public static void markExecutionTime(long executionTimeMs) {
        getRequired().executionTimeMs = Math.max(0L, executionTimeMs);
    }

    /**
     * 计算最终扣费点数：
     * {@code max(0, ceil(baseCostPerCall * customDiscount * billingMultiplier))}。
     *
     * @return 最终应扣点数；上下文不存在时返回 0
     */
    public static int calculateFinalCost() {
        ApiContext context = HOLDER.get();
        if (context == null) {
            return 0;
        }
        double cost = context.baseCostPerCall * context.customDiscount * context.billingMultiplier;
        return Math.max(0, (int) Math.ceil(cost));
    }

    /**
     * 预扣费：在执行业务逻辑之前完成 Redis 层面的原子扣减。
     *
     * <p>这是并发安全的唯一扣费入口——多个并发请求各自调用此方法，
     * 回调内部走 Redis Lua 原子 {@code decrby}（Redis 不可达时降级为 MySQL 条件 UPDATE），
     * 保证只有余额真正充足的请求才能扣减成功，其余请求收到 false 并被拒绝，不会执行业务逻辑。
     * 回调同时在上下文回写 {@link #ledgerMode}，供失败退款与异步 settle 选择落账轨道。</p>
     *
     * <p>扣费成功后记录已扣金额，供 {@link #refund()} 退款与调用日志记账使用。
     * 免费接口（cost &le; 0）直接返回 true，不触发回调，{@link #ledgerMode} 保持 null。
     * 无回调注入时（测试场景）也直接返回 true。</p>
     *
     * @param cost 应扣点数
     * @return true 扣减成功或无需扣费；false 余额不足或账户不可用
     */
    public boolean charge(int cost) {
        if (cost <= 0 || billingCallback == null) {
            return true;
        }
        if (billingCallback.charge(cost)) {
            chargedCost = cost;
            return true;
        }
        return false;
    }

    /**
     * 退还已预扣的点数（补偿）。仅在业务逻辑执行失败时由模板调用。
     *
     * <p>REDIS 模式下在 Redis 中 {@code incrby} 返还；MYSQL_FALLBACK 模式下走 MySQL 加点。
     * 若 {@link #charge(int)} 未成功扣费（{@code chargedCost == 0}），此方法为空操作，
     * 因此可安全地在所有异常路径上无条件调用。</p>
     */
    public void refund() {
        if (chargedCost > 0 && billingCallback != null) {
            billingCallback.refund(chargedCost);
            chargedCost = 0;
        }
    }

    /**
     * 解除当前线程绑定，必须在请求结束时调用。
     */
    public static void clear() {
        HOLDER.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public Long getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(Long endpointId) {
        this.endpointId = endpointId;
    }

    public int getBaseCostPerCall() {
        return baseCostPerCall;
    }

    public void setBaseCostPerCall(int baseCostPerCall) {
        this.baseCostPerCall = Math.max(0, baseCostPerCall);
    }

    public double getCustomDiscount() {
        return customDiscount;
    }

    public void setCustomDiscount(double customDiscount) {
        this.customDiscount = customDiscount;
    }

    public long getUserBalanceQuota() {
        return userBalanceQuota;
    }

    public void setUserBalanceQuota(long userBalanceQuota) {
        this.userBalanceQuota = userBalanceQuota;
    }

    public int getBillingMultiplier() {
        return billingMultiplier;
    }

    public void setBillingMultiplier(int billingMultiplier) {
        this.billingMultiplier = billingMultiplier < 1 ? 1 : billingMultiplier;
    }

    public boolean isBillable() {
        return billable;
    }

    public void setBillable(boolean billable) {
        this.billable = billable;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = Math.max(0L, executionTimeMs);
    }

    public BillingCallback getBillingCallback() {
        return billingCallback;
    }

    public void setBillingCallback(BillingCallback billingCallback) {
        this.billingCallback = billingCallback;
    }

    public QuotaLedger.LedgerMode getLedgerMode() {
        return ledgerMode;
    }

    public void setLedgerMode(QuotaLedger.LedgerMode ledgerMode) {
        this.ledgerMode = ledgerMode;
    }

    /**
     * 已预扣的点数。业务成功时即为调用日志应记录的实际消费；为 0 表示未扣费。
     */
    public int getChargedCost() {
        return chargedCost;
    }
}
