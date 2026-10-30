package com.ryu.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置
 *
 * <p><b>原先的写法有问题</b>：{@code addAllowedOriginPattern("*")} 配合
 * {@code setAllowCredentials(true)} —— 也就是<b>任意站点</b>都可以带着
 * Cookie / 凭据来调这个 API。这是典型的「万能 CORS」配置：
 * 任何人在自己的域名上放一个页面，就能以用户身份操作这个站。
 *
 * <p>现在改成<b>显式白名单</b>：默认只放行本地开发用的几个端口，
 * 生产环境通过配置项 {@code blog.cors.allowed-origins} 指定自己的前端域名。
 *
 * <p>确实需要「任意来源」时，把配置项写成 {@code *} —— 但那等于把上面的
 * 风险又请回来了，所以会打一条醒目的警告日志。
 *
 * @author ryu
 */
@Slf4j
@Configuration
public class CorsConfig {

    /**
     * 允许跨域的来源，逗号分隔
     *
     * <p>默认是本地开发常用的几个前端端口。
     */
    @Value("${blog.cors.allowed-origins:http://localhost:5777}")
    private String allowedOrigins;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (origins.contains("*")) {
            log.warn("⚠️ blog.cors.allowed-origins 配置成了 '*' —— 任意站点都能带凭据访问本 API。"
                    + " 生产环境请改成明确的前端域名。");
            config.addAllowedOriginPattern("*");
        } else if (origins.isEmpty()) {
            log.info("未配置 blog.cors.allowed-origins —— 不下发任何跨域响应头（前后端同源部署即可）。");
        } else {
            config.setAllowedOrigins(origins);
            log.info("跨域白名单: {}", origins);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
