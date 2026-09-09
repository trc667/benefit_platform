package com.campus.growth.common.aspect;

/**
 * 切面执行顺序常量。
 *
 * <h3>为什么要把顺序写死成常量</h3>
 * <p>切面顺序是本项目最容易踩坑的地方：一旦顺序写错，"分布式锁"就会失效且很难复现。
 * 用常量集中管理，并在 {@link com.campus.growth.config.TransactionConfig} 里把事务切面顺序
 * 固定为 {@link #TRANSACTION}，任何新增切面都必须显式选择一个区间。</p>
 *
 * <pre>
 * 执行顺序（值越小越靠外）：
 *   RATE_LIMIT(0)     限流最先，挡掉无效流量，不占用锁与数据库连接
 *   LOCK(10)          锁必须包住事务
 *   IDEMPOTENT(20)    幂等键写入
 *   TRANSACTION(30)   事务边界
 *   OP_LOG(40)        日志最内层，记录真实执行耗时与结果
 * </pre>
 */
public final class AspectOrder {

    private AspectOrder() {
    }

    public static final int RATE_LIMIT = 0;
    public static final int LOCK = 10;
    public static final int IDEMPOTENT = 20;
    public static final int TRANSACTION = 30;
    public static final int OP_LOG = 40;
}
