package com.campus.growth.modules.point.service;

import java.util.List;

/**
 * 排行榜服务（Redis ZSet）。
 *
 * <h3>为什么排行榜放 Redis</h3>
 * <p>排行榜是典型的"读多写多、实时性要求高"场景。若用 MySQL 实现，
 * 每次查 Top20 都要 {@code ORDER BY point DESC LIMIT 20} 并扫全表；
 * 查"我的排名"更是灾难（MySQL 5.7 没有窗口函数，只能 count 比我大的行）。
 * ZSet 的 {@code ZREVRANGE}/{@code ZREVRANK} 都是 O(log N)，天然适配。</p>
 *
 * <h3>两个榜</h3>
 * <ul>
 *   <li>总榜：只增不减（扣分不减榜，反映"历史累计贡献"，避免用户被扣分后名次剧烈波动）；</li>
 *   <li>月榜：key 带 yyyyMM，40 天后自动过期，不需要清理任务。</li>
 * </ul>
 */
public interface RankService {

    /** 积分变动时更新两个榜 */
    void addScore(Long userId, int delta);

    /** 榜单前 N 名 */
    List<RankEntry> top(String type, int limit);

    /** 我的名次（1 开始），未上榜返回 null */
    Integer rankOf(Long userId, String type);

    /** 我的分数，未上榜返回 0 */
    long scoreOf(Long userId, String type);

    /** 榜单总人数 */
    long totalOf(String type);

    /**
     * 用数据库重建排行榜（运维/初始化用）。
     *
     * <h3>为什么需要</h3>
     * <p>排行榜是 Redis 派生数据。如果初始化数据直接写了积分账户而没有同步 ZSet
     * （例如导入历史数据、Redis 被清空），就会出现"账户里有 2600 分但榜上无名"的不一致。
     * 本方法以 {@code user_point_account.total_earned} 与 {@code point_record} 为真源重建两个榜。</p>
     *
     * @return 总榜/月榜重建人数
     */
    RebuildResult rebuild();

    /** 重建结果 */
    record RebuildResult(int totalRankSize, int monthRankSize) {
    }

    /** 榜单条目 */
    record RankEntry(Long userId, long score) {
    }
}
