package com.campus.growth.config;

import com.campus.growth.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：登录拦截 + 跨域。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 允许跨域的前端来源（逗号分隔），来自 campus.cors.allowed-origins。
     * <p>不使用 {@code "*"} + {@code allowCredentials(true)}：那等于让任意站点
     * 都能带着凭据打接口，属于典型的 CORS 配置错误。</p>
     */
    @Value("${campus.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/benefit/categories"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 开发期前端跑在 5173；生产建议由 nginx 同源代理，或把域名加进 CAMPUS_CORS_ORIGINS
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
