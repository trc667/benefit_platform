package com.campus.growth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.ai")
public class AiProperties {

    /** 是否启用 AI 助手 */
    private boolean enabled = true;
    /** OpenAI 兼容的接口地址，如 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";
    /** API Key，未配置时自动降级为本地规则引擎 */
    private String apiKey;
    /** 主模型：负责 Function Calling 与多轮对话 */
    private String model = "deepseek-chat";
    /** 主模型超时（毫秒） */
    private int timeoutMs = 30_000;
    /** 携带的历史轮数 */
    private int maxHistoryRounds = 6;
    /** 单次回复最大 token */
    private int maxTokens = 1024;
    /** 温度 */
    private double temperature = 0.3;

    /** 轻量模型：只用于短文本（订单清单文案），HTTP 直连、短超时 */
    private LightModel lightModel = new LightModel();

    @Data
    public static class LightModel {
        private boolean enabled = true;
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey;
        private String model = "deepseek-chat";
        private int timeoutMs = 8_000;
        private int maxTokens = 256;
    }
}
