package com.campus.growth.common.aspect;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.context.LoginUser;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.common.util.WebUtil;
import com.campus.growth.modules.system.entity.SysOperationLog;
import com.campus.growth.modules.system.service.OperationLogService;
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
 * 操作日志切面。
 * <p>顺序最靠内（{@link AspectOrder#OP_LOG}），记录的是"包含事务与锁在内的真实耗时"。</p>
 */
@Slf4j
@Aspect
@Component
@Order(AspectOrder.OP_LOG)
@RequiredArgsConstructor
public class OpLogAspect {

    private final OperationLogService operationLogService;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        int resultCode = 0;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            resultCode = -1;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            try {
                SysOperationLog entity = buildLog(joinPoint, opLog, resultCode, errorMsg,
                        (int) (System.currentTimeMillis() - start));
                operationLogService.saveAsync(entity);
            } catch (Exception e) {
                log.warn("记录操作日志异常", e);
            }
        }
    }

    private SysOperationLog buildLog(ProceedingJoinPoint joinPoint, OpLog opLog,
                                     int resultCode, String errorMsg, int costMs) {
        SysOperationLog entity = new SysOperationLog();
        LoginUser user = UserContext.get();
        if (user != null) {
            entity.setUserId(user.getUserId());
            entity.setUsername(user.getUsername());
        }
        entity.setModule(opLog.module());
        entity.setAction(opLog.action());
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        entity.setMethod(method.getDeclaringClass().getSimpleName() + "." + method.getName());
        entity.setResultCode(resultCode);
        entity.setErrorMsg(errorMsg == null ? null : errorMsg.substring(0, Math.min(errorMsg.length(), 500)));
        entity.setCostMs(costMs);
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            entity.setIp(WebUtil.getIp(request));
        }
        if (opLog.saveParams()) {
            try {
                // 入参统一截断，避免大对象把日志表撑爆
                entity.setParams(JsonUtil.abbreviate(JsonUtil.toJson(joinPoint.getArgs())));
            } catch (Exception ignored) {
                entity.setParams("参数序列化失败");
            }
        }
        return entity;
    }
}
