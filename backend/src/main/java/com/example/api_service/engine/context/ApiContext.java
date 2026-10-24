package com.example.api_service.engine.context;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;

/**
 * 当前 OpenAPI 请求的计费上下文。
 *
 * <p>由开放网关拦截器在鉴权、限流、权限校验与额度预检通过后写入，
 * 请求结束时必须调用 {@link #clear()} 释放 ThreadLocal，避免线程池复用导致
 * 上下文串号。业务 Handler 只有在真正执行成功后才允许 {@link #markBillable(boolean)}
 * 置为 true，服务端故障时绝不扣减用户点数。</p>
 */
public final class ApiContext {

    private static final ThreadLocal<ApiContext> HOLDER = new ThreadLocal<>();

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

    /** 用户当前剩余点数，用于扣费前预检。 */
    private long userBalanceQuota;

    /** 计费倍率，默认 1；分页类接口按每 20 条一个计费单元累加。 */
    private int billingMultiplier = 1;

    /** 是否允许扣费，默认 false，仅业务执行成功后可置为 true。 */
    private boolean billable = false;

    /** 业务执行耗时（毫秒），用于写入调用日志。 */
    private long executionTimeMs;

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
}
