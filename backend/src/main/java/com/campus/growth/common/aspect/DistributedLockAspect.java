package com.campus.growth.common.aspect;

import com.campus.growth.common.annotation.DistributedLock;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.SpelUtil;
import com.campus.growth.infra.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁切面。
 *
 * <h3>顺序是这段代码的全部重点</h3>
 * <p>{@link Order}({@link AspectOrder#LOCK} = 10) 必须小于事务切面顺序
 * （{@link AspectOrder#TRANSACTION} = 30，在 TransactionConfig 中显式指定）。
 * Spring AOP 的多个切面构成责任链：order 越小越靠外。
 * 若把锁切面放在事务里面，执行顺序变成"开事务 → 加锁 → 业务 → 解锁 → 提交"，
 * 锁在提交前就释放，并发请求会读到未提交的数据，分布式锁形同虚设。</p>
 *
 * <p>本切面同时兼容"锁内手动控制事务"的写法，但推荐直接用注解。</p>
 */
@Slf4j
@Aspect
@Component
@Order(AspectOrder.LOCK)
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final DistributedLockTemplate lockTemplate;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String key = SpelUtil.evaluate(distributedLock.key(), method, joinPoint.getArgs());
        if (key == null || key.isBlank()) {
            // 表达式解析失败属于开发期错误，直接抛出而不是静默放行（静默放行等于丢锁）
            throw new IllegalStateException("@DistributedLock key 表达式解析失败: " + distributedLock.key());
        }
        Long userId = UserContext.userId();
        String bizKey = userId == null ? key : key + ":" + userId;

        Object result = lockTemplate.executeWithResult(bizKey, distributedLock.waitSeconds(),
                distributedLock.leaseSeconds(), () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new IllegalStateException(e);
                    }
                });
        if (result == null) {
            throw BizException.of(ErrorCode.TOO_MANY_REQUESTS, distributedLock.message());
        }
        return result;
    }
}
