package com.campus.growth.common.filter;

import com.campus.growth.common.context.DeviceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把请求头 {@code X-Device-Id} 放进 {@link DeviceContext}，请求结束后清理。
 *
 * <p>放在拦截器之前（{@code @Order} 小于拦截器不受影响，Filter 天然先于 Interceptor），
 * 因为登录/注册接口被拦截器排除，但风控同样需要设备号。</p>
 */
@Component
@Order(1)
public class DeviceIdFilter extends OncePerRequestFilter {

    /** 请求头名，前端在 axios 拦截器里统一带上 */
    public static final String HEADER = "X-Device-Id";

    /** 设备号最大长度，避免被塞入超长字符串打爆 Redis key */
    private static final int MAX_LEN = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String deviceId = request.getHeader(HEADER);
            if (deviceId != null) {
                deviceId = deviceId.trim();
                if (deviceId.isEmpty() || deviceId.length() > MAX_LEN) {
                    deviceId = null;
                }
            }
            DeviceContext.set(deviceId);
            chain.doFilter(request, response);
        } finally {
            DeviceContext.clear();
        }
    }
}
