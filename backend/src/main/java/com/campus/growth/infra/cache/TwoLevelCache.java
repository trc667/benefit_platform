package com.campus.growth.infra.cache;

import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.infra.lock.DistributedLockTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 二级缓存：Caffeine（L1，进程内）+ Redis（L2，分布式）。
 *
 * <h3>为什么需要两级</h3>
 * <p>纯 Redis 缓存的问题：每次读都要一次网络往返，热点商品 QPS 一高 Redis 就成了瓶颈。
 * L1 挡住绝大部分读请求（纳秒级），L2 负责多实例间共享与持久化，Redis 压力下降一个量级。</p>
 *
 * <h3>更新策略：Cache-Aside</h3>
 * <pre>
 * 读：L1 → L2 → 加互斥锁回源 DB → 写 L2（带随机 TTL）→ 写 L1
 * 写：先更新 DB → 再删除 L1 + L2（延迟双删兜底）
 * </pre>
 *
 * <h3>三个经典问题的处理</h3>
 * <ul>
 *   <li><b>击穿</b>：热点 key 过期瞬间大量请求打到 DB。这里用 Redisson 锁保证"只有一个线程回源"，
 *       其余线程短暂等待后重读 L2，仍拿不到则返回空并由上层降级；</li>
 *   <li><b>穿透</b>：查询不存在的数据。回源结果为空时写入空值占位键（短 TTL），后续请求直接命中占位；</li>
 *   <li><b>雪崩</b>：同一时刻大量 key 过期。写入 L2 时对 TTL 做 ±jitter% 随机抖动。</li>
 * </ul>
 *
 * <p><b>一致性说明</b>：L1 在多实例部署下存在秒级不一致窗口，因此只用于"读多写少、
 * 容忍短时不一致"的数据（商品、券模板）。下单、扣库存等强一致校验一律读 DB 或 Redis 原子操作。</p>
 */
@Slf4j
@Component
public class TwoLevelCache {

    private final StringRedisTemplate redis;
    private final CacheProperties properties;
    private final DistributedLockTemplate lockTemplate;
    private final CacheStatsHolder stats = new CacheStatsHolder();
    private final Cache<String, Object> localCache;

    /** 空值占位，序列化后写入 Redis 与 L1 */
    private static final String NULL_PLACEHOLDER = "\u0000NULL\u0000";

    public TwoLevelCache(StringRedisTemplate redis, CacheProperties properties,
                         DistributedLockTemplate lockTemplate) {
        this.redis = redis;
        this.properties = properties;
        this.lockTemplate = lockTemplate;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(properties.getLocalMaxSize())
                .expireAfterWrite(Duration.ofSeconds(properties.getLocalTtlSeconds()))
                .recordStats()
                .build();
    }

    /**
     * 读取单个对象。
     *
     * @param key    完整缓存键
     * @param clazz  目标类型
     * @param ttl    L2 过期时间
     * @param loader 回源逻辑（查数据库）
     */
    public <T> T get(String key, Class<T> clazz, Duration ttl, Supplier<T> loader) {
        // ---------- L1 ----------
        Object local = localCache.getIfPresent(key);
        if (local != null) {
            stats.getL1Hit().increment();
            return NULL_PLACEHOLDER.equals(local) ? null : clazz.cast(local);
        }

        // ---------- L2 ----------
        String cached = safeGet(key);
        if (cached != null) {
            stats.getL2Hit().increment();
            if (NULL_PLACEHOLDER.equals(cached)) {
                stats.getNullHit().increment();
                localCache.put(key, NULL_PLACEHOLDER);
                return null;
            }
            T value = JsonUtil.parse(cached, clazz);
            if (value != null) {
                localCache.put(key, value);
                return value;
            }
            // 缓存内容损坏：删掉后走回源，避免一直反序列化失败
            log.warn("缓存反序列化失败，已删除 key={}", key);
            safeDelete(key);
        }

        // ---------- 回源（防击穿） ----------
        stats.getMiss().increment();
        return loadWithMutex(key, clazz, ttl, loader);
    }

    /**
     * 读取列表。列表元素类型用于反序列化。
     */
    public <T> List<T> getList(String key, Class<T> elementType, Duration ttl, Supplier<List<T>> loader) {
        Object local = localCache.getIfPresent(key);
        if (local instanceof List<?> list) {
            stats.getL1Hit().increment();
            // L1 里存的是已反序列化的对象列表，直接返回
            return list.stream().map(elementType::cast).toList();
        }

        String cached = safeGet(key);
        if (cached != null) {
            stats.getL2Hit().increment();
            List<T> value = JsonUtil.parseList(cached, elementType);
            if (!value.isEmpty()) {
                localCache.put(key, value);
                return value;
            }
        }

        stats.getMiss().increment();
        return loadWithMutex(key, ttl, () -> loader.get(), new TypeReference<>() {
        });
    }

    /** 主动失效（Cache-Aside 的写路径：先更新 DB，再调用本方法） */
    public void evict(String key) {
        stats.getEvict().increment();
        localCache.invalidate(key);
        safeDelete(key);
    }

    /**
     * 延迟双删：删除缓存后，延迟再删一次。
     * <p>用于"先更新 DB 再删缓存"仍可能被并发读请求回填脏数据的场景。</p>
     */
    public void evictWithDoubleDelete(String key, long delayMillis) {
        evict(key);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // 用一个轻量线程做延迟删除；失败不影响主流程，下次读会自然回源
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delayMillis + random.nextInt(50));
                safeDelete(key);
                localCache.invalidate(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("延迟双删失败 key={}", key, e);
            }
        }, "cache-double-delete");
        t.setDaemon(true);
        t.start();
    }

    /** 只写缓存（用于预热） */
    public void put(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }
        String json = JsonUtil.toJson(value);
        if (json == null) {
            return;
        }
        safeSet(key, json, jitter(ttl));
        localCache.put(key, value);
    }

    public CacheStatsHolder getStats() {
        return stats;
    }

    public long localCacheSize() {
        localCache.cleanUp();
        return localCache.estimatedSize();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private <T> T loadWithMutex(String key, Class<T> clazz, Duration ttl, Supplier<T> loader) {
        String lockKey = "cache:rebuild:" + key;
        return lockTemplate.executeWithResult(lockKey, 0, 10, () -> {
            // 双重检查：可能已被前一个持锁线程回填
            String again = safeGet(key);
            if (again != null) {
                if (NULL_PLACEHOLDER.equals(again)) {
                    return null;
                }
                T cachedValue = JsonUtil.parse(again, clazz);
                if (cachedValue != null) {
                    localCache.put(key, cachedValue);
                    return cachedValue;
                }
            }
            T value = loadAndFill(key, ttl, loader);
            return value;
        });
    }

    private <T> List<T> loadWithMutex(String key, Duration ttl, Supplier<List<T>> loader,
                                      TypeReference<List<T>> typeReference) {
        String lockKey = "cache:rebuild:" + key;
        return lockTemplate.executeWithResult(lockKey, 0, 10, () -> {
            String again = safeGet(key);
            if (again != null) {
                List<T> cachedValue = JsonUtil.parse(again, typeReference);
                if (cachedValue != null && !cachedValue.isEmpty()) {
                    localCache.put(key, cachedValue);
                    return cachedValue;
                }
            }
            List<T> value = loader.get();
            if (value == null || value.isEmpty()) {
                // 列表为空不写占位（可能是合法的空列表），只做短 TTL 缓存避免频繁回源
                safeSet(key, "[]", Duration.ofSeconds(properties.getNullValueTtlSeconds()));
                return List.of();
            }
            safeSet(key, JsonUtil.toJson(value), jitter(ttl));
            localCache.put(key, value);
            return value;
        });
    }

    private <T> T loadAndFill(String key, Duration ttl, Supplier<T> loader) {
        T value;
        try {
            value = loader.get();
        } catch (Exception e) {
            // 回源失败不能把缓存写坏，直接抛出由上层决定降级
            log.error("缓存回源失败 key={}", key, e);
            throw e;
        }
        stats.getDbLoad().increment();
        if (value == null) {
            // 防穿透：写空值占位，短 TTL
            safeSet(key, NULL_PLACEHOLDER, Duration.ofSeconds(properties.getNullValueTtlSeconds()));
            localCache.put(key, NULL_PLACEHOLDER);
            return null;
        }
        safeSet(key, JsonUtil.toJson(value), jitter(ttl));
        localCache.put(key, value);
        return value;
    }

    /** TTL 随机抖动，防雪崩 */
    private Duration jitter(Duration ttl) {
        int percent = properties.getRandomJitterPercent();
        if (percent <= 0) {
            return ttl;
        }
        long base = ttl.toMillis();
        long delta = base * percent / 100;
        long offset = ThreadLocalRandom.current().nextLong(-delta, delta + 1);
        return Duration.ofMillis(Math.max(1000, base + offset));
    }

    private String safeGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 抖动时降级为"未命中"，让请求回源而不是整体报错
            log.error("读取缓存异常 key={}", key, e);
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("写入缓存异常 key={}", key, e);
        }
    }

    private void safeDelete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.error("删除缓存异常 key={}", key, e);
        }
    }
}
