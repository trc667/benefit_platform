package com.campus.growth.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 配置（仅用于分布式锁与原子操作）。
 *
 * <h3>为什么不用 redisson-spring-boot-starter</h3>
 * <p>starter 会把 Redisson 的 ConnectionFactory 接管给 Spring Data Redis，
 * 这样缓存读写也走 Redisson 的编解码，位图/计数器的原始字节就变了，
 * redis-cli 直接看会是一串编码后的内容，排查非常痛苦。
 * 这里只把 Redisson 当作"锁客户端"，缓存与位图交给 Lettuce，
 * 两者共用一个 Redis 实例，互不干扰。</p>
 *
 * <p>本机验证：Redisson 3.27.2 + Redis 3.2.100 的锁、位图、原子计数、ZSet 均正常。</p>
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:3000ms}")
    private String timeout;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setLockWatchdogTimeout(30_000L);

        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(16)
                .setConnectTimeout(5000)
                .setRetryAttempts(3);
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        log.info("初始化 Redisson 单机客户端 address=redis://{}:{} database={}", host, port, database);
        return Redisson.create(config);
    }
}
