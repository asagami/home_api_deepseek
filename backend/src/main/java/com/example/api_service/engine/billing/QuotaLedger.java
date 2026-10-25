package com.example.api_service.engine.billing;

import com.example.api_service.mapper.SysUserMapper;
import com.example.api_service.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 基于 Redis 的用户额度账本：开放网关计费的唯一权威热路径。
 *
 * <p>计费模型为<strong>Redis 原子预扣 + 状态补偿</strong>，彻底消除并发"白嫖"：</p>
 * <ul>
 *   <li>{@link #preDeduct} —— 业务 Handler 执行前，通过 Lua 脚本对
 *       {@code user:quota:{userId}} 原子 {@code decrby}；余额不足返回 -1 → 由业务模板转为 HTTP 402。
 *       多个并发请求各自走 Lua 原子扣减，从根本上消除基于内存快照的并发白嫖。</li>
 *   <li>{@link #compensate} —— 业务执行异常（如 504 上游超时）时，在 Redis 中 {@code incrby} 返还预扣额度。</li>
 *   <li>{@link #settle} —— 业务成功后异步执行，将本次扣费结果<strong>增量刷回 MySQL</strong>，
 *       让 MySQL 作为持久账本与 Redis 保持最终一致。</li>
 *   <li>{@link #credit} —— 充值 / 管理员加点入口：先落 MySQL，事务提交后再 incrby Redis，
 *       保证回滚不会让 Redis 多记一笔。</li>
 *   <li>{@link #invalidate} —— 遗留直连 MySQL 的扣费路径（{@code QuotaService.consume}）
 *       扣减后失效缓存，下次预扣重新从 MySQL 装载，避免双轨账本漂移。</li>
 * </ul>
 *
 * <p><strong>降级</strong>：Redis 不可达时 {@link #preDeduct} 降级为 MySQL 条件 UPDATE
 * （同样原子、不白嫖），并标记 {@link LedgerMode#MYSQL_FALLBACK}；后续 {@link #compensate}
 * / {@link #settle} 按该模式走 MySQL，保证降级期间账本仍自洽。Redis 恢复后若怀疑缓存键陈旧
 * （例如发生过降级扣费），运维可批量删除 {@code user:quota:*} 键触发重新装载。</p>
 *
 * <p><strong>一致性边界</strong>：Redis 是热路径权威，MySQL 经 {@link #settle} 异步追赶（毫秒级延迟）。
 * 缓存键无 TTL，仅在首次预扣时通过 SETNX 从 MySQL 装载；充值经 {@link #credit} 同步两侧，
 * 遗留扣费经 {@link #invalidate} 失效缓存，保证正常运转下两侧始终一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaLedger {

    /** Redis 额度键前缀，完整键为 {@code user:quota:{userId}}。 */
    private static final String QUOTA_KEY_PREFIX = "user:quota:";

    private final StringRedisTemplate redisTemplate;
    private final SysUserMapper sysUserMapper;

    /**
     * 原子预扣 Lua：余额 ≥ cost 时 decrby 并返回新余额，否则返回 -1。
     * 与需求约定的脚本一致；调用前须确保键已装载，否则 missing 会与"余额不足"混为 -1。
     */
    private static final DefaultRedisScript<Long> PRE_DEDUCT_SCRIPT;
    static {
        PRE_DEDUCT_SCRIPT = new DefaultRedisScript<>();
        PRE_DEDUCT_SCRIPT.setScriptText("""
                local current = redis.call('get', KEYS[1])
                if current and tonumber(current) >= tonumber(ARGV[1]) then
                    return redis.call('decrby', KEYS[1], ARGV[1])
                else
                    return -1
                end
                """);
        PRE_DEDUCT_SCRIPT.setResultType(Long.class);
    }

    /**
     * 仅当键存在时 incrby，避免在缓存未装载时凭空创建一个只有"增量"的键。
     */
    private static final DefaultRedisScript<Long> INCRBY_IF_EXISTS_SCRIPT;
    static {
        INCRBY_IF_EXISTS_SCRIPT = new DefaultRedisScript<>();
        INCRBY_IF_EXISTS_SCRIPT.setScriptText("""
                if redis.call('exists', KEYS[1]) == 1 then
                    return redis.call('incrby', KEYS[1], ARGV[1])
                else
                    return -1
                end
                """);
        INCRBY_IF_EXISTS_SCRIPT.setResultType(Long.class);
    }

    /** 计费落账模式：REDIS 正常走 Lua；MYSQL_FALLBACK 为 Redis 不可达时的降级。 */
    public enum LedgerMode {
        REDIS,
        MYSQL_FALLBACK
    }

    /** 预扣结果。 */
    public record ChargeResult(boolean success, LedgerMode mode) {
        public static ChargeResult redis(boolean success) {
            return new ChargeResult(success, LedgerMode.REDIS);
        }

        public static ChargeResult mysqlFallback(boolean success) {
            return new ChargeResult(success, LedgerMode.MYSQL_FALLBACK);
        }
    }

    /**
     * 业务 Handler 执行前的原子预扣。
     *
     * @param userId 用户 ID
     * @param cost   应扣点数（&le; 0 视为免费接口，直接放行）
     * @return 预扣结果，包含是否成功与落账模式
     */
    public ChargeResult preDeduct(Long userId, int cost) {
        if (cost <= 0) {
            return ChargeResult.redis(true);
        }
        String key = quotaKey(userId);
        try {
            seedIfAbsent(userId, key);
            Long result = redisTemplate.execute(PRE_DEDUCT_SCRIPT, List.of(key), String.valueOf(cost));
            if (result == null) {
                log.warn("Redis 预扣返回 null，降级 MySQL: userId={}, cost={}", userId, cost);
                return mysqlFallback(userId, cost);
            }
            return ChargeResult.redis(result >= 0);
        } catch (DataAccessException ex) {
            log.warn("Redis 预扣不可达，降级 MySQL 扣费: userId={}, cost={}", userId, cost, ex);
            return mysqlFallback(userId, cost);
        }
    }

    /**
     * 业务执行失败时返还预扣额度（补偿）。
     *
     * <p>REDIS 模式下 MySQL 尚未落账（{@link #settle} 仅在成功路径触发），只需返还 Redis；
     * 若键已被驱逐则跳过——下次预扣会从 MySQL 重新装载正确余额（MySQL 未扣，故正确）。
     * MYSQL_FALLBACK 模式下预扣走的是 MySQL，故退款也走 MySQL。</p>
     */
    public void compensate(Long userId, int cost, LedgerMode mode) {
        if (cost <= 0) {
            return;
        }
        if (mode == LedgerMode.MYSQL_FALLBACK) {
            sysUserMapper.addBalanceQuota(userId, cost);
            return;
        }
        try {
            redisTemplate.execute(INCRBY_IF_EXISTS_SCRIPT, List.of(quotaKey(userId)), String.valueOf(cost));
        } catch (DataAccessException ex) {
            // Redis 不可达：MySQL 本就未扣，无需补偿，仅记录。
            log.warn("Redis 补偿返还失败，跳过（MySQL 未落账）: userId={}, cost={}", userId, cost, ex);
        }
    }

    /**
     * 业务成功后异步将扣费结果增量刷回 MySQL，使持久账本与 Redis 最终一致。
     *
     * <p>REDIS 模式：执行无条件扣减（Redis 已授权，MySQL 仅做追赶记录）；
     * MYSQL_FALLBACK 模式：预扣阶段已同步扣过 MySQL，此处为空操作。</p>
     */
    public void settle(Long userId, int cost, LedgerMode mode) {
        if (cost <= 0 || mode == null || mode == LedgerMode.MYSQL_FALLBACK) {
            return;
        }
        try {
            int rows = sysUserMapper.settleQuota(userId, cost);
            if (rows == 0) {
                log.warn("settle 未命中用户行，可能已删除: userId={}, cost={}", userId, cost);
            }
        } catch (Exception ex) {
            // settle 失败会导致 MySQL 落后于 Redis；记录以便人工对账，不影响已成功的业务调用。
            log.error("异步 settle MySQL 失败: userId={}, cost={}", userId, cost, ex);
        }
    }

    /**
     * 充值 / 管理员加点：先落 MySQL，事务提交后再 incrby Redis。
     *
     * @param requireActiveUser {@code true} 时使用 {@code addQuota}（校验未删除），
     *                          适用于管理员加点；{@code false} 时使用 {@code addBalanceQuota}，
     *                          适用于支付入账
     * @return {@code true} MySQL 入账成功
     */
    public boolean credit(Long userId, long amount, boolean requireActiveUser) {
        if (amount == 0) {
            return true;
        }
        boolean ok = requireActiveUser
                ? sysUserMapper.addQuota(userId, amount) > 0
                : sysUserMapper.addBalanceQuota(userId, amount) > 0;
        if (ok && amount > 0) {
            runAfterCommitOrNow(() -> incrbyIfExists(quotaKey(userId), amount));
        }
        return ok;
    }

    /**
     * 失效缓存键，供遗留 MySQL 直扣路径调用，避免双轨账本漂移。
     */
    public void invalidate(Long userId) {
        try {
            redisTemplate.delete(quotaKey(userId));
        } catch (DataAccessException ex) {
            log.debug("invalidate 失败，忽略: userId={}", userId, ex);
        }
    }

    // ---- internals ----

    /**
     * 缓存未装载时从 MySQL 读取余额并 SETNX 装载。并发装载只有第一个写入生效，
     * 由于读到的 MySQL 值相同，落败者的值被丢弃也无妨。
     */
    private void seedIfAbsent(Long userId, String key) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        long balance = readMysqlBalance(userId);
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(balance));
    }

    private void incrbyIfExists(String key, long amount) {
        try {
            redisTemplate.execute(INCRBY_IF_EXISTS_SCRIPT, List.of(key), String.valueOf(amount));
        } catch (DataAccessException ex) {
            log.warn("Redis incrby 失败，缓存可能落后于 MySQL: key={}, amount={}", key, amount, ex);
        }
    }

    private ChargeResult mysqlFallback(Long userId, int cost) {
        // 尽量清掉可能过期的缓存键，Redis 恢复后下次预扣重新装载，避免陈旧键被复用
        invalidate(userId);
        boolean ok = sysUserMapper.deductQuota(userId, cost) > 0;
        return ChargeResult.mysqlFallback(ok);
    }

    private long readMysqlBalance(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getBalanceQuota() == null) {
            return 0L;
        }
        return Math.max(0L, user.getBalanceQuota());
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String quotaKey(Long userId) {
        return QUOTA_KEY_PREFIX + userId;
    }
}
