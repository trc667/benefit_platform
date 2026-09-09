package com.campus.growth.modules.point.controller;

import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.auth.service.UserQueryService;
import com.campus.growth.modules.auth.vo.UserBriefVO;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.point.service.RankService;
import com.campus.growth.modules.point.vo.PointAccountVO;
import com.campus.growth.modules.point.vo.PointRecordVO;
import com.campus.growth.modules.point.vo.RankItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 积分与排行榜接口。
 *
 * <p>排行榜的昵称/头像由本 Controller 组装：PointService 只负责积分，
 * UserQueryService 只负责用户，两者在 Controller 层组合，避免模块间循环依赖。</p>
 */
@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;
    private final RankService rankService;
    private final UserQueryService userQueryService;

    @GetMapping("/account")
    public Result<PointAccountVO> account() {
        return Result.ok(pointService.getAccountView(UserContext.requireUserId()));
    }

    @GetMapping("/records")
    public Result<PageResult<PointRecordVO>> records(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String bizType) {
        return Result.ok(pointService.pageRecords(UserContext.requireUserId(), bizType, page, size));
    }

    @GetMapping("/rank")
    public Result<List<RankItemVO>> rank(@RequestParam(defaultValue = "TOTAL") String type,
                                         @RequestParam(defaultValue = "20") int limit) {
        Long me = UserContext.userId();
        List<RankService.RankEntry> entries = rankService.top(type, Math.min(limit, 100));
        if (entries.isEmpty()) {
            return Result.ok(List.of());
        }
        Map<Long, UserBriefVO> userMap = userQueryService
                .listBrief(entries.stream().map(RankService.RankEntry::userId).toList())
                .stream().collect(Collectors.toMap(UserBriefVO::getId, Function.identity()));

        List<RankItemVO> list = new ArrayList<>(entries.size());
        int rank = 1;
        for (RankService.RankEntry entry : entries) {
            RankItemVO vo = new RankItemVO();
            vo.setRank(rank++);
            vo.setUserId(entry.userId());
            vo.setPoint(entry.score());
            vo.setIsMe(entry.userId().equals(me));
            UserBriefVO user = userMap.get(entry.userId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            } else {
                // 用户被删除但榜上还有分：给个兜底昵称，避免前端出现空白
                vo.setNickname("已注销用户");
            }
            list.add(vo);
        }
        return Result.ok(list);
    }

    @GetMapping("/rank/me")
    public Result<RankItemVO> myRank(@RequestParam(defaultValue = "TOTAL") String type) {
        Long userId = UserContext.requireUserId();
        RankItemVO vo = new RankItemVO();
        vo.setUserId(userId);
        vo.setRank(rankService.rankOf(userId, type));
        vo.setPoint(rankService.scoreOf(userId, type));
        vo.setIsMe(true);
        vo.setNickname(UserContext.get().getNickname());
        return Result.ok(vo);
    }
}
