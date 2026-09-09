-- =====================================================================
-- 校园成长权益平台 · 初始化数据
-- 说明：密码统一为 123456 的 BCrypt 密文；运营/管理员账号用于管理端演示
-- =====================================================================
USE `campus_growth`;

-- ---------------------------------------------------------------------
-- 用户：1 管理员 + 1 运营 + 6 学生
-- BCrypt(123456)，每次加密盐值不同，此处为固定演示值
-- ---------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `phone`, `student_no`, `school`, `role`, `growth_level`, `status`) VALUES
(1, 'admin',    '$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '平台管理员', NULL, '13800000001', NULL,        '校园成长平台', 'ADMIN',    1, 1),
(2, 'operator', '$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '运营小李',   NULL, '13800000002', NULL,        '校园成长平台', 'OPERATOR', 1, 1),
(3, 'student01','$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '林清和',     NULL, '13900000001', '2023010101', '示范大学',     'STUDENT',  3, 1),
(4, 'student02','$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '周斯年',     NULL, '13900000002', '2023010102', '示范大学',     'STUDENT',  2, 1),
(5, 'student03','$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '陈屿',       NULL, '13900000003', '2023010103', '示范大学',     'STUDENT',  2, 1),
(6, 'student04','$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '苏晚棠',     NULL, '13900000004', '2023010104', '示范大学',     'STUDENT',  1, 1),
(7, 'student05','$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '许听白',     NULL, '13900000005', '2023010105', '示范大学',     'STUDENT',  1, 1),
(8, 'student06','$2a$10$otGS8Ljyztkl2rYeeNLaBe0hI3o2ziC/pK41JC7MwQMZux9mA.ekK', '孟星野',     NULL, '13900000006', '2023010106', '示范大学',     'STUDENT',  1, 1);

-- 积分账户（初始余额，便于排行榜/下单演示）
INSERT INTO `user_point_account` (`user_id`, `balance`, `total_earned`, `total_used`) VALUES
(1, 0,    0,    0),
(2, 0,    0,    0),
(3, 2600, 2600, 0),
(4, 1800, 1800, 0),
(5, 1450, 1450, 0),
(6, 900,  900,  0),
(7, 600,  600,  0),
(8, 320,  320,  0);

-- ---------------------------------------------------------------------
-- 任务定义
-- ---------------------------------------------------------------------
INSERT INTO `task_definition` (`task_code`, `task_name`, `task_type`, `target_value`, `point_award`, `icon`, `description`, `sort`, `status`) VALUES
('DAILY_SIGN_IN',   '每日签到',     'DAILY',  1,  10, 'Calendar',  '每天签到一次，连续签到有额外奖励',        1, 1),
('DAILY_BROWSE',    '浏览权益商城', 'DAILY',  3,  5,  'Goods',     '浏览 3 个权益商品',                      2, 1),
('DAILY_SHARE',     '分享权益',     'DAILY',  1,  8,  'Share',     '分享任意权益给同学',                     3, 1),
('WEEKLY_ORDER',    '本周兑换一次', 'WEEKLY', 1,  30, 'ShoppingCart', '本周完成一次权益兑换',                4, 1),
('WEEKLY_COMMENT',  '评价兑换体验', 'WEEKLY', 2,  20, 'ChatDotRound', '对已兑换权益发表 2 条评价',           5, 1),
('ONCE_PROFILE',    '完善个人资料', 'ONCE',   1,  50, 'User',      '补全学校与学号信息',                     6, 1);

-- ---------------------------------------------------------------------
-- 权益商品
-- ---------------------------------------------------------------------
INSERT INTO `benefit_goods` (`goods_code`, `title`, `sub_title`, `cover_url`, `category`, `price_point`, `origin_price`, `stock`, `sold_count`, `tags`, `detail`, `sort`, `status`) VALUES
('GD1001', '图书馆研修间 2 小时券', '单人独立研修间，含插座与台灯', '/img/goods/study-room.png', 'STUDY', 300, 2000, 200, 12, '学习,安静', '可预约图书馆 3 楼研修间，单次 2 小时，凭券码到前台核销。', 1, 1),
('GD1002', '校园咖啡券（中杯）',    '校内三食堂咖啡吧通用',         '/img/goods/coffee.png',     'FOOD',  150, 1200, 500, 88, '饮品,通用', '中杯美式/拿铁二选一，有效期 30 天。', 2, 1),
('GD1003', '打印复印 200 页额度',   '覆盖黑白打印与复印',           '/img/goods/print.png',      'STUDY', 120, 800,  300, 45, '学习,实用', '校园自助打印点通用，额度不找零。', 3, 1),
('GD1004', '健身房单次入场券',      '含器械区与淋浴',               '/img/goods/gym.png',        'SPORT', 260, 1800, 150, 20, '运动,健康', '体育馆 1 楼，单次入场，需携带学生证。', 4, 1),
('GD1005', '定制帆布包',            '校园文创联名款',               '/img/goods/bag.png',        'LIFE',  480, 3900, 80,  6,  '文创,限量', '帆布材质，尺寸 38x42cm，限量 80 件。', 5, 1),
('GD1006', '洗衣房 5 次卡',         '宿舍楼洗衣房通用',             '/img/goods/laundry.png',    'LIFE',  200, 1500, 260, 33, '生活,宿舍', '5 次洗衣额度，扫码核销。', 6, 1),
('GD1007', '自习室月卡',            '24 小时自习室',                '/img/goods/study-card.png', 'STUDY', 900, 6000, 50,  3,  '学习,月卡', '一个月内不限次数进入 24 小时自习室。', 7, 1),
('GD1008', '校园周边手账本',        '含校徽烫金封面',               '/img/goods/notebook.png',   'LIFE',  360, 2800, 120, 11, '文创,文具', 'A5 手账本 128 页，附赠贴纸一套。', 8, 1);

-- ---------------------------------------------------------------------
-- 优惠券模板
-- ---------------------------------------------------------------------
INSERT INTO `coupon_template` (`template_code`, `title`, `coupon_type`, `face_value`, `discount_rate`, `threshold_point`, `max_discount`, `scope_type`, `scope_value`, `total_count`, `issued_count`, `per_user_limit`, `valid_type`, `start_time`, `end_time`, `valid_days`, `status`) VALUES
('CT2026001', '新人无门槛 50 积分券', 'DIRECT',   50,  100, 0,   0,   'ALL',      NULL,          10000, 0, 1, 'RELATIVE', NULL,                NULL,                7,  1),
('CT2026002', '满 300 减 60',        'CASH',     60,  100, 300, 0,   'ALL',      NULL,          5000,  0, 2, 'RELATIVE', NULL,                NULL,                30, 1),
('CT2026003', '学习类 8.5 折',        'DISCOUNT', 0,   85,  100, 200, 'CATEGORY', 'STUDY',       2000,  0, 1, 'RELATIVE', NULL,                NULL,                15, 1),
('CT2026004', '满 500 减 120',       'CASH',     120, 100, 500, 0,   'ALL',      NULL,          3000,  0, 1, 'FIXED',    '2026-01-01 00:00:00', '2030-12-31 23:59:59', 0, 1),
('CT2026005', '文创专区 9 折',        'DISCOUNT', 0,   90,  200, 100, 'CATEGORY', 'LIFE',        1500,  0, 1, 'RELATIVE', NULL,                NULL,                20, 1),
('CT2026006', '满 800 减 200',       'CASH',     200, 100, 800, 0,   'ALL',      NULL,          800,   0, 1, 'RELATIVE', NULL,                NULL,                60, 1);

-- ---------------------------------------------------------------------
-- 兑换码批次（容量演示：total_count 可到 20 亿，这里给可验证的小批量）
-- ---------------------------------------------------------------------
INSERT INTO `redeem_code_batch` (`batch_no`, `title`, `biz_type`, `ref_id`, `reward_value`, `total_count`, `used_count`, `start_time`, `end_time`, `status`, `remark`) VALUES
('RCB2026090100001', '开学季积分兑换码 100 积分', 'POINT',  NULL, 100, 10000, 0, '2026-09-01 00:00:00', '2030-12-31 23:59:59', 1, '开学季活动，每码 100 积分'),
('RCB2026090100002', '开学季新人券兑换码',        'COUPON', 1,   1,   5000,  0, '2026-09-01 00:00:00', '2030-12-31 23:59:59', 1, '兑换 CT2026001 新人券');

-- ---------------------------------------------------------------------
-- 给演示学生发几张券，方便直接体验结算页最优优惠组合
-- ---------------------------------------------------------------------
INSERT INTO `user_coupon` (`user_id`, `template_id`, `coupon_code`, `coupon_title`, `coupon_type`, `face_value`, `discount_rate`, `threshold_point`, `max_discount`, `scope_type`, `scope_value`, `status`, `source`, `expire_time`) VALUES
(3, 2, 'UCP000000000000000000000003A1', '满 300 减 60',   'CASH',     60, 100, 300, 0,   'ALL',      NULL,    'UNUSED', 'RECEIVE', '2030-12-31 23:59:59'),
(3, 3, 'UCP000000000000000000000003A2', '学习类 8.5 折',  'DISCOUNT', 0,  85,  100, 200, 'CATEGORY', 'STUDY', 'UNUSED', 'RECEIVE', '2030-12-31 23:59:59'),
(3, 4, 'UCP000000000000000000000003A3', '满 500 减 120',  'CASH',     120,100, 500, 0,   'ALL',      NULL,    'UNUSED', 'RECEIVE', '2030-12-31 23:59:59'),
(4, 2, 'UCP000000000000000000000004A1', '满 300 减 60',   'CASH',     60, 100, 300, 0,   'ALL',      NULL,    'UNUSED', 'RECEIVE', '2030-12-31 23:59:59'),
(4, 5, 'UCP000000000000000000000004A2', '文创专区 9 折',  'DISCOUNT', 0,  90,  200, 100, 'CATEGORY', 'LIFE',  'UNUSED', 'RECEIVE', '2030-12-31 23:59:59');
