package com.campus.growth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 * <p>只引入 {@code spring-security-crypto}（一个几百 KB 的包）而不是整个 Spring Security，
 * 因为项目只需要 BCrypt 这一件事。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=10 是默认值，单次加密约 60-80ms，能有效抵抗离线爆破
        return new BCryptPasswordEncoder(10);
    }
}
