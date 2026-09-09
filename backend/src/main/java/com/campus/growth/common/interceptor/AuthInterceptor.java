package com.campus.growth.common.interceptor;

import com.campus.growth.common.annotation.RequireRole;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.context.LoginUser;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.Result;
import com.campus.growth.common.util.JsonUtil;
import com.campus.growth.common.util.WebUtil;
import com.campus.growth.modules.auth.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 登录态 + 权限拦截器。
 *
 * <h3>执行顺序</h3>
 * <pre>
 * 1. 解析 JWT → 校验 Redis 白名单（支持禁用账号/改密码立即失效）
 * 2. 写入 UserContext
 * 3. 权限校验：
 *    a) 方法/类上有 @RequireRole → 按注解声明的角色判断
 *    b) 否则若路径以 /api/admin 开头 → 强制 OPERATOR/ADMIN（兜底，防漏加注解）
 * 4. 请求结束后清理 ThreadLocal
 * </pre>
 *
 * <p>注意：权限判断发生在 UserContext 写入之后，所以鉴权失败时也要清理上下文，
 * 否则 Tomcat 复用线程会把"半登录"状态带给下一个请求。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 管理端路径前缀，兜底鉴权用 */
    private static final String ADMIN_PATH_PREFIX = "/api/admin";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = WebUtil.resolveToken(request.getHeader(BizConst.HEADER_TOKEN));
        if (token == null) {
            writeError(response, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage());
            return false;
        }
        Claims claims = jwtTokenProvider.parse(token);
        if (claims == null) {
            writeError(response, ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.getMessage());
            return false;
        }
        Long userId = claims.get("userId", Number.class) == null
                ? null : claims.get("userId", Number.class).longValue();
        String jti = claims.getId();
        if (userId == null || jti == null) {
            writeError(response, ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.getMessage());
            return false;
        }
        // 白名单校验：支持禁用账号 / 改密码后立即失效
        String whitelistKey = String.format(RedisKeyConst.TOKEN, userId, jti);
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(whitelistKey))) {
            writeError(response, ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.getMessage());
            return false;
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUsername(claims.getSubject());
        loginUser.setRole(claims.get("role", String.class));
        UserContext.set(loginUser);

        // ---------------- 权限校验 ----------------
        if (!hasPermission(request, handler, loginUser)) {
            log.warn("越权访问拦截 userId={} role={} uri={}", userId, loginUser.getRole(), request.getRequestURI());
            UserContext.clear();
            writeError(response, ErrorCode.FORBIDDEN, "无权访问该资源，需要运营或管理员权限");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理：Tomcat 线程复用，残留会导致下一个请求"继承"上一个用户身份
        UserContext.clear();
    }

    /**
     * 权限判断：注解优先，路径兜底。
     */
    private boolean hasPermission(HttpServletRequest request, Object handler, LoginUser loginUser) {
        String[] requiredRoles = null;
        if (handler instanceof HandlerMethod handlerMethod) {
            RequireRole annotation = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (annotation == null) {
                annotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (annotation != null) {
                requiredRoles = annotation.value();
            }
        }
        // 兜底：管理端路径没有注解也必须要求运营/管理员
        if (requiredRoles == null && request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)) {
            requiredRoles = new String[]{BizConst.ROLE_OPERATOR, BizConst.ROLE_ADMIN};
        }
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }
        String role = loginUser.getRole();
        return role != null && Arrays.asList(requiredRoles).contains(role);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JsonUtil.toJson(Result.fail(errorCode.getCode(), message)));
        } catch (Exception e) {
            log.error("写入鉴权失败响应异常", e);
        }
    }
}
