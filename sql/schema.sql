-- =====================================================================
-- 校园成长权益平台 · 数据库初始化脚本
-- 兼容性：MySQL 5.7.31+ / MySQL 8.0.x（刻意规避窗口函数、CTE、CHECK、JSON 函数）
-- 字符集：utf8mb4（5.7 无 utf8mb4_0900_ai_ci，统一用 utf8mb4_general_ci）
-- 约定：主键 BIGINT UNSIGNED 自增；逻辑删除 deleted TINYINT；时间列自带默认值
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `campus_growth`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `campus_growth`;

-- ---------------------------------------------------------------------
-- 1. 用户表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(64)     NOT NULL                COMMENT '登录账号',
  `password`        VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 密文',
  `nickname`        VARCHAR(64)     NOT NULL                COMMENT '昵称',
  `avatar`          VARCHAR(255)    DEFAULT NULL            COMMENT '头像地址',
  `phone`           VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
  `student_no`      VARCHAR(32)     DEFAULT NULL            COMMENT '学号',
  `school`          VARCHAR(64)     DEFAULT NULL            COMMENT '学校',
  `role`            VARCHAR(20)     NOT NULL DEFAULT 'STUDENT' COMMENT '角色 STUDENT/OPERATOR/ADMIN',
  `growth_level`    TINYINT         NOT NULL DEFAULT 1      COMMENT '成长等级（按累计积分换算）',
  `status`          TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0禁用',
  `last_login_time` DATETIME        DEFAULT NULL            COMMENT '最后登录时间',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_student_no` (`student_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表';

-- ---------------------------------------------------------------------
-- 2. 积分账户（与流水分离，账户行用乐观锁更新）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user_point_account`;
CREATE TABLE `user_point_account` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `balance`      INT             NOT NULL DEFAULT 0      COMMENT '可用积分',
  `total_earned` INT             NOT NULL DEFAULT 0      COMMENT '累计获得',
  `total_used`   INT             NOT NULL DEFAULT 0      COMMENT '累计消耗',
  `version`      INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分账户';

-- ---------------------------------------------------------------------
-- 3. 积分流水（(user_id,biz_type,biz_no) 唯一，天然幂等）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `point_record`;
CREATE TABLE `point_record` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `biz_type`      VARCHAR(32)     NOT NULL                COMMENT '业务类型 SIGNIN/TASK/ORDER_PAY/ORDER_REFUND/REDEEM/ADMIN',
  `biz_no`        VARCHAR(64)     NOT NULL                COMMENT '业务单号（签到日期、订单号、事件ID等）',
  `change_point`  INT             NOT NULL                COMMENT '变动积分（正加负减）',
  `balance_after` INT             NOT NULL                COMMENT '变动后余额',
  `remark`        VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_biz` (`user_id`, `biz_type`, `biz_no`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分流水';

-- ---------------------------------------------------------------------
-- 4. 签到流水（真源是 Redis BitMap，本表用于对账与连续天数统计）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sign_in_record`;
CREATE TABLE `sign_in_record` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `sign_date`       DATE            NOT NULL                COMMENT '签到日期',
  `continuous_days` INT             NOT NULL DEFAULT 1      COMMENT '连续签到天数',
  `point_award`     INT             NOT NULL DEFAULT 0      COMMENT '本次获得积分',
  `source`          VARCHAR(16)     NOT NULL DEFAULT 'APP'  COMMENT '来源 APP/WEB/ADMIN',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`),
  KEY `idx_date` (`sign_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '签到流水';

-- ---------------------------------------------------------------------
-- 5. 任务定义
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `task_definition`;
CREATE TABLE `task_definition` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_code`    VARCHAR(32)     NOT NULL                COMMENT '任务编码',
  `task_name`    VARCHAR(64)     NOT NULL                COMMENT '任务名称',
  `task_type`    VARCHAR(16)     NOT NULL                COMMENT 'DAILY/WEEKLY/ONCE',
  `target_value` INT             NOT NULL DEFAULT 1      COMMENT '目标值',
  `point_award`  INT             NOT NULL DEFAULT 0      COMMENT '完成奖励积分',
  `icon`         VARCHAR(64)     DEFAULT NULL            COMMENT '图标名',
  `description`  VARCHAR(255)    DEFAULT NULL            COMMENT '任务描述',
  `sort`         INT             NOT NULL DEFAULT 0,
  `status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务定义';

-- ---------------------------------------------------------------------
-- 6. 用户任务进度（Redis 热数据异步落库的归档表）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user_task_progress`;
CREATE TABLE `user_task_progress` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `task_code`    VARCHAR(32)     NOT NULL                COMMENT '任务编码',
  `period_key`   VARCHAR(16)     NOT NULL                COMMENT '周期键 20260909/2026W37/ONCE',
  `progress`     INT             NOT NULL DEFAULT 0      COMMENT '当前进度',
  `target_value` INT             NOT NULL DEFAULT 1      COMMENT '目标值快照',
  `status`       TINYINT         NOT NULL DEFAULT 0      COMMENT '0进行中 1已完成 2已发奖',
  `finish_time`  DATETIME        DEFAULT NULL,
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_task_period` (`user_id`, `task_code`, `period_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户任务进度';

-- ---------------------------------------------------------------------
-- 7. 权益商品
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `benefit_goods`;
CREATE TABLE `benefit_goods` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `goods_code`   VARCHAR(32)     NOT NULL                COMMENT '商品编码',
  `title`        VARCHAR(128)    NOT NULL                COMMENT '商品标题',
  `sub_title`    VARCHAR(255)    DEFAULT NULL            COMMENT '副标题',
  `cover_url`    VARCHAR(255)    DEFAULT NULL            COMMENT '封面',
  `category`     VARCHAR(32)     NOT NULL DEFAULT 'OTHER' COMMENT '分类 STUDY/FOOD/LIFE/SPORT/OTHER',
  `price_point`  INT             NOT NULL                COMMENT '所需积分',
  `origin_price` INT             NOT NULL DEFAULT 0      COMMENT '原价（分）',
  `stock`        INT             NOT NULL DEFAULT 0      COMMENT '库存',
  `sold_count`   INT             NOT NULL DEFAULT 0      COMMENT '已售',
  `tags`         VARCHAR(255)    DEFAULT NULL            COMMENT '标签，逗号分隔',
  `detail`       TEXT                                    COMMENT '详情',
  `start_time`   DATETIME        DEFAULT NULL,
  `end_time`     DATETIME        DEFAULT NULL,
  `sort`         INT             NOT NULL DEFAULT 0,
  `status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1上架 0下架',
  `version`      INT             NOT NULL DEFAULT 0      COMMENT '乐观锁',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_code` (`goods_code`),
  KEY `idx_status_sort` (`status`, `sort`),
  KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '权益商品';

-- ---------------------------------------------------------------------
-- 8. 优惠券模板
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `coupon_template`;
CREATE TABLE `coupon_template` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `template_code`  VARCHAR(32)     NOT NULL                COMMENT '模板编码',
  `title`          VARCHAR(64)     NOT NULL                COMMENT '券名称',
  `coupon_type`    VARCHAR(20)     NOT NULL                COMMENT 'CASH满减/DISCOUNT折扣/DIRECT无门槛',
  `face_value`     INT             NOT NULL DEFAULT 0      COMMENT '面额（积分）',
  `discount_rate`  INT             NOT NULL DEFAULT 100    COMMENT '折扣率*100，如85=8.5折',
  `threshold_point` INT            NOT NULL DEFAULT 0      COMMENT '使用门槛（订单积分）',
  `max_discount`   INT             NOT NULL DEFAULT 0      COMMENT '最高抵扣，0 表示不限',
  `scope_type`     VARCHAR(16)     NOT NULL DEFAULT 'ALL'  COMMENT 'ALL/GOODS/CATEGORY',
  `scope_value`    VARCHAR(255)    DEFAULT NULL            COMMENT '适用范围值，逗号分隔',
  `total_count`    INT             NOT NULL DEFAULT 0      COMMENT '发行总量',
  `issued_count`   INT             NOT NULL DEFAULT 0      COMMENT '已发放',
  `per_user_limit` INT             NOT NULL DEFAULT 1      COMMENT '单人限领',
  `valid_type`     VARCHAR(16)     NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED固定区间/RELATIVE领取后N天',
  `start_time`     DATETIME        DEFAULT NULL,
  `end_time`       DATETIME        DEFAULT NULL,
  `valid_days`     INT             NOT NULL DEFAULT 0      COMMENT 'RELATIVE 模式有效天数',
  `status`         TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `version`        INT             NOT NULL DEFAULT 0      COMMENT '乐观锁',
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`),
  KEY `idx_status_time` (`status`, `start_time`, `end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券模板';

-- ---------------------------------------------------------------------
-- 9. 用户优惠券
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `template_id`    BIGINT UNSIGNED NOT NULL                COMMENT '券模板ID',
  `coupon_code`    VARCHAR(32)     NOT NULL                COMMENT '券码（Base32 生成）',
  `coupon_title`   VARCHAR(64)     NOT NULL                COMMENT '名称快照',
  `coupon_type`    VARCHAR(20)     NOT NULL                COMMENT '类型快照',
  `face_value`     INT             NOT NULL DEFAULT 0      COMMENT '面额快照',
  `discount_rate`  INT             NOT NULL DEFAULT 100    COMMENT '折扣率快照',
  `threshold_point` INT            NOT NULL DEFAULT 0      COMMENT '门槛快照',
  `max_discount`   INT             NOT NULL DEFAULT 0      COMMENT '最高抵扣快照',
  `scope_type`     VARCHAR(16)     NOT NULL DEFAULT 'ALL',
  `scope_value`    VARCHAR(255)    DEFAULT NULL,
  `status`         VARCHAR(16)     NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
  `source`         VARCHAR(16)     NOT NULL DEFAULT 'RECEIVE' COMMENT 'RECEIVE/REDEEM/ADMIN',
  `receive_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `use_time`       DATETIME        DEFAULT NULL,
  `expire_time`    DATETIME        NOT NULL                COMMENT '过期时间',
  `order_no`       VARCHAR(32)     DEFAULT NULL            COMMENT '核销订单号',
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_code` (`coupon_code`),
  KEY `idx_user_status` (`user_id`, `status`, `expire_time`),
  KEY `idx_template` (`template_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户优惠券';

-- ---------------------------------------------------------------------
-- 10. 兑换码批次（核销状态真源为 Redis BitMap：key=redeem:used:{batchNo}）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `redeem_code_batch`;
CREATE TABLE `redeem_code_batch` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_no`    VARCHAR(24)     NOT NULL                COMMENT '批次号（含时间戳+随机段）',
  `title`       VARCHAR(64)     NOT NULL                COMMENT '批次名称',
  `biz_type`    VARCHAR(20)     NOT NULL                COMMENT 'POINT/COUPON/GOODS',
  `ref_id`      BIGINT UNSIGNED DEFAULT NULL            COMMENT '关联券模板/商品ID',
  `reward_value` INT            NOT NULL DEFAULT 0      COMMENT '奖励值（积分/券数量）',
  `total_count` INT             NOT NULL                COMMENT '码总量',
  `used_count`  INT             NOT NULL DEFAULT 0      COMMENT '已核销数',
  `start_time`  DATETIME        DEFAULT NULL,
  `end_time`    DATETIME        DEFAULT NULL,
  `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `remark`      VARCHAR(255)    DEFAULT NULL,
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '兑换码批次';

-- ---------------------------------------------------------------------
-- 11. 兑换码核销记录（按需插入，不做预生成）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `redeem_code`;
CREATE TABLE `redeem_code` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_no`    VARCHAR(24)     NOT NULL                COMMENT '批次号',
  `code`        VARCHAR(32)     NOT NULL                COMMENT '兑换码',
  `seq_no`      BIGINT UNSIGNED NOT NULL                COMMENT '序号，对应 BitMap offset',
  `user_id`     BIGINT UNSIGNED DEFAULT NULL            COMMENT '核销用户',
  `status`      TINYINT         NOT NULL DEFAULT 0      COMMENT '0未用 1已用',
  `use_time`    DATETIME        DEFAULT NULL,
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_batch_seq` (`batch_no`, `seq_no`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '兑换码核销记录';

-- ---------------------------------------------------------------------
-- 12. 订单主表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `order_main`;
CREATE TABLE `order_main` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`          VARCHAR(32)     NOT NULL                COMMENT '订单号',
  `user_id`           BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `order_type`        VARCHAR(16)     NOT NULL DEFAULT 'GOODS' COMMENT 'GOODS',
  `goods_total_point` INT             NOT NULL DEFAULT 0      COMMENT '商品总额',
  `discount_point`    INT             NOT NULL DEFAULT 0      COMMENT '优惠总额',
  `pay_point`         INT             NOT NULL DEFAULT 0      COMMENT '实付积分',
  `coupon_id`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '使用的券ID',
  `coupon_code`       VARCHAR(32)     DEFAULT NULL            COMMENT '券码',
  `discount_snapshot` VARCHAR(1024)   DEFAULT NULL            COMMENT '优惠方案快照（最优组合JSON文本）',
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/CANCELLED/FINISHED',
  `receiver_name`     VARCHAR(64)     DEFAULT NULL,
  `receiver_phone`    VARCHAR(20)     DEFAULT NULL,
  `receiver_address`  VARCHAR(255)    DEFAULT NULL,
  `remark`            VARCHAR(255)    DEFAULT NULL,
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time`          DATETIME        DEFAULT NULL,
  `cancel_time`       DATETIME        DEFAULT NULL,
  `finish_time`       DATETIME        DEFAULT NULL,
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version`           INT             NOT NULL DEFAULT 0      COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单主表';

-- ---------------------------------------------------------------------
-- 13. 订单明细
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`      BIGINT UNSIGNED NOT NULL,
  `order_no`      VARCHAR(32)     NOT NULL,
  `goods_id`      BIGINT UNSIGNED NOT NULL,
  `goods_code`    VARCHAR(32)     NOT NULL                COMMENT '商品编码快照',
  `goods_title`   VARCHAR(128)    NOT NULL                COMMENT '标题快照',
  `goods_cover`   VARCHAR(255)    DEFAULT NULL,
  `unit_point`    INT             NOT NULL                COMMENT '单价积分快照',
  `quantity`      INT             NOT NULL DEFAULT 1,
  `item_discount` INT             NOT NULL DEFAULT 0      COMMENT '行优惠',
  `item_amount`   INT             NOT NULL DEFAULT 0      COMMENT '行实付',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_goods_id` (`goods_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单明细';

-- ---------------------------------------------------------------------
-- 14. 退换单
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `order_refund`;
CREATE TABLE `order_refund` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `refund_no`     VARCHAR(32)     NOT NULL                COMMENT '退换单号',
  `order_no`      VARCHAR(32)     NOT NULL                COMMENT '订单号',
  `order_id`      BIGINT UNSIGNED NOT NULL,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `refund_type`   VARCHAR(16)     NOT NULL DEFAULT 'RETURN' COMMENT 'RETURN退货/EXCHANGE换货',
  `reason`        VARCHAR(255)    NOT NULL                COMMENT '申请原因',
  `status`        VARCHAR(16)     NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED/APPROVED/REJECTED/REFUNDED',
  `refund_point`  INT             NOT NULL DEFAULT 0      COMMENT '退回积分',
  `handle_remark` VARCHAR(255)    DEFAULT NULL            COMMENT '处理备注',
  `handler_id`    BIGINT UNSIGNED DEFAULT NULL            COMMENT '处理人',
  `apply_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handle_time`   DATETIME        DEFAULT NULL,
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_status_time` (`status`, `apply_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '退换单';

-- ---------------------------------------------------------------------
-- 15. AI 会话
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_chat_session`;
CREATE TABLE `ai_chat_session` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id`    VARCHAR(32)     NOT NULL                COMMENT '会话ID',
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `title`         VARCHAR(64)     DEFAULT NULL            COMMENT '会话标题（首句截断）',
  `model`         VARCHAR(64)     DEFAULT NULL            COMMENT '使用的模型',
  `message_count` INT             NOT NULL DEFAULT 0,
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 会话';

-- ---------------------------------------------------------------------
-- 16. AI 消息（含工具调用审计）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id`        VARCHAR(32)     NOT NULL,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `role`              VARCHAR(16)     NOT NULL                COMMENT 'system/user/assistant/tool',
  `content`           TEXT                                    COMMENT '内容',
  `tool_name`         VARCHAR(64)     DEFAULT NULL            COMMENT '工具名',
  `tool_args`         VARCHAR(1000)   DEFAULT NULL            COMMENT '工具入参JSON',
  `tool_result`       TEXT                                    COMMENT '工具返回JSON',
  `prompt_tokens`     INT             NOT NULL DEFAULT 0,
  `completion_tokens` INT             NOT NULL DEFAULT 0,
  `cost_ms`           INT             NOT NULL DEFAULT 0,
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 消息';

-- ---------------------------------------------------------------------
-- 17. 本地消息表（Kafka 事件最终必达，定时补偿重试）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_event_outbox`;
CREATE TABLE `mq_event_outbox` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id`        VARCHAR(32)     NOT NULL                COMMENT '事件ID（消费端幂等键）',
  `topic`           VARCHAR(64)     NOT NULL,
  `event_type`      VARCHAR(32)     NOT NULL,
  `biz_key`         VARCHAR(64)     DEFAULT NULL            COMMENT '业务键，如 userId:date',
  `payload`         TEXT            NOT NULL                COMMENT '事件体JSON',
  `status`          VARCHAR(16)     NOT NULL DEFAULT 'NEW'  COMMENT 'NEW/SENT/FAILED/DEAD',
  `retry_count`     INT             NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME        DEFAULT NULL,
  `last_error`      VARCHAR(500)    DEFAULT NULL,
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`),
  KEY `idx_topic_time` (`topic`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '本地消息表';

-- ---------------------------------------------------------------------
-- 18. 消息消费幂等记录
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_consume_record`;
CREATE TABLE `mq_consume_record` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id`       VARCHAR(32)     NOT NULL,
  `consumer_group` VARCHAR(64)     NOT NULL,
  `topic`          VARCHAR(64)     NOT NULL,
  `status`         TINYINT         NOT NULL DEFAULT 1      COMMENT '1成功 0失败',
  `error_msg`      VARCHAR(500)    DEFAULT NULL,
  `cost_ms`        INT             NOT NULL DEFAULT 0,
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_group`),
  KEY `idx_topic_time` (`topic`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息消费幂等记录';

-- ---------------------------------------------------------------------
-- 19. 操作日志（@OpLog 切面自动落库）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED DEFAULT NULL,
  `username`    VARCHAR(64)     DEFAULT NULL,
  `module`      VARCHAR(32)     NOT NULL                COMMENT '模块',
  `action`      VARCHAR(64)     NOT NULL                COMMENT '动作',
  `method`      VARCHAR(255)    DEFAULT NULL            COMMENT '类方法',
  `params`      TEXT                                    COMMENT '入参',
  `result_code` INT             NOT NULL DEFAULT 0,
  `error_msg`   VARCHAR(500)    DEFAULT NULL,
  `cost_ms`     INT             NOT NULL DEFAULT 0,
  `ip`          VARCHAR(64)     DEFAULT NULL,
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_module_time` (`module`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志';
