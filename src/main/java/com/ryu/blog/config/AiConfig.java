package com.ryu.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * AI功能配置类
 * 
 * <p>根据配置文件中的 blog.ai.enabled 属性决定是否启用AI功能。
 * 当启用时，会扫描并加载所有AI相关的组件。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "blog.ai", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "com.ryu.blog.service.ai")
public class AiConfig {

    public AiConfig() {
        log.info("AI功能已启用，正在加载AI相关组件...");
    }
}
