package com.campus.growth.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求工具。
 */
public final class WebUtil {

    private WebUtil() {
    }

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP"
    };

    /** 获取真实 IP（兼容 nginx 反向代理） */
    public static String getIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"
                int comma = ip.indexOf(',');
                return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** 提取 Bearer token */
    public static String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return authorizationHeader.trim();
    }
}
