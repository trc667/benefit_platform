package com.campus.growth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 启动期安全检查：防止"默认密钥被带上生产"。
 *
 * <h3>为什么需要它</h3>
 * <p>{@code campus.jwt.secret} 必须在配置文件里留一个开发默认值，否则本地跑不起来；
 * 但只要有人忘了用环境变量覆盖，线上就等于把签名密钥公开了——任何人都能伪造
 * 管理员 token。这里做两件事：</p>
 * <ul>
 *   <li>{@code prod} 环境仍是默认密钥 → <b>直接拒绝启动</b>（fail fast，比事后补救便宜）；</li>
 *   <li>其他环境 → 打一条显眼的 WARN，提示部署前必须换。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupCheck implements ApplicationRunner {

    /** 与 application.yml 中的默认值保持一致，仅用于开发环境 */
    public static final String DEV_DEFAULT_JWT_SECRET = "campus-growth-dev-only-change-me-2026";

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!DEV_DEFAULT_JWT_SECRET.equals(jwtProperties.getSecret())) {
            return;
        }
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod) {
            throw new IllegalStateException("""
                    [安全] 检测到 prod 环境仍在使用开发默认 JWT 密钥，已拒绝启动。
                    请设置环境变量后重启：CAMPUS_JWT_SECRET=<32 位以上的随机字符串>
                    """);
        }
        log.warn("""
                [安全] 当前使用开发默认 JWT 密钥，仅限本地调试。
                部署前请设置环境变量 CAMPUS_JWT_SECRET（更换后所有已签发的 token 立即失效）。""");
    }
}
