package com.campus.growth.infra.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 二级缓存配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.cache")
public class CacheProperties {

    /** 商品缓存 L2 时长（秒） */
    private long goodsTtlSeconds = 1800;

    /** 券模板缓存 L2 时长（秒） */
    private long couponTemplateTtlSeconds = 600;

    /** 空值缓存时长（秒），用于防穿透 */
    private long nullValueTtlSeconds = 60;

    /** TTL 随机抖动百分比，用于防雪崩 */
    private int randomJitterPercent = 10;

    /** 本地缓存最大条目数 */
    private long localMaxSize = 10_000;

    /** 本地缓存过期秒数 */
    private long localTtlSeconds = 60;

    /** 回源互斥锁等待时间（毫秒） */
    private long rebuildWaitMillis = 200;
}
