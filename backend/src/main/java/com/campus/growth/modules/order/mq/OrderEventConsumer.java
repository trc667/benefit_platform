package com.campus.growth.modules.order.mq;

import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.infra.mq.EventEnvelope;
import com.campus.growth.modules.mq.service.IdempotentConsumerSupport;
import com.campus.growth.modules.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单支付事件消费者。
 *
 * <h3>为什么支付成功要发事件，而不是直接在 pay() 里同步调</h3>
 * <p>支付是核心链路，只该做"扣积分 + 改订单状态"两件事。任务进度、通知、对账这些
 * 属于"支付之后的连带动作"，放进来会让支付接口越来越重、越来越难排查。
 * 发一条 {@code cg.order.paid} 事件，连带动作各自订阅，支付链路保持干净。</p>
 *
 * <h3>本消费者做的事</h3>
 * <p>推进「本周兑换一次」任务进度（Redis 热数据 + 异步落库）。</p>
 *
 * <h3>一致性取舍</h3>
 * <p>进度上报是"尽力而为"：{@link TaskService#reportProgressQuietly} 内部吞掉异常，
 * 因为任务只是激励手段，不能反过来把支付/消费链路拖垮。
 * Kafka 不可用时事件先落在本地消息表，恢复后由补偿任务补发。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    /** 任务编码：本周兑换一次 */
    private static final String TASK_WEEKLY_ORDER = "WEEKLY_ORDER";

    private final IdempotentConsumerSupport idempotentSupport;
    private final TaskService taskService;

    @KafkaListener(topics = MqTopicConst.ORDER_PAID, groupId = MqTopicConst.GROUP_ORDER)
    @Transactional(rollbackFor = Exception.class)
    public void onOrderPaid(List<String> messages, Acknowledgment ack) {
        for (int index = 0; index < messages.size(); index++) {
            String message = messages.get(index);
            try {
                EventEnvelope<?> envelope = JsonUtil.parse(message, EventEnvelope.class);
                if (envelope == null || envelope.getEventId() == null) {
                    log.warn("订单支付事件体无法解析，跳过: {}", JsonUtil.abbreviate(message));
                    continue;
                }
                OrderPaidEvent event = JsonUtil.mapper()
                        .convertValue(envelope.getPayload(), OrderPaidEvent.class);
                if (event == null || event.getUserId() == null) {
                    log.warn("订单支付事件缺少业务字段，跳过 eventId={}", envelope.getEventId());
                    continue;
                }
                // 幂等登记与后续操作同一事务，避免"登记成功但业务失败"导致事件被丢弃
                if (!idempotentSupport.tryMarkConsuming(envelope.getEventId(),
                        MqTopicConst.GROUP_ORDER, MqTopicConst.ORDER_PAID)) {
                    continue;
                }
                taskService.reportProgressQuietly(event.getUserId(), TASK_WEEKLY_ORDER, 1);
                log.info("订单支付事件已消费 orderNo={} userId={} payPoint={}", event.getOrderNo(),
                        event.getUserId(), event.getPayPoint());
            } catch (Exception e) {
                log.error("处理订单支付事件失败 index={}", index, e);
                throw new BatchListenerFailedException("订单支付事件处理失败", e, index);
            }
        }
        ack.acknowledge();
    }
}
