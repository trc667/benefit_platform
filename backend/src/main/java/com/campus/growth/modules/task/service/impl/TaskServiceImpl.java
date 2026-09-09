package com.campus.growth.modules.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.enums.TaskStatus;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.PeriodKeyUtil;
import com.campus.growth.infra.cache.TwoLevelCache;
import com.campus.growth.modules.mq.service.EventPublisher;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.task.entity.TaskDefinition;
import com.campus.growth.modules.task.entity.UserTaskProgress;
import com.campus.growth.modules.task.mapper.TaskDefinitionMapper;
import com.campus.growth.modules.task.mapper.UserTaskProgressMapper;
import com.campus.growth.modules.task.mq.TaskProgressEvent;
import com.campus.growth.modules.task.service.TaskService;
import com.campus.growth.modules.task.vo.UserTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务服务实现（Redis 热数据 + Kafka 异步落库）。
 *
 * <h3>双层存储</h3>
 * <pre>
 * 写：HINCRBY cg:task:progress:{userId}:{periodKey} {taskCode} delta   ← 毫秒级，页面刷新不丢
 *     └─ 发 Kafka 事件 → 消费者批量 upsert user_task_progress（MySQL 归档）
 * 读：只读 Redis Hash；Redis 缺失时回源 MySQL 并回填
 * </pre>
 *
 * <h3>为什么"真源"放 Redis</h3>
 * <p>任务进度是高频写、高频读、单条数据极小（一个整数）的典型场景。
 * 若每次都写 MySQL，一个用户一天点几次任务就产生多次 UPDATE，
 * 高峰时行锁与 binlog 压力都不可接受。放 Redis 后写入是 O(1) 内存操作，
 * 落库交给消费者批量处理，吞吐差了一个量级。</p>
 *
 * <h3>丢数据怎么办</h3>
 * <p>Redis 不是绝对可靠的，所以做了三层兜底：</p>
 * <ol>
 *   <li>Kafka 消费者把进度写进 MySQL，Redis 重启后可从归档表恢复；</li>
 *   <li>进度是"单调递增"的，恢复时用 {@code GREATEST(progress, 事件进度)}，不会倒退；</li>
 *   <li>签到、下单这类关键行为本身在 MySQL 有流水，可以据此重算任务进度。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    /** 任务定义缓存 5 分钟（改动很少，管理端保存时会主动失效） */
    private static final String TASK_DEF_CACHE_KEY = RedisKeyConst.PREFIX + "cache:task:defs";
    private static final Duration TASK_DEF_TTL = Duration.ofMinutes(5);

    private final TaskDefinitionMapper taskDefinitionMapper;
    private final UserTaskProgressMapper userTaskProgressMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final EventPublisher eventPublisher;
    private final PointService pointService;
    private final TwoLevelCache twoLevelCache;

    @Override
    public List<UserTaskVO> listMine() {
        Long userId = UserContext.requireUserId();
        List<TaskDefinition> definitions = loadDefinitions();
        if (definitions.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        // 按周期分组读取进度，避免为每个任务单独发一次 Redis 请求
        Map<String, List<TaskDefinition>> byPeriod = definitions.stream()
                .collect(Collectors.groupingBy(d -> PeriodKeyUtil.resolve(d.getTaskType(), today)));

        List<UserTaskVO> result = new ArrayList<>(definitions.size());
        for (Map.Entry<String, List<TaskDefinition>> entry : byPeriod.entrySet()) {
            String periodKey = entry.getKey();
            String progressKey = RedisKeyConst.taskProgress(userId, periodKey);
            String claimedKey = RedisKeyConst.taskClaimed(userId, periodKey);
            Map<Object, Object> progressMap = stringRedisTemplate.opsForHash().entries(progressKey);
            Map<Object, Object> claimedMap = stringRedisTemplate.opsForHash().entries(claimedKey);

            // Redis 是热数据，可能被清空/重启丢数据：这里回源 MySQL 归档并回填，
            // 保证"页面进度不会因为 Redis 重启而归零"
            if (progressMap.isEmpty()) {
                progressMap = backfillFromArchive(userId, periodKey, progressKey);
            }

            for (TaskDefinition definition : entry.getValue()) {
                int progress = parseInt(progressMap.get(definition.getTaskCode()));
                boolean claimed = claimedMap.containsKey(definition.getTaskCode());
                result.add(buildVo(definition, periodKey, progress, claimed));
            }
        }
        // 按任务定义的 sort 顺序返回，前端展示才稳定
        Map<String, Integer> orderMap = new java.util.HashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            orderMap.put(definitions.get(i).getTaskCode(), i);
        }
        result.sort(java.util.Comparator.comparingInt(v -> orderMap.getOrDefault(v.getTaskCode(), Integer.MAX_VALUE)));
        return result;
    }

    @Override
    public UserTaskVO reportProgress(String taskCode, int delta) {
        return doReportProgress(UserContext.requireUserId(), taskCode, delta);
    }

    /**
     * 供其他业务域调用（签到、下单等）：进度上报失败不能影响主流程。
     * <p>例如运营把"每日签到"任务停用或删除后，签到仍然必须成功——
     * 任务只是激励手段，不能反过来卡住主业务。</p>
     */
    @Override
    public void reportProgressQuietly(Long userId, String taskCode, int delta) {
        if (userId == null || delta <= 0) {
            return;
        }
        try {
            doReportProgress(userId, taskCode, delta);
        } catch (Exception e) {
            log.warn("任务进度上报跳过 userId={} task={} reason={}", userId, taskCode, e.getMessage());
        }
    }

    /**
     * 进度上报核心逻辑（不依赖 UserContext，便于其他域本地调用）。
     */
    private UserTaskVO doReportProgress(Long userId, String taskCode, int delta) {
        if (delta <= 0) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "进度增量必须大于 0");
        }
        TaskDefinition definition = requireTask(taskCode);
        String periodKey = PeriodKeyUtil.resolve(definition.getTaskType(), LocalDate.now());
        String progressKey = RedisKeyConst.taskProgress(userId, periodKey);

        // 1. 热数据：Redis Hash 原子自增（毫秒级，页面刷新立即可见）
        Long progress = stringRedisTemplate.opsForHash().increment(progressKey, taskCode, delta);
        stringRedisTemplate.expire(progressKey, PeriodKeyUtil.ttlSeconds(definition.getTaskType()),
                java.util.concurrent.TimeUnit.SECONDS);
        int current = progress == null ? delta : progress.intValue();
        // 进度不允许超过目标值，避免前端进度条超过 100%
        if (current > definition.getTargetValue()) {
            stringRedisTemplate.opsForHash().put(progressKey, taskCode, String.valueOf(definition.getTargetValue()));
            current = definition.getTargetValue();
        }

        int status = current >= definition.getTargetValue() ? TaskStatus.FINISHED.getCode() : TaskStatus.DOING.getCode();

        // 2. 异步落库：事件体里带"最新进度"，消费端用 GREATEST 保证单调，重复消费无害
        TaskProgressEvent event = new TaskProgressEvent(userId, taskCode, periodKey, current,
                definition.getTargetValue(), status);
        eventPublisher.publish(MqTopicConst.TASK_PROGRESS_PERSIST, MqTopicConst.EventType.TASK_PROGRESS,
                userId + ":" + taskCode + ":" + periodKey, event);

        log.debug("任务进度上报 userId={} task={} period={} progress={}", userId, taskCode, periodKey, current);
        return buildVo(definition, periodKey, current, false);
    }

    /**
     * Redis 进度缺失时从 MySQL 归档回填。
     * <p>归档表是"异步落库"的产物，可能比 Redis 落后几秒，所以只做兜底：
     * 回填后 Redis 继续作为热数据，后续增量仍写 Redis。</p>
     */
    private Map<Object, Object> backfillFromArchive(Long userId, String periodKey, String progressKey) {
        try {
            List<UserTaskProgress> archived = userTaskProgressMapper.selectList(
                    new LambdaQueryWrapper<UserTaskProgress>()
                            .eq(UserTaskProgress::getUserId, userId)
                            .eq(UserTaskProgress::getPeriodKey, periodKey));
            if (archived.isEmpty()) {
                return Map.of();
            }
            Map<String, String> backfill = archived.stream()
                    .filter(item -> item.getProgress() != null && item.getProgress() > 0)
                    .collect(Collectors.toMap(UserTaskProgress::getTaskCode,
                            item -> String.valueOf(item.getProgress()), (a, b) -> a));
            if (backfill.isEmpty()) {
                return Map.of();
            }
            stringRedisTemplate.opsForHash().putAll(progressKey, backfill);
            log.info("任务进度从归档回填 userId={} period={} 条数={}", userId, periodKey, backfill.size());
            return new java.util.HashMap<>(backfill);
        } catch (Exception e) {
            log.warn("任务进度回填失败 userId={} period={} reason={}", userId, periodKey, e.getMessage());
            return Map.of();
        }
    }

    @Override
    public int claimReward(String taskCode) {
        Long userId = UserContext.requireUserId();
        TaskDefinition definition = requireTask(taskCode);
        String periodKey = PeriodKeyUtil.resolve(definition.getTaskType(), LocalDate.now());
        String progressKey = RedisKeyConst.taskProgress(userId, periodKey);
        String claimedKey = RedisKeyConst.taskClaimed(userId, periodKey);

        int progress = parseInt(stringRedisTemplate.opsForHash().get(progressKey, taskCode));
        if (progress < definition.getTargetValue()) {
            throw BizException.of(ErrorCode.TASK_NOT_FINISHED);
        }
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForHash().hasKey(claimedKey, taskCode))) {
            throw BizException.of(ErrorCode.TASK_REWARD_CLAIMED);
        }
        // 领取标记与积分入账：先用 HSETNX 抢占，避免并发重复领取
        Boolean first = stringRedisTemplate.opsForHash().putIfAbsent(claimedKey, taskCode, "1");
        if (!Boolean.TRUE.equals(first)) {
            throw BizException.of(ErrorCode.TASK_REWARD_CLAIMED);
        }
        stringRedisTemplate.expire(claimedKey, PeriodKeyUtil.ttlSeconds(definition.getTaskType()),
                java.util.concurrent.TimeUnit.SECONDS);

        // 积分流水用 taskCode:periodKey 作为幂等键，Redis 标记丢失时也不会重复发奖
        boolean added = pointService.addPoint(userId, PointBizType.TASK, taskCode + ":" + periodKey,
                definition.getPointAward(), "完成任务：" + definition.getTaskName());
        if (!added) {
            log.info("任务奖励已发放过 userId={} task={} period={}", userId, taskCode, periodKey);
        }
        return definition.getPointAward();
    }

    // ------------------------------------------------------------------
    // 管理端
    // ------------------------------------------------------------------

    @Override
    public com.campus.growth.common.result.PageResult<TaskDefinition> pageForAdmin(
            String keyword, Integer status, long page, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskDefinition> result =
                taskDefinitionMapper.selectPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(page, size),
                        new LambdaQueryWrapper<TaskDefinition>()
                                .like(org.springframework.util.StringUtils.hasText(keyword),
                                        TaskDefinition::getTaskName, keyword)
                                .eq(status != null, TaskDefinition::getStatus, status)
                                .orderByAsc(TaskDefinition::getSort));
        return com.campus.growth.common.result.PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public Long save(com.campus.growth.modules.task.dto.TaskSaveDTO dto) {
        TaskDefinition entity = new TaskDefinition();
        entity.setId(dto.getId());
        entity.setTaskCode(dto.getTaskCode());
        entity.setTaskName(dto.getTaskName());
        entity.setTaskType(dto.getTaskType());
        entity.setTargetValue(dto.getTargetValue());
        entity.setPointAward(dto.getPointAward());
        entity.setIcon(dto.getIcon());
        entity.setDescription(dto.getDescription());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        if (dto.getId() == null) {
            taskDefinitionMapper.insert(entity);
        } else {
            taskDefinitionMapper.updateById(entity);
        }
        evictDefinitionCache();
        return entity.getId();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        TaskDefinition update = new TaskDefinition();
        update.setId(id);
        update.setStatus(status);
        taskDefinitionMapper.updateById(update);
        evictDefinitionCache();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private TaskDefinition requireTask(String taskCode) {
        return loadDefinitions().stream()
                .filter(d -> d.getTaskCode().equals(taskCode))
                .findFirst()
                .orElseThrow(() -> BizException.of(ErrorCode.TASK_NOT_FOUND));
    }

    /** 任务定义读多写少，走二级缓存（Caffeine + Redis） */
    private List<TaskDefinition> loadDefinitions() {
        return twoLevelCache.getList(TASK_DEF_CACHE_KEY, TaskDefinition.class, TASK_DEF_TTL,
                () -> taskDefinitionMapper.selectList(new LambdaQueryWrapper<TaskDefinition>()
                        .eq(TaskDefinition::getStatus, 1)
                        .orderByAsc(TaskDefinition::getSort)));
    }

    /** 管理端保存任务后主动失效缓存 */
    public void evictDefinitionCache() {
        twoLevelCache.evict(TASK_DEF_CACHE_KEY);
    }

    private UserTaskVO buildVo(TaskDefinition definition, String periodKey, int progress, boolean claimed) {
        UserTaskVO vo = new UserTaskVO();
        vo.setTaskCode(definition.getTaskCode());
        vo.setTaskName(definition.getTaskName());
        vo.setTaskType(definition.getTaskType());
        vo.setIcon(definition.getIcon());
        vo.setDescription(definition.getDescription());
        vo.setProgress(progress);
        vo.setTargetValue(definition.getTargetValue());
        vo.setPointAward(definition.getPointAward());
        vo.setPeriodKey(periodKey);
        if (claimed) {
            vo.setStatus(TaskStatus.CLAIMED.getCode());
        } else if (progress >= definition.getTargetValue()) {
            vo.setStatus(TaskStatus.FINISHED.getCode());
        } else {
            vo.setStatus(TaskStatus.DOING.getCode());
        }
        int target = definition.getTargetValue() == null || definition.getTargetValue() <= 0
                ? 1 : definition.getTargetValue();
        vo.setPercent(Math.min(100, progress * 100 / target));
        return vo;
    }

    private int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
