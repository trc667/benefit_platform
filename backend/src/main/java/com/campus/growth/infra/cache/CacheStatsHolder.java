package com.campus.growth.infra.cache;

import lombok.Data;

import java.util.concurrent.atomic.LongAdder;

/**
 * 二级缓存统计。管理端 {@code /api/admin/cache/stats} 读取它展示命中率。
 */
@Data
public class CacheStatsHolder {

    /** L1（Caffeine）命中次数 */
    private final LongAdder l1Hit = new LongAdder();
    /** L2（Redis）命中次数 */
    private final LongAdder l2Hit = new LongAdder();
    /** 完全未命中，需要回源 */
    private final LongAdder miss = new LongAdder();
    /** 回源数据库次数 */
    private final LongAdder dbLoad = new LongAdder();
    /** 命中的空值缓存（说明挡掉了一次穿透） */
    private final LongAdder nullHit = new LongAdder();
    /** 因未拿到重建锁而返回空/等待的次数（说明触发了击穿保护） */
    private final LongAdder rebuildBlocked = new LongAdder();
    /** 主动失效次数 */
    private final LongAdder evict = new LongAdder();

    public long totalRequest() {
        return l1Hit.sum() + l2Hit.sum() + miss.sum();
    }

    public double hitRate() {
        long total = totalRequest();
        if (total == 0) {
            return 0d;
        }
        return (l1Hit.sum() + l2Hit.sum()) * 100d / total;
    }

    public CacheStatsVO toVo() {
        CacheStatsVO vo = new CacheStatsVO();
        vo.setL1Hit(l1Hit.sum());
        vo.setL2Hit(l2Hit.sum());
        vo.setMiss(miss.sum());
        vo.setDbLoad(dbLoad.sum());
        vo.setNullHit(nullHit.sum());
        vo.setRebuildBlocked(rebuildBlocked.sum());
        vo.setEvict(evict.sum());
        vo.setTotalRequest(totalRequest());
        vo.setHitRate(Math.round(hitRate() * 100) / 100d);
        return vo;
    }

    public void reset() {
        l1Hit.reset();
        l2Hit.reset();
        miss.reset();
        dbLoad.reset();
        nullHit.reset();
        rebuildBlocked.reset();
        evict.reset();
    }
}
