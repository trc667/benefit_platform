package com.campus.growth.config;

import com.campus.growth.common.constant.MqTopicConst;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka 配置。
 *
 * <h3>三件事</h3>
 * <ol>
 *   <li><b>自动建 Topic</b>：KafkaAdmin 启动时按 {@link NewTopic} 创建，首次跑项目不用手动建主题；</li>
 *   <li><b>消费重试 + 死信</b>：业务异常重试 3 次（指数退避），仍失败则投递到
 *       {@code cg.dead.letter}，避免坏消息把分区堵死；</li>
 *   <li><b>手动提交</b>：listener ack-mode=manual_immediate，业务处理成功才提交位点，
 *       配合 {@code mq_consume_record} 唯一索引做到"至少一次投递 + 幂等消费"。</li>
 * </ol>
 *
 * <p>注意：反序列化异常这类"不可重试"的错误直接进死信，不浪费 3 次重试。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "campus.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    /** 启动时自动创建的主题（每个主题都有真实消费者，见各模块 mq 包） */
    @Bean
    public NewTopic signinSuccessTopic() {
        // 分区数 3：签到事件量大但处理轻量，3 个分区足够并行消费
        return new NewTopic(MqTopicConst.SIGNIN_SUCCESS, 3, (short) 1);
    }

    @Bean
    public NewTopic taskProgressTopic() {
        return new NewTopic(MqTopicConst.TASK_PROGRESS_PERSIST, 3, (short) 1);
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return new NewTopic(MqTopicConst.ORDER_PAID, 3, (short) 1);
    }

    @Bean
    public NewTopic deadLetterTopic() {
        // 死信只保留 1 个分区，人工处理量很小
        return new NewTopic(MqTopicConst.DEAD_LETTER, 1, (short) 1);
    }

    /**
     * 消费错误处理器：重试 3 次后进死信。
     * <p>DeadLetterPublishingRecoverer 默认发到 {@code <topic>.DLT}，
     * 这里改成统一死信主题，便于管理端集中处理。</p>
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(org.springframework.kafka.core.KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> {
                    log.error("消息消费最终失败，投递死信 topic={} key={}", record.topic(), record.key(), exception);
                    return new org.apache.kafka.common.TopicPartition(MqTopicConst.DEAD_LETTER, 0);
                });
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(8000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // 参数错误、反序列化失败重试没有意义，直接死信
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
