package com.campus.growth.common.context;

import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;

/**
 * 当前登录用户上下文。
 * <p>用 {@link ThreadLocal} 保存，拦截器 preHandle 写入、afterCompletion 清理；
 * 异步线程（Kafka 消费、CompletableFuture）不继承该上下文，需要用户信息时显式传参，
 * 避免出现"异步线程读到上个请求用户"的经典事故。</p>
 */
public final class UserContext {

    private UserContext() {
    }

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /** 必须登录的场景使用，未登录直接抛 401 业务异常 */
    public static Long requireUserId() {
        Long userId = userId();
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static String username() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }
}
