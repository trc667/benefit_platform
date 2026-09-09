package com.campus.growth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Dubbo RPC 开关守卫。
 *
 * <h3>本项目的 Dubbo 用法（务必读一遍）</h3>
 * <ul>
 *   <li>只有 <b>订单</b> 与 <b>优惠券</b> 两个域预留了 RPC 接口（{@code modules.*.rpc}）；</li>
 *   <li>单体部署时 {@code dubbo.enabled=false}，Dubbo 自动装配整体失效，
 *       {@code @DubboService} 不会被扫描，不监听 20880 端口，
 *       <b>任何业务代码都不允许通过 RPC 调用</b>；</li>
 *   <li>需要验证"将来能不能拆"时，用 {@code --spring.profiles.active=rpc} 打开，
 *       通过 {@code dubbo.reference.url=dubbo://127.0.0.1:20880} 直连自测；</li>
 *   <li>直连模式不部署注册中心，不依赖 Nacos / Zookeeper。</li>
 * </ul>
 *
 * <p>这个类本身不做装配，只在开启时打一条显眼的日志，提醒使用者"现在不是单体形态了"。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "dubbo", name = "enabled", havingValue = "true")
public class DubboRpcConfig {

    @PostConstruct
    public void warn() {
        log.warn("========================================================================");
        log.warn("  Dubbo RPC 已启用：订单/优惠券 RPC 接口将对外暴露（直连模式，无注册中心）");
        log.warn("  单体部署请设置 dubbo.enabled=false，业务代码禁止远程调用");
        log.warn("========================================================================");
    }
}
