package com.campus.growth;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * 校园成长权益平台 · 模块化单体启动类。
 *
 * <p>启动后：</p>
 * <pre>
 * 接口地址   http://127.0.0.1:8080/api
 * 健康检查   http://127.0.0.1:8080/actuator/health
 * 演示账号   admin / operator / student01 ... 密码均为 123456
 * </pre>
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.campus.growth.modules.**.mapper")
public class CampusGrowthApplication {

    public static void main(String[] args) {
        Environment env = SpringApplication.run(CampusGrowthApplication.class, args).getEnvironment();
        String port = env.getProperty("server.port", "8080");
        log.info("""
                
                ============================================================
                  校园成长权益平台启动成功
                  接口前缀 : http://127.0.0.1:{}/api
                  当前环境 : {}
                  Dubbo    : {}（false = 单体形态，无任何远程调用）
                ============================================================
                """, port, String.join(",", env.getActiveProfiles()),
                env.getProperty("dubbo.enabled", "false"));
    }
}
