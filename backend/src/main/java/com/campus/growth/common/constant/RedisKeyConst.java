package com.campus.growth.common.constant;

/**
 * Redis 键常量。
 * <p>统一前缀 {@code cg:}；缓存键与业务键分开，避免误清理打掉锁与签到位图。</p>
 */
public final class RedisKeyConst {

    private RedisKeyConst() {
    }

    /** 全局前缀 */
    public static final String PREFIX = "cg:";

    // ---------------- 签到（BitMap） ----------------
    /** 月度签到位图：cg:signin:month:{yyyyMM}:{userId}，offset = day - 1 */
    public static final String SIGNIN_MONTH = PREFIX + "signin:month:%s:%d";
    /** 年度签到位图：cg:signin:year:{yyyy}:{userId}，offset = dayOfYear - 1 */
    public static final String SIGNIN_YEAR = PREFIX + "signin:year:%d:%d";
    /** 连续签到天数 */
    public static final String SIGNIN_CONTINUOUS = PREFIX + "signin:continuous:%d";

    // ---------------- 积分与排行榜 ----------------
    /** 总积分排行榜 ZSet */
    public static final String POINT_RANK_TOTAL = PREFIX + "point:rank:total";
    /** 月度积分排行榜 ZSet：cg:point:rank:month:{yyyyMM} */
    public static final String POINT_RANK_MONTH = PREFIX + "point:rank:month:%s";

    // ---------------- 任务进度（热数据 Hash） ----------------
    /** cg:task:progress:{userId}:{periodKey}，field = taskCode，value = 进度 */
    public static final String TASK_PROGRESS = PREFIX + "task:progress:%d:%s";
    /** 任务奖励领取锁/标记：cg:task:claimed:{userId}:{periodKey}，field = taskCode */
    public static final String TASK_CLAIMED = PREFIX + "task:claimed:%d:%s";

    // ---------------- 优惠券 ----------------
    /** 券库存（Redis 预扣）：cg:coupon:stock:{templateId} */
    public static final String COUPON_STOCK = PREFIX + "coupon:stock:%d";
    /** 单人已领数量：cg:coupon:user:{userId}:{templateId} */
    public static final String COUPON_USER_COUNT = PREFIX + "coupon:user:%d:%d";
    /** 可用券缓存：cg:coupon:available:{userId} */
    public static final String COUPON_AVAILABLE = PREFIX + "coupon:available:%d";

    // ---------------- 兑换码 ----------------
    /** 核销位图：cg:redeem:used:{batchNo}，offset = seqNo */
    public static final String REDEEM_USED_BITMAP = PREFIX + "redeem:used:%s";
    /** 兑换码生成进度：cg:redeem:cursor:{batchNo} */
    public static final String REDEEM_CURSOR = PREFIX + "redeem:cursor:%s";

    // ---------------- 二级缓存 ----------------
    /** 商品缓存 L2 */
    public static final String CACHE_GOODS = PREFIX + "cache:goods:%d";
    /** 商品空值占位（防穿透） */
    public static final String CACHE_GOODS_NULL = PREFIX + "cache:goods:null:%d";
    /** 券模板缓存 L2 */
    public static final String CACHE_COUPON_TEMPLATE = PREFIX + "cache:coupon:template:%d";

    // ---------------- 锁 / 限流 / 幂等 / 登录态 ----------------
    public static final String LOCK = PREFIX + "lock:%s";
    public static final String LIMIT_LOCAL = PREFIX + "limit:local:%s";
    public static final String LIMIT_REDIS = PREFIX + "limit:redis:%s";
    public static final String IDEMPOTENT = PREFIX + "idem:%s";
    public static final String TOKEN = PREFIX + "token:%d:%s";
    /** 登录失败计数：cg:auth:fail:{username}（防暴力破解） */
    public static final String AUTH_FAIL = PREFIX + "auth:fail:%s";

    // ---------------- 风控（防小号薅羊毛） ----------------
    /** 单 IP 每日注册计数：cg:risk:reg:ip:{yyyyMMdd}:{ip} */
    public static final String RISK_REGISTER_IP = PREFIX + "risk:reg:ip:%s:%s";
    /** 单设备每日注册计数：cg:risk:reg:device:{yyyyMMdd}:{deviceId} */
    public static final String RISK_REGISTER_DEVICE = PREFIX + "risk:reg:device:%s:%s";
    /** 单设备每日签到的**不同账号**集合：cg:risk:signin:device:{yyyyMMdd}:{deviceId} */
    public static final String RISK_SIGNIN_DEVICE = PREFIX + "risk:signin:device:%s:%s";

    public static String authFail(String username) {
        return String.format(AUTH_FAIL, username);
    }

    public static String riskRegisterIp(String day, String ip) {
        return String.format(RISK_REGISTER_IP, day, ip);
    }

    public static String riskRegisterDevice(String day, String deviceId) {
        return String.format(RISK_REGISTER_DEVICE, day, deviceId);
    }

    public static String riskSigninDevice(String day, String deviceId) {
        return String.format(RISK_SIGNIN_DEVICE, day, deviceId);
    }

    public static String signinMonth(String yyyyMM, Long userId) {
        return String.format(SIGNIN_MONTH, yyyyMM, userId);
    }

    public static String signinYear(int year, Long userId) {
        return String.format(SIGNIN_YEAR, year, userId);
    }

    public static String signinContinuous(Long userId) {
        return String.format(SIGNIN_CONTINUOUS, userId);
    }

    public static String pointRankMonth(String yyyyMM) {
        return String.format(POINT_RANK_MONTH, yyyyMM);
    }

    public static String taskProgress(Long userId, String periodKey) {
        return String.format(TASK_PROGRESS, userId, periodKey);
    }

    public static String taskClaimed(Long userId, String periodKey) {
        return String.format(TASK_CLAIMED, userId, periodKey);
    }

    public static String couponStock(Long templateId) {
        return String.format(COUPON_STOCK, templateId);
    }

    public static String couponUserCount(Long userId, Long templateId) {
        return String.format(COUPON_USER_COUNT, userId, templateId);
    }

    public static String redeemUsedBitmap(String batchNo) {
        return String.format(REDEEM_USED_BITMAP, batchNo);
    }

    public static String lock(String bizKey) {
        return String.format(LOCK, bizKey);
    }

    public static String idempotent(String key) {
        return String.format(IDEMPOTENT, key);
    }
}
