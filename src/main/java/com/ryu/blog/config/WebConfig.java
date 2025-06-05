package com.ryu.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Web配置类
 * @author ryu
 */
@Configuration
public class WebConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age}")
    private long maxAge;

    @Value("${app.cors.exposed-headers:Content-Disposition,Content-Length,Content-Type}")
    private String exposedHeaders;

    /**
     * CORS跨域配置
     * 创建WebFilter以处理跨域请求
     * 设置为最高优先级，确保在其他过滤器之前执行
     * @return CORS WebFilter
     */
    @Bean
    public WebFilter corsFilter() {
        return new CorsWebFilter();
    }
    
    /**
     * CORS跨域过滤器
     * 实现WebFilter接口并设置为最高优先级
     */
    private class CorsWebFilter implements WebFilter, Ordered {
        @Override
        public Mono<Void> filter(ServerWebExchange ctx, WebFilterChain chain) {
            ServerHttpRequest request = ctx.getRequest();
            // 检查请求是否为CORS请求
            if (CorsUtils.isCorsRequest(request)) {
                ServerHttpResponse response = ctx.getResponse();
                HttpHeaders headers = response.getHeaders();
                
                // 处理允许的源
                if ("*".equals(allowedOrigins)) {
                    // 如果配置为"*"，根据实际请求源动态设置
                    // 这样做是为了支持带凭证的请求，因为"*"和credentials:true不能同时使用
                    String origin = request.getHeaders().getOrigin();
                    if (origin != null) {
                        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                    }
                } else if (allowedOrigins.contains(",")) {
                    // 如果配置了多个源，检查请求源是否在允许列表中
                    String origin = request.getHeaders().getOrigin();
                    if (origin != null) {
                        List<String> originList = Arrays.asList(allowedOrigins.split(","));
                        if (originList.contains(origin)) {
                            headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                        }
                    }
                } else {
                    // 单一固定源
                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins);
                }
                
                // 设置允许的请求方法
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods);
                
                // 设置允许的请求头
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders);
                
                // 设置是否允许携带凭证（cookies等）
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(allowCredentials));
                
                // 设置预检请求的有效期（秒）
                headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(maxAge));
                
                // 设置允许客户端访问的响应头
                headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeaders);
                
                // 处理预检请求（OPTIONS）
                if (request.getMethod() == HttpMethod.OPTIONS) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }
            return chain.filter(ctx);
        }
        
        @Override
        public int getOrder() {
            // 设置为最高优先级，确保在其他过滤器之前执行
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
} 