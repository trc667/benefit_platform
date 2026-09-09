package com.campus.growth.modules.task.mq;

import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.infra.mq.EventEnvelope;
import com.campus.growth.modules.mq.service.IdempotentConsumerSupport;
import com.campus.growth.modules.task.entity.UserTaskProgress;
import com.campus.growth.modules.task.mapper.UserTaskProgressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务进度落库消费者（Redis 热数据 → MySQL 归档）。
 *
 * <h3>幂等与单调性</h3>
 * <p>进度事件可能重复、也可能乱序（多分区并发消费）。这里不依赖"事件顺序"，
 * 而是让落库操作本身幂等且单调：</p>
 * <pre>
 * 已存在 → UPDATE progress = GREATEST(progress, 事件进度)
 * 不存在 → INSERT
 * </pre>
 * <p>这样即使先处理了 5、再处理 3，最终进度仍是 5，不会倒退。</p>
 *
 * <h3>批量写入</h3>
 * <p>listener 配置为 batch 模式（{@code max.poll.records=100}），
 * 一批消息在一个事务里处理，减少数据库交互次数。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressConsumer {

    private final UserTaskProgressMapper progressMapper;
    private final IdempotentConsumerSupport idempotentSupport;

    @KafkaListener(topics = MqTopicConst.TASK_PROGRESS_PERSIST, groupId = MqTopicConst.GROUP_TASK)
    @Transactional(rollbackFor = Exception.class)
    public void onTaskProgress(List<String> messages, Acknowledgment ack) {
        for (int index = 0; index < messages.size(); index++) {
            try {
                EventEnvelope<?> envelope = JsonUtil.parse(messages.get(index), EventEnvelope.class);
                if (envelope == null || envelope.getEventId() == null) {
                    continue;
                }
                TaskProgressEvent event = JsonUtil.mapper()
                        .convertValue(envelope.getPayload(), TaskProgressEvent.class);
                if (event == null || event.getUserId() == null || event.getTaskCode() == null) {
                    continue;
                }
                if (!idempotentSupport.tryMarkConsuming(envelope.getEventId(),
                        MqTopicConst.GROUP_TASK, MqTopicConst.TASK_PROGRESS_PERSIST)) {
                    continue;
                }
                upsert(event);
            } catch (Exception e) {
                log.error("任务进度落库失败 index={}", index, e);
                throw new BatchListenerFailedException("任务进度落库失败", e, index);
            }
        }
        ack.acknowledge();
    }

    /**
     * 单调 upsert。
     * <p>MyBatis-Plus 没有通用的 upsert，这里用"先查后写"实现，
     * 由唯一索引 {@code uk_user_task_period} 兜住并发插入冲突。</p>
     */
    private void upsert(TaskProgressEvent event) {
        UserTaskProgress existing = progressMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTaskProgress>()
                        .eq(UserTaskProgress::getUserId, event.getUserId())
                        .eq(UserTaskProgress::getTaskCode, event.getTaskCode())
                        .eq(UserTaskProgress::getPeriodKey, event.getPeriodKey()));
        if (existing == null) {
            UserTaskProgress entity = new UserTaskProgress();
            entity.setUserId(event.getUserId());
            entity.setTaskCode(event.getTaskCode());
            entity.setPeriodKey(event.getPeriodKey());
            entity.setProgress(event.getProgress());
            entity.setTargetValue(event.getTargetValue());
            entity.setStatus(event.getStatus());
            if (event.getStatus() != null && event.getStatus() == 1) {
                entity.setFinishTime(LocalDateTime.now());
            }
            try {
                progressMapper.insert(entity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发插入冲突：必须重新查一次拿到真实主键再更新。
                // （早期版本这里传了一个 id=null 的占位对象，updateProgress 首行就 return，
                //   导致进度更新被静默丢弃——这是很隐蔽的丢数据。）
                UserTaskProgress latest = progressMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTaskProgress>()
                                .eq(UserTaskProgress::getUserId, event.getUserId())
                                .eq(UserTaskProgress::getTaskCode, event.getTaskCode())
                                .eq(UserTaskProgress::getPeriodKey, event.getPeriodKey()));
                if (latest != null) {
                    updateProgress(latest, event);
                } else {
                    log.warn("并发插入冲突后仍未查到进度记录 userId={} task={} period={}",
                            event.getUserId(), event.getTaskCode(), event.getPeriodKey());
                }
            }
            return;
        }
        updateProgress(existing, event);
    }

    private void updateProgress(UserTaskProgress existing, TaskProgressEvent event) {
        if (existing.getId() == null) {
            return;
        }
        // 进度只增不减：乱序事件不会让进度倒退
        int finalProgress = Math.max(existing.getProgress() == null ? 0 : existing.getProgress(),
                event.getProgress() == null ? 0 : event.getProgress());
        UserTaskProgress update = new UserTaskProgress();
        update.setId(existing.getId());
        update.setProgress(finalProgress);
        update.setStatus(finalProgress >= (event.getTargetValue() == null ? 1 : event.getTargetValue()) ? 1 : 0);
        if (update.getStatus() == 1 && existing.getFinishTime() == null) {
            update.setFinishTime(LocalDateTime.now());
        }
        progressMapper.updateById(update);
    }
}
