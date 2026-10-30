package com.ryu.blog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 功能开关条件注解
 *
 * <p>只有当 {@code blog.ai.enabled=true} 时，被标注的 Bean 才会被注册。
 *
 * <p><b>为什么需要它</b>：原先开关写在 {@code application-ai.yml} 里，而该文件只有
 * 在 profile 为 {@code ai} 时才会被加载（默认 profile 是 dev），所以这个属性永远读不到；
 * 同时 {@code AiConfig} 上挂的 {@code @ComponentScan("com.ryu.blog.service.ai")}
 * 是多余的——主类 {@code @SpringBootApplication} 位于 {@code com.ryu.blog}，
 * 已经扫描了整个包。结果是：开关写 false 也不会关掉任何东西。
 *
 * <p>现在开关通过 {@code spring.profiles.include: ai} 保证被加载，
 * 并用本注解逐个标注 AI 相关的 Bean 与 Controller，真正做到「关得掉」。
 *
 * @author ryu
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "blog.ai", name = "enabled", havingValue = "true")
public @interface ConditionalOnAiEnabled {
}
