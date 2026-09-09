package com.campus.growth.infra.cache;

import lombok.Data;

/**
 * 缓存统计视图对象。
 */
@Data
public class CacheStatsVO {

    private long l1Hit;
    private long l2Hit;
    private long miss;
    private long dbLoad;
    private long nullHit;
    private long rebuildBlocked;
    private long evict;
    private long totalRequest;
    /** 命中率（百分比，保留两位小数） */
    private double hitRate;
}
