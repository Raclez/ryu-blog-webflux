package com.ryu.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * AI 功能配置类
 *
 * <p>这里只负责在 AI 功能开启时打一条启动日志。
 *
 * <p><b>原先的 {@code @ComponentScan(basePackages = "com.ryu.blog.service.ai")} 已删除</b>：
 * 主类 {@code com.ryu.blog.RyuBlogApplication} 标注了 {@code @SpringBootApplication}，
 * 默认就扫描 {@code com.ryu.blog} 及其全部子包，那条 @ComponentScan 属于重复扫描。
 *
 * <p>AI 的各个 Bean 通过 {@link ConditionalOnAiEnabled} 逐个受开关控制，
 * 而不是靠这里的 @ComponentScan —— 因为 @ConditionalOnProperty 挂在 @Configuration 上
 * 时，条件不满足只会跳过这个配置类，<b>不会</b>阻止主扫描注册同包的其他 Bean。
 *
 * @author ryu
 */
@Slf4j
@Configuration
@ConditionalOnAiEnabled
public class AiConfig {

    public AiConfig() {
        log.info("AI功能已启用，正在加载AI相关组件...");
    }
}
