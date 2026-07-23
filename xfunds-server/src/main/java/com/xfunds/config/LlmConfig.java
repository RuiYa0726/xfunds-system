package com.xfunds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱GLM 大模型配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    /** 是否启用 LLM 解析 */
    private boolean enabled;
    /** API Key */
    private String apiKey;
    /** 模型名 */
    private String model = "glm-4-flash";
    /** API 基础地址 */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    /** 超时毫秒 */
    private int timeout = 30000;
    /** 温度 */
    private double temperature = 0.1;
}
