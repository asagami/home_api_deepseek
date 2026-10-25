package com.example.api_service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的分布式限流工具，所有计数器跨实例共享。
 *
 * <p>提供两种语义：</p>
 * <ul>
 *   <li>{@link #allowFixedWindow} —— 固定窗口计数器，适用于 QPS / 每分钟请求数等频率限制；
 *       窗口按 epoch 对齐，同一窗口内累计计数，超出阈值即拒绝。</li>
 *   <li>{@link #incrementWithTtl} / {@link #getCount} / {@link #reset} —— 带 TTL 的通用计数器，
 *       适用于登录失败锁定、验证码尝试次数等场景。</li>
 * </ul>
 *
 * <p>Redis 不可达时 fail-open（放行请求并告警），避免限流组件故障拖垮整个服务；
 * 安全关键路径（登录锁定、验证码）在上层仍有独立校验。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 固定窗口限流：在当前窗口内累计计数，返回是否放行。
     *
     * @param bucket 计数桶标识（如 {@code "console:auth:ip:1.2.3.4"}）
     * @param limit  窗口内允许的最大请求数
     * @param window 窗口时长
     * @return {@code true} 表示当前请求在配额内，应放行
     */
    public boolean allowFixedWindow(String bucket, int limit, Duration window) {
        long windowMillis = window.toMillis();
        long windowId = System.currentTimeMillis() / windowMillis;
        String key = PREFIX + bucket + ":" + windowId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 窗口结束后保留一个窗口的缓冲期再过期，避免边界竞态
                redisTemplate.expire(key, window.multipliedBy(2));
            }
            return count != null && count <= limit;
        } catch (Exception ex) {
            log.warn("限流计数器 Redis 不可达，fail-open 放行: bucket={}", bucket, ex);
            return true;
        }
    }

    /**
     * 递增带 TTL 的计数器，首次递增时设置 TTL（后续递增不延长 TTL）。
     *
     * @return 递增后的计数值
     */
    public long incrementWithTtl(String bucket, Duration ttl) {
        String key = PREFIX + bucket;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.warn("计数器 Redis 不可达，返回 0: bucket={}", bucket, ex);
            return 0L;
        }
    }

    /**
     * 获取当前计数值。
     */
    public long getCount(String bucket) {
        try {
            String val = redisTemplate.opsForValue().get(PREFIX + bucket);
            return val == null ? 0L : Long.parseLong(val);
        } catch (Exception ex) {
            log.warn("计数器 Redis 不可达，返回 0: bucket={}", bucket, ex);
            return 0L;
        }
    }

    /**
     * 重置计数器。
     */
    public void reset(String bucket) {
        try {
            redisTemplate.delete(PREFIX + bucket);
        } catch (Exception ex) {
            log.warn("计数器 Redis 不可达，跳过重置: bucket={}", bucket, ex);
        }
    }
}
