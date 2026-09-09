package com.campus.growth.modules.point.mq;

import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.infra.mq.EventEnvelope;
import com.campus.growth.modules.mq.service.IdempotentConsumerSupport;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.signin.mq.SignInSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 签到成功事件消费者：异步加积分 + 更新排行榜。
 *
 * <h3>为什么要异步</h3>
 * <p>签到高峰（比如 23:50-23:59）会在极短时间内产生大量写请求。
 * 同步写积分账户意味着每笔签到都要更新同一批热点行，MySQL 行锁竞争会非常严重。
 * 异步化之后签到接口只做"位图 + 一条流水"，积分入账由消费者按自己的吞吐节奏处理。</p>
 *
 * <h3>幂等</h3>
 * <p>Kafka 至少一次投递 + 消费者重启，重复消费不可避免。两层保证：</p>
 * <ol>
 *   <li>{@code mq_consume_record} 唯一索引：同 eventId 只处理一次；</li>
 *   <li>{@code point_record} 唯一索引 {@code (user_id, biz_type, biz_no)}：
 *       即便幂等记录被误删，积分也不会重复入账。</li>
 * </ol>
 *
 * <h3>失败处理</h3>
 * <p>抛 {@link BatchListenerFailedException} 告诉框架"第几条失败"，
 * 由 KafkaConfig 里的 DefaultErrorHandler 重试 3 次后投递死信，
 * 避免坏消息把分区堵死。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignInEventConsumer {

    private final IdempotentConsumerSupport idempotentSupport;
    private final PointService pointService;

    @KafkaListener(topics = MqTopicConst.SIGNIN_SUCCESS, groupId = MqTopicConst.GROUP_POINT)
    @Transactional(rollbackFor = Exception.class)
    public void onSignInSuccess(List<String> messages, Acknowledgment ack) {
        for (int index = 0; index < messages.size(); index++) {
            String message = messages.get(index);
            try {
                EventEnvelope<?> envelope = JsonUtil.parse(message, EventEnvelope.class);
                if (envelope == null || envelope.getEventId() == null) {
                    log.warn("签到事件体无法解析，跳过: {}", JsonUtil.abbreviate(message));
                    continue;
                }
                SignInSuccessEvent event = JsonUtil.mapper()
                        .convertValue(envelope.getPayload(), SignInSuccessEvent.class);
                if (event == null || event.getUserId() == null) {
                    log.warn("签到事件缺少业务字段，跳过 eventId={}", envelope.getEventId());
                    continue;
                }
                // 幂等登记（与积分入账同一事务：登记成功但入账失败会一起回滚，消息可重试）
                if (!idempotentSupport.tryMarkConsuming(envelope.getEventId(),
                        MqTopicConst.GROUP_POINT, MqTopicConst.SIGNIN_SUCCESS)) {
                    continue;
                }
                boolean first = pointService.addPoint(event.getUserId(), PointBizType.SIGNIN,
                        event.getSignDate(), event.getTotalAward(),
                        "签到奖励（连续 " + event.getContinuousDays() + " 天）");
                log.info("签到积分入账 userId={} date={} point={} first={}", event.getUserId(),
                        event.getSignDate(), event.getTotalAward(), first);
            } catch (Exception e) {
                log.error("处理签到事件失败 index={}", index, e);
                throw new BatchListenerFailedException("签到积分入账失败", e, index);
            }
        }
        // 全部处理成功后手动提交位点
        ack.acknowledge();
    }
}
