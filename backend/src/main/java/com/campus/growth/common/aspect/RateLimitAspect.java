package com.campus.growth.common.aspect;

import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.util.WebUtil;
import com.campus.growth.infra.ratelimit.RateLimitFacade;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面。
 * <p>顺序 {@link AspectOrder#RATE_LIMIT} 最靠外：先挡掉无效流量，再去抢分布式锁和数据库连接，
 * 避免被刷量时把连接池打满。</p>
 */
@Slf4j
@Aspect
@Component
@Order(AspectOrder.RATE_LIMIT)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitFacade rateLimitFacade;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String dimension = rateLimit.byUser()
                ? "u:" + (UserContext.userId() == null ? "anon" : UserContext.userId())
                : "ip:" + resolveIp();
        String prefix = rateLimit.key().isBlank() ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : rateLimit.key();
        String key = RedisKeyConst.PREFIX + "limit:" + prefix + ":" + dimension;

        if (!rateLimitFacade.tryAcquire(key, rateLimit.qps(), rateLimit.type())) {
            log.warn("触发限流 key={} qps={}", key, rateLimit.qps());
            throw BizException.of(ErrorCode.TOO_MANY_REQUESTS, rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private String resolveIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        return WebUtil.getIp(request);
    }
}
