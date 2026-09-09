package com.campus.growth.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.infra.cache.CacheStatsVO;
import com.campus.growth.infra.cache.TwoLevelCache;
import com.campus.growth.infra.ratelimit.LocalRateLimiter;
import com.campus.growth.infra.ratelimit.RateLimitFacade;
import com.campus.growth.modules.mq.entity.MqEventOutbox;
import com.campus.growth.modules.mq.job.OutboxCompensateJob;
import com.campus.growth.modules.mq.mapper.MqEventOutboxMapper;
import com.campus.growth.modules.system.entity.SysOperationLog;
import com.campus.growth.modules.system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端运维接口：缓存统计、本地消息表监控、操作日志。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SystemAdminController {

    private final TwoLevelCache twoLevelCache;
    private final RateLimitFacade rateLimitFacade;
    private final LocalRateLimiter localRateLimiter;
    private final MqEventOutboxMapper outboxMapper;
    /**
     * 补偿任务是条件装配的（campus.mq.outbox.enabled=false 时不存在），
     * 这里用 ObjectProvider 懒获取，避免"关掉补偿任务就起不来"的启动依赖问题。
     */
    private final ObjectProvider<OutboxCompensateJob> outboxCompensateJobProvider;
    private final OperationLogService operationLogService;

    /** 二级缓存命中率 */
    @GetMapping("/cache/stats")
    public Result<Map<String, Object>> cacheStats() {
        CacheStatsVO stats = twoLevelCache.getStats().toVo();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("localCacheSize", twoLevelCache.localCacheSize());
        result.put("limiterStrategy", rateLimitFacade.getDefaultStrategy());
        result.put("localLimiterCount", localRateLimiter.cachedLimiterCount());
        return Result.ok(result);
    }

    /** 重置缓存统计（压测前后对比用） */
    @PostMapping("/cache/stats/reset")
    public Result<Void> resetCacheStats() {
        twoLevelCache.getStats().reset();
        return Result.ok();
    }

    /** 本地消息表分页 */
    @GetMapping("/mq/outbox/page")
    public Result<PageResult<MqEventOutbox>> outboxPage(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) String status) {
        Page<MqEventOutbox> result = outboxMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<MqEventOutbox>()
                        .eq(status != null && !status.isBlank(), MqEventOutbox::getStatus, status)
                        .orderByDesc(MqEventOutbox::getId));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    /** 手动重发失败事件 */
    @PostMapping("/mq/outbox/retry")
    public Result<Map<String, Object>> retryOutbox(@RequestBody Map<String, Object> body) {
        OutboxCompensateJob job = outboxCompensateJobProvider.getIfAvailable();
        if (job == null) {
            return Result.fail(com.campus.growth.common.result.ErrorCode.SYSTEM_ERROR,
                    "本地消息表补偿任务未启用（campus.mq.outbox.enabled=false）");
        }
        Long id = Long.valueOf(String.valueOf(body.get("id")));
        MqEventOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) {
            return Result.fail(com.campus.growth.common.result.ErrorCode.NOT_FOUND, "消息不存在");
        }
        boolean ok = job.resend(outbox);
        return Result.ok(Map.of("success", ok));
    }

    /** 操作日志分页 */
    @GetMapping("/operation/log/page")
    public Result<PageResult<SysOperationLog>> logPage(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @RequestParam(required = false) String module,
                                                       @RequestParam(required = false) String username) {
        return Result.ok(operationLogService.page(page, size, module, username));
    }
}
