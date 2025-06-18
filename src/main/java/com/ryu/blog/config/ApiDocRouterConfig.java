package com.ryu.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

/**
 * API文档路径配置
 * @author ryu
 */
@Configuration
public class ApiDocRouterConfig {

    /**
     * API文档相关路径路由
     */
    @Bean
    public RouterFunction<ServerResponse> apiRoutes() {
        return RouterFunctions.route()
                // 根路径跳转到文档
                // 其他文档路径跳转
                .GET("/api", request -> ServerResponse.permanentRedirect(URI.create("/doc.html")).build())
                .GET("/docs", request -> ServerResponse.permanentRedirect(URI.create("/doc.html")).build())
                .build();
    }
} 