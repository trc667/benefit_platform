package com.campus.growth.it;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.growth.common.constant.MqTopicConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.LoginUser;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.enums.PointBizType;
import com.campus.growth.common.util.PeriodKeyUtil;
import com.campus.growth.modules.mq.entity.MqConsumeRecord;
import com.campus.growth.modules.mq.mapper.MqConsumeRecordMapper;
import com.campus.growth.modules.point.entity.PointRecord;
import com.campus.growth.modules.point.mapper.PointRecordMapper;
import com.campus.growth.modules.point.service.PointService;
import com.campus.growth.modules.point.service.RankService;
import com.campus.growth.modules.signin.entity.SignInRecord;
import com.campus.growth.modules.signin.mapper.SignInRecordMapper;
import com.campus.growth.modules.signin.service.SignInService;
import com.campus.growth.modules.signin.vo.SignInResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签到 → Kafka → 积分 → 排行榜 全链路集成测试。
 *
 * <h3>为什么用 @EmbeddedKafka</h3>
 * <p>这条链路的价值就在于"异步"，如果只验证"签到接口返回 200"等于什么都没验证。
 * 这里在 JVM 内拉起一个真实的 Kafka broker（KRaft/ZK 单节点），
 * 让 {@code EventPublisher} → outbox → Kafka → 消费者 → 积分入账 全程真实发生。</p>
 *
 * <h3>运行方式</h3>
 * <pre>
 * mvn test -Dtest=SigninKafkaFlowTest -DrunItTests=true
 * </pre>
 * <p>默认不跑（需要 MySQL + Redis 就绪），避免 CI 无中间件时失败。</p>
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "campus.mq.outbox.enabled=false", // 本测试直接发 Kafka，不需要补偿任务干扰
        "logging.level.org.apache.kafka=warn"
})
@EmbeddedKafka(partitions = 3, topics = {MqTopicConst.SIGNIN_SUCCESS, MqTopicConst.TASK_PROGRESS_PERSIST},
        brokerProperties = {
                "offsets.topic.replication.factor=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        })
@EnabledIfSystemProperty(named = "runItTests", matches = "true")
class SigninKafkaFlowTest {

    /** 用 student02 做测试，避免污染其他演示账号 */
    private static final Long USER_ID = 4L;

    @Autowired
    private SignInService signInService;
    @Autowired
    private PointService pointService;
    @Autowired
    private RankService rankService;
    @Autowired
    private PointRecordMapper pointRecordMapper;
    @Autowired
    private SignInRecordMapper signInRecordMapper;
    @Autowired
    private MqConsumeRecordMapper consumeRecordMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        // 清理当天数据，保证测试可重复执行
        stringRedisTemplate.delete(RedisKeyConst.signinMonth(PeriodKeyUtil.monthKey(today), USER_ID));
        stringRedisTemplate.delete(RedisKeyConst.signinYear(today.getYear(), USER_ID));
        stringRedisTemplate.delete(RedisKeyConst.signinContinuous(USER_ID));
        stringRedisTemplate.delete(RedisKeyConst.signinContinuous(USER_ID) + ":last");
        signInRecordMapper.delete(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, USER_ID).eq(SignInRecord::getSignDate, today));
        pointRecordMapper.delete(new LambdaQueryWrapper<PointRecord>()
                .eq(PointRecord::getUserId, USER_ID).eq(PointRecord::getBizType, PointBizType.SIGNIN.name()));
        // 补一条账户，避免首次执行时账户不存在
        pointService.getOrCreateAccount(USER_ID);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(USER_ID);
        loginUser.setUsername("student02");
        loginUser.setRole("STUDENT");
        UserContext.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("签到后积分经 Kafka 异步入账，排行榜同步更新")
    void signinShouldCreditPointsAsynchronously() throws Exception {
        int balanceBefore = pointService.balanceOf(USER_ID);
        long rankScoreBefore = rankService.scoreOf(USER_ID, "TOTAL");

        // 1. 执行签到（此时只写位图 + 流水 + 本地消息表）
        SignInResultVO result = signInService.signIn("TEST");
        assertNotNull(result);
        assertEquals(today.toString(), result.getSignDate());
        assertTrue(result.getPointAward() > 0, "签到奖励应大于 0");
        System.out.println("签到成功：连续 " + result.getContinuousDays() + " 天，奖励 "
                + result.getPointAward() + " 积分（等待 Kafka 异步入账）");

        // 2. 等待消费者处理（最多 20 秒）
        int expectedBalance = balanceBefore + result.getPointAward();
        boolean credited = awaitCondition(20_000, () -> pointService.balanceOf(USER_ID) == expectedBalance);
        assertTrue(credited, "积分未在 20 秒内到账：期望 " + expectedBalance + "，实际 " + pointService.balanceOf(USER_ID));

        // 3. 校验积分流水（幂等键 = 签到日期）
        PointRecord record = pointRecordMapper.selectOne(new LambdaQueryWrapper<PointRecord>()
                .eq(PointRecord::getUserId, USER_ID)
                .eq(PointRecord::getBizType, PointBizType.SIGNIN.name())
                .eq(PointRecord::getBizNo, today.toString()));
        assertNotNull(record, "应生成一条 SIGNIN 积分流水");
        assertEquals(result.getPointAward(), record.getChangePoint());
        assertEquals(expectedBalance, record.getBalanceAfter());

        // 4. 校验消费幂等记录
        Long consumeCount = consumeRecordMapper.selectCount(new LambdaQueryWrapper<MqConsumeRecord>()
                .eq(MqConsumeRecord::getConsumerGroup, MqTopicConst.GROUP_POINT));
        assertTrue(consumeCount > 0, "应存在积分消费组的幂等记录");

        // 5. 校验排行榜
        long rankScoreAfter = rankService.scoreOf(USER_ID, "TOTAL");
        assertTrue(rankScoreAfter >= rankScoreBefore + result.getPointAward(),
                "排行榜分数应增加：before=" + rankScoreBefore + " after=" + rankScoreAfter);
        System.out.println("积分已到账：" + expectedBalance + "，排行榜分数 " + rankScoreAfter);
    }

    @Test
    @DisplayName("重复签到被拦截（BitMap 判重）")
    void duplicateSigninRejected() throws Exception {
        signInService.signIn("TEST");
        awaitCondition(20_000, () -> pointService.balanceOf(USER_ID) > 0);
        try {
            signInService.signIn("TEST");
            throw new AssertionError("重复签到应当抛出业务异常");
        } catch (com.campus.growth.common.exception.BizException e) {
            assertEquals(2001, e.getCode(), "错误码应为 SIGNIN_ALREADY");
            System.out.println("重复签到被正确拦截：" + e.getMessage());
        }
    }

    /** 轮询等待条件成立 */
    private boolean awaitCondition(long timeoutMillis, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}
