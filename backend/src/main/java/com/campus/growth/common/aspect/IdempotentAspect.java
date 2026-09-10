package com.campus.growth.common.aspect;

import com.campus.growth.common.annotation.Idempotent;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.SpelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 幂等切面：Redis SETNX 窗口内只放行一次。
 * <p>与数据库唯一索引配合：注解挡住"用户连点"，唯一索引兜住"极端并发"。</p>
 */
@Slf4j
@Aspect
@Component
@Order(AspectOrder.IDEMPOTENT)
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String key = SpelUtil.evaluate(idempotent.key(), method, joinPoint.getArgs());
        if (key == null || key.isBlank()) {
            key = method.getDeclaringClass().getSimpleName() + ":" + method.getName()
                    + ":" + Arrays.deepHashCode(joinPoint.getArgs());
        }
        // 统一加"用户维度"前缀：注解里的 key 通常只描述业务对象（如 goodsId+quantity），
        // 不带用户就会让**不同用户互相把对方挡住**——压测 60 个用户同时下单同一商品时，
        // 59 个被"请勿重复提交"拦掉就是这么来的。
        // 幂等的语义是"同一个人别连点"，不是"全站只准提交一次"。
        Long userId = UserContext.userId();
        String redisKey = RedisKeyConst.idempotent((userId == null ? "anon" : "u:" + userId) + ":" + key);
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", idempotent.ttlSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            log.warn("幂等拦截 key={}", redisKey);
            throw BizException.of(ErrorCode.TOO_MANY_REQUESTS, idempotent.message());
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务失败时释放幂等键，允许用户立即重试（否则一次误触要等窗口结束）
            stringRedisTemplate.delete(redisKey);
            throw e;
        }
    }
}
