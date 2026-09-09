package com.campus.growth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 大模型 HTTP 客户端配置。
 *
 * <h3>为什么用 RestClient 而不是 Spring AI / LangChain4j</h3>
 * <p>项目只需要"OpenAI 兼容协议的 /chat/completions + Function Calling"这一件事，
 * 引一个框架要多带几十个依赖、多一层抽象，出问题还得多查一份文档。
 * Spring 6.1 的 {@link RestClient} 足够干净：同步调用、超时可控、日志好加。</p>
 *
 * <h3>两个客户端分开配置</h3>
 * <ul>
 *   <li>{@code mainModelClient}：主模型，负责多轮对话与工具调用，超时 30s；</li>
 *   <li>{@code lightModelClient}：轻量模型，只生成订单清单这类短文本，超时 8s。
 *       生成文案是"锦上添花"的能力，不能让它拖慢下单主链路，超时直接降级为模板文案。</li>
 * </ul>
 */
@Configuration
public class AiConfig {

    @Bean("mainModelClient")
    public RestClient mainModelClient(AiProperties properties) {
        return build(properties.getBaseUrl(), properties.getTimeoutMs());
    }

    @Bean("lightModelClient")
    public RestClient lightModelClient(AiProperties properties) {
        AiProperties.LightModel light = properties.getLightModel();
        String baseUrl = light.getBaseUrl() == null || light.getBaseUrl().isBlank()
                ? properties.getBaseUrl() : light.getBaseUrl();
        return build(baseUrl, light.getTimeoutMs());
    }

    private RestClient build(String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
