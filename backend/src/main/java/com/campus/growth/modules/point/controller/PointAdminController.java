package com.campus.growth.modules.point.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.point.dto.PointAdjustDTO;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.point.service.RankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 管理端积分运维接口。
 */
@RestController
@RequestMapping("/api/admin/point")
@RequiredArgsConstructor
public class PointAdminController {

    private final RankService rankService;
    private final PointService pointService;
    private final UserQueryService userQueryService;

    /**
     * 重建排行榜（以积分账户与流水为真源）。
     * <p>用于导入历史数据、Redis 被清空后修复"账户有分但榜上无名"的不一致。</p>
     */
    @PostMapping("/rank/rebuild")
    @OpLog(module = "积分", action = "重建排行榜")
    public Result<Map<String, Object>> rebuildRank() {
        RankService.RebuildResult result = rankService.rebuild();
        return Result.ok(Map.of("totalRankSize", result.totalRankSize(),
                "monthRankSize", result.monthRankSize()));
    }

    /**
     * 手动调整积分（活动漏发、投诉补偿、兑换码核销异常等人工兜底）。
     *
     * <p>不是直接改库：走流水 + 幂等键，重复提交同一个 {@code bizNo} 只会入账一次，
     * 扣分不允许把余额扣成负数，操作本身进操作日志。</p>
     */
    @PostMapping("/adjust")
    @OpLog(module = "积分", action = "手动调整积分")
    public Result<Map<String, Object>> adjust(@Valid @RequestBody PointAdjustDTO dto) {
        if (userQueryService.getById(dto.getUserId()) == null) {
            return Result.fail(ErrorCode.NOT_FOUND, "用户不存在");
        }
        String bizNo = StringUtils.hasText(dto.getBizNo())
                ? dto.getBizNo().trim()
                : "ADMIN:" + UUID.randomUUID();
        boolean applied = pointService.adjustPoint(dto.getUserId(), bizNo, dto.getChangePoint(),
                dto.getReason());
        return Result.ok(Map.of("applied", applied, "bizNo", bizNo,
                "balance", pointService.balanceOf(dto.getUserId())));
    }
}
