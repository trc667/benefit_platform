package com.campus.growth.modules.point.job;

import com.campus.growth.modules.point.service.RankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 排行榜启动自检。
 *
 * <h3>解决什么问题</h3>
 * <p>排行榜是 Redis 派生数据，真源在 MySQL。首次部署、导入历史数据或 Redis 被清空时，
 * 会出现"账户里有钱但榜上无名"。这里在应用启动时做一次轻量自检：
 * <b>只有当总榜为空时才重建</b>（{@code zCard == 0}），正常启动几乎没有开销。</p>
 *
 * <p>运行期若发现不一致，管理端还有 {@code POST /api/admin/point/rank/rebuild} 可手动重建。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankBackfillRunner implements ApplicationRunner {

    private final RankService rankService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            long size = rankService.totalOf("TOTAL");
            if (size > 0) {
                log.info("排行榜已有 {} 条数据，跳过启动回填", size);
                return;
            }
            RankService.RebuildResult result = rankService.rebuild();
            log.info("排行榜为空，启动回填完成：总榜 {} 人，月榜 {} 人",
                    result.totalRankSize(), result.monthRankSize());
        } catch (Exception e) {
            // 回填失败不能影响应用启动
            log.warn("排行榜启动回填失败（可稍后用管理端接口重建）：{}", e.getMessage());
        }
    }
}
