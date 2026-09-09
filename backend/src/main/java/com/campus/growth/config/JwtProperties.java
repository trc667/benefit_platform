package com.campus.growth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.jwt")
public class JwtProperties {

    /** 签名密钥，生产必须通过环境变量 CAMPUS_JWT_SECRET 覆盖 */
    private String secret;
    /** 过期分钟数 */
    private long expireMinutes = 720;
    /** 签发者 */
    private String issuer = "campus-growth";
}
