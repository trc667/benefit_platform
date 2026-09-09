package com.campus.growth.common.constant;

/**
 * Kafka 主题常量。
 *
 * <p>命名规范：{@code cg.<域>.<事件>}；所有主题都通过本地消息表 {@code mq_event_outbox} 投递，保证最终必达。</p>
 *
 * <p><b>只保留"有真实消费者"的主题。</b>曾经声明过 task-completed / order-cancelled /
 * refund-finished / point-changed 四个主题，但它们只发不收（取消订单、退款、发奖励都是本地事务里
 * 同步做完了），留着就是"死事件"——白白写 outbox、占 Kafka 分区，还让文档和实现对不上。
 * 需要时再加：加主题的成本很低，维护一堆没人消费的事件成本很高。</p>
 */
public final class MqTopicConst {

    private MqTopicConst() {
    }

    /** 签到成功 → 积分模块异步加积分、更新排行榜 */
    public static final String SIGNIN_SUCCESS = "cg.signin.success";
    /** 任务进度变更 → 异步落库（Redis 热数据 → MySQL 归档） */
    public static final String TASK_PROGRESS_PERSIST = "cg.task.progress.persist";
    /** 订单支付成功 → 异步推进"本周兑换一次"任务进度 */
    public static final String ORDER_PAID = "cg.order.paid";
    /** 死信主题：消费重试仍失败的事件进入这里，由管理端人工处理 */
    public static final String DEAD_LETTER = "cg.dead.letter";

    /** 消费组 */
    public static final String GROUP_POINT = "cg-group-point";
    public static final String GROUP_TASK = "cg-group-task";
    public static final String GROUP_ORDER = "cg-group-order";

    /** 事件类型（写入 outbox.event_type，消费端据此路由） */
    public static final class EventType {
        private EventType() {
        }

        public static final String SIGNIN_SUCCESS = "SIGNIN_SUCCESS";
        public static final String TASK_PROGRESS = "TASK_PROGRESS";
        public static final String ORDER_PAID = "ORDER_PAID";
    }
}
