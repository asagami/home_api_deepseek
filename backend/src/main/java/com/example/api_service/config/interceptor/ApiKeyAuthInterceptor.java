package com.example.api_service.config.interceptor;

import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.billing.QuotaLedger;
import com.example.api_service.engine.context.ApiContext;
import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.security.ApiKeyUtils;
import com.example.api_service.security.AuthResponseWriter;
import com.example.api_service.security.ClientIpUtils;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.SysUserService;
import com.example.api_service.service.UserApiPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 开放服务轨道统一拦截器，仅拦截 {@code /api/v1/**}。
 *
 * <p>前置阶段完成协议约束、API Key 验签、QPS 限流、服务目录与权限校验，
 * 并把计费所需信息写入 {@link ApiContext}，同时注入 {@link ApiContext.BillingCallback}
 * 供业务模板在执行前完成预扣费。请求完成后根据 Handler 的 billable 标记异步落调用日志——
 * 扣费已在业务执行前完成，此处不再重复扣减。</p>
 */
@Slf4j
@Component("gatewayApiKeyAuthInterceptor")
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String START_TIME_ATTRIBUTE =
            ApiKeyAuthInterceptor.class.getName() + ".startNanos";
    private static final String ENDPOINT_PATH_ATTRIBUTE =
            ApiKeyAuthInterceptor.class.getName() + ".endpointPath";

    private static final int DEFAULT_QPS = 1;
    private static final int ENABLED = 1;
    private static final int PUBLISHED = 1;

    private final ApiKeyInfoService apiKeyInfoService;
    private final SysUserService sysUserService;
    private final ApiEndpointMetaService apiEndpointMetaService;
    private final UserApiPermissionService userApiPermissionService;
    private final ApiAccessLogService apiAccessLogService;
    private final AuthResponseWriter responseWriter;
    private final TaskExecutor taskExecutor;
    private final RedisRateLimiter rateLimiter;
    private final ClientIpUtils clientIpUtils;
    private final QuotaLedger quotaLedger;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());

        // 协议铁律：开放接口只接受 POST
        if (!HttpMethod.POST.matches(request.getMethod())) {
            responseWriter.write(response, ErrorCode.METHOD_NOT_ALLOWED,
                    "开放接口仅支持 POST 请求");
            return false;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKey)) {
            responseWriter.write(response, ErrorCode.API_KEY_MISSING, "缺少 X-API-Key 请求头");
            return false;
        }

        ApiKeyInfo keyInfo = apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(apiKey.trim()));
        if (keyInfo == null) {
            responseWriter.write(response, ErrorCode.API_KEY_INVALID, "无效的 API Key");
            return false;
        }
        if (!Integer.valueOf(ENABLED).equals(keyInfo.getStatus())) {
            responseWriter.write(response, ErrorCode.API_KEY_DISABLED, "API Key 已被禁用");
            return false;
        }
        if (keyInfo.getExpiredTime() != null && !keyInfo.getExpiredTime().isAfter(LocalDateTime.now())) {
            responseWriter.write(response, ErrorCode.API_KEY_EXPIRED, "API Key 已过期");
            return false;
        }

        SysUser user = sysUserService.getById(keyInfo.getUserId());
        if (user == null || !Integer.valueOf(ENABLED).equals(user.getStatus())) {
            responseWriter.write(response, ErrorCode.ACCOUNT_DISABLED, "账户不存在或已被封禁");
            return false;
        }

        if (!acquireRateLimit(keyInfo)) {
            responseWriter.write(response, ErrorCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，当前 Key 限制为 " + resolveQps(keyInfo) + " QPS");
            return false;
        }

        String path = normalizePath(request.getRequestURI());
        ApiEndpointMeta endpoint = apiEndpointMetaService.getByPathAndMethod(
                path, HttpMethod.POST.name());
        if (endpoint == null || !Integer.valueOf(PUBLISHED).equals(endpoint.getStatus())) {
            responseWriter.write(response, ErrorCode.ENDPOINT_NOT_FOUND,
                    "接口不存在、未上线或未在服务目录中登记");
            return false;
        }

        UserApiPermission permission = userApiPermissionService.getApprovedPermission(
                user.getId(), endpoint.getId());
        if (permission == null) {
            responseWriter.write(response, ErrorCode.API_PERMISSION_DENIED,
                    "未获得该接口的调用权限，请先在控制台提交申请");
            return false;
        }

        ApiContext context = new ApiContext();
        context.setUserId(user.getId());
        context.setApiKeyId(keyInfo.getId());
        context.setEndpointId(endpoint.getId());
        context.setBaseCostPerCall(endpoint.getCostPerCall() == null ? 0 : endpoint.getCostPerCall());
        context.setCustomDiscount(permission.getCustomDiscount() == null
                ? 1.0d
                : permission.getCustomDiscount().doubleValue());
        context.setUserBalanceQuota(user.getBalanceQuota() == null ? 0L : user.getBalanceQuota());
        context.setBillingCallback(buildBillingCallback(user.getId()));
        ApiContext.set(context);

        request.setAttribute(ENDPOINT_PATH_ATTRIBUTE, endpoint.getPath());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            ApiContext context = ApiContext.get();
            if (context != null && context.isBillable()) {
                settleBilling(context, request, response);
            }
        } catch (Exception settleException) {
            log.error("开放接口计量失败: uri={}", request.getRequestURI(), settleException);
        } finally {
            ApiContext.clear();
        }
    }

    /**
     * 异步记账 + 落账。Redis 预扣已在业务模板 {@code execute()} 阶段完成，此方法负责：
     * <ol>
     *   <li>将本次扣费结果增量刷回 MySQL（{@link QuotaLedger#settle}），让持久账本与 Redis 一致；</li>
     *   <li>将实际消费写入 {@link ApiAccessLog}。</li>
     * </ol>
     * 必须在调用 {@link ApiContext#clear()} 之前读取完上下文数据。
     */
    private void settleBilling(ApiContext context, HttpServletRequest request,
                               HttpServletResponse response) {
        Long userId = context.getUserId();
        Long apiKeyId = context.getApiKeyId();
        if (userId == null || apiKeyId == null) {
            log.error("计费上下文缺少用户或 Key 信息，跳过记账: userId={}, apiKeyId={}",
                    userId, apiKeyId);
            return;
        }

        int chargedCost = context.getChargedCost();
        QuotaLedger.LedgerMode ledgerMode = context.getLedgerMode();

        ApiAccessLog accessLog = new ApiAccessLog();
        accessLog.setUserId(userId);
        accessLog.setApiKeyId(apiKeyId);
        accessLog.setEndpointPath(resolveEndpointPath(request));
        accessLog.setHttpStatus(response.getStatus());
        accessLog.setCostPoints(chargedCost);
        accessLog.setLatencyMs((int) Math.min(resolveLatencyMs(context, request), Integer.MAX_VALUE));
        accessLog.setClientIp(clientIpUtils.resolve(request));

        writeAccessLogAsync(accessLog, () -> quotaLedger.settle(userId, chargedCost, ledgerMode));
    }

    /**
     * 构建计费回调：预扣费走 {@link QuotaLedger#preDeduct}（Redis Lua 原子预扣，余额不足返回 false），
     * 退款走 {@link QuotaLedger#compensate}（Redis incrby 补偿）。回调捕获 userId，与 ThreadLocal
     * {@link ApiContext} 一一对应，线程安全；落账模式由 {@code charge} 回写到上下文。
     */
    private ApiContext.BillingCallback buildBillingCallback(Long userId) {
        return new ApiContext.BillingCallback() {
            @Override
            public boolean charge(int cost) {
                QuotaLedger.ChargeResult result = quotaLedger.preDeduct(userId, cost);
                ApiContext context = ApiContext.get();
                if (context != null) {
                    context.setLedgerMode(result.mode());
                }
                return result.success();
            }

            @Override
            public void refund(int cost) {
                ApiContext context = ApiContext.get();
                QuotaLedger.LedgerMode mode = context != null ? context.getLedgerMode() : null;
                quotaLedger.compensate(userId, cost, mode);
            }
        };
    }

    private void writeAccessLogAsync(ApiAccessLog accessLog, Runnable settleAction) {
        try {
            taskExecutor.execute(() -> {
                try {
                    settleAction.run();
                } catch (Exception ex) {
                    log.error("异步 settle 失败: userId={}, path={}",
                            accessLog.getUserId(), accessLog.getEndpointPath(), ex);
                }
                try {
                    apiAccessLogService.save(accessLog);
                } catch (Exception ex) {
                    log.error("异步写入 API 调用日志失败: userId={}, path={}",
                            accessLog.getUserId(), accessLog.getEndpointPath(), ex);
                }
            });
        } catch (Exception ex) {
            log.error("提交异步日志任务失败: userId={}, path={}",
                    accessLog.getUserId(), accessLog.getEndpointPath(), ex);
        }
    }

    /**
     * 基于 Redis 的分布式 QPS 限流，跨实例共享同一令牌桶。
     * 使用 1 秒固定窗口：同一秒内累计计数，超出 QPS 即拒绝。
     */
    private boolean acquireRateLimit(ApiKeyInfo keyInfo) {
        int qps = resolveQps(keyInfo);
        String bucket = "apikey:qps:" + keyInfo.getId();
        boolean allowed = rateLimiter.allowFixedWindow(bucket, qps, Duration.ofSeconds(1));
        if (!allowed) {
            log.debug("API Key 触发限流: keyId={}, qps={}", keyInfo.getId(), qps);
        }
        return allowed;
    }

    private int resolveQps(ApiKeyInfo keyInfo) {
        Integer configuredQps = keyInfo.getRateLimitQps();
        return configuredQps == null || configuredQps <= 0 ? DEFAULT_QPS : configuredQps;
    }

    private long resolveLatencyMs(ApiContext context, HttpServletRequest request) {
        long executionTimeMs = context.getExecutionTimeMs();
        if (executionTimeMs > 0) {
            return executionTimeMs;
        }
        Object startNanos = request.getAttribute(START_TIME_ATTRIBUTE);
        if (startNanos instanceof Long start) {
            return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        }
        return 0L;
    }

    private String resolveEndpointPath(HttpServletRequest request) {
        Object endpointPath = request.getAttribute(ENDPOINT_PATH_ATTRIBUTE);
        return endpointPath instanceof String path
                ? path
                : normalizePath(request.getRequestURI());
    }

    /** 去掉尾部斜杠，保证 /api/v1/foo/ 与目录中的 /api/v1/foo 匹配到同一条记录。 */
    private static String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
