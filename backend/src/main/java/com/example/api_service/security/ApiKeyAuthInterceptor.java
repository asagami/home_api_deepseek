package com.example.api_service.security;

import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PointCostCalculator;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.SysUserService;
import com.example.api_service.service.UserApiPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 开放接口鉴权拦截器，仅拦截 /api/v1/**。
 *
 * <p>校验顺序：X-API-Key 是否存在 → SHA-256 命中密钥 → 密钥状态与有效期 →
 * 账户状态 → 单 Key QPS 限流 → 匹配服务目录 → 校验接口权限 →
 * 按专属折扣计算实际点数 → 账户剩余额度。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final int DEFAULT_QPS = 1;
    private static final int ENABLED = 1;
    private static final int PUBLISHED = 1;

    private final ApiKeyInfoService apiKeyInfoService;
    private final SysUserService sysUserService;
    private final ApiEndpointMetaService apiEndpointMetaService;
    private final UserApiPermissionService userApiPermissionService;
    private final AuthResponseWriter responseWriter;
    private final RedisRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKey)) {
            responseWriter.write(response, ErrorCode.API_KEY_MISSING,
                    "缺少 X-API-Key 请求头");
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

        int qps = keyInfo.getRateLimitQps() == null || keyInfo.getRateLimitQps() <= 0
                ? DEFAULT_QPS
                : keyInfo.getRateLimitQps();
        // 基于 Redis 的分布式 QPS 限流，跨实例共享
        String bucket = "apikey:qps:" + keyInfo.getId();
        if (!rateLimiter.allowFixedWindow(bucket, qps, Duration.ofSeconds(1))) {
            log.debug("API Key 触发限流: keyId={}, qps={}", keyInfo.getId(), qps);
            responseWriter.write(response, ErrorCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，当前 Key 限制为 " + qps + " QPS");
            return false;
        }

        ApiEndpointMeta endpoint = apiEndpointMetaService.getByPathAndMethod(
                normalizePath(request.getRequestURI()), request.getMethod());
        if (endpoint == null) {
            responseWriter.write(response, ErrorCode.ENDPOINT_NOT_FOUND,
                    "接口不存在或未在服务目录中登记");
            return false;
        }
        // 未发布（下线）的接口统一返回「API 已下线」，与动态网关保持一致
        if (!Integer.valueOf(PUBLISHED).equals(endpoint.getStatus())) {
            responseWriter.write(response, ErrorCode.API_OFFLINE,
                    "API 已下线，暂时无法调用");
            return false;
        }

        UserApiPermission permission = userApiPermissionService.getApprovedPermission(
                user.getId(), endpoint.getId());
        if (permission == null) {
            responseWriter.write(response, ErrorCode.API_PERMISSION_DENIED,
                    "未获得该接口的调用权限，请先在控制台提交申请");
            return false;
        }

        int costPoints = PointCostCalculator.actualCost(
                endpoint.getCostPerCall(), permission.getCustomDiscount());
        Long balanceQuota = user.getBalanceQuota();
        if (balanceQuota == null || balanceQuota < costPoints) {
            responseWriter.write(response, ErrorCode.QUOTA_INSUFFICIENT,
                    "账户剩余额度不足，本次调用需要 " + costPoints + " 点");
            return false;
        }

        ApiKeyContext.set(new ApiKeyPrincipal(
                user.getId(), keyInfo.getId(), keyInfo.getKeyPrefix(), qps,
                endpoint.getId(), costPoints));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        ApiKeyContext.clear();
    }

    /** 去掉尾部斜杠，保证 /api/v1/foo/ 与目录中的 /api/v1/foo 匹配到同一条记录。 */
    private String normalizePath(String path) {
        String normalized = path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
