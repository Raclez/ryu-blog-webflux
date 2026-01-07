package com.ryu.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI重试配置
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.retry")
public class AiRetryConfig {

    /**
     * 最大重试次数
     */
    private int maxAttempts = 3;

    /**
     * 初始退避时间（毫秒）
     */
    private long initialBackoff = 1000;

    /**
     * 最大退避时间（毫秒）
     */
    private long maxBackoff = 10000;

    /**
     * 退避倍数
     */
    private double backoffMultiplier = 2.0;

    /**
     * 请求超时时间（毫秒）
     */
    private long timeout = 60000;

    /**
     * 是否启用重试
     */
    private boolean enabled = true;
}
