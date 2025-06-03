package com.ryu.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    /**
     * CORS配置
     */
    @Bean
    public WebFilter corsFilter() {
        return (ServerWebExchange ctx, WebFilterChain chain) -> {
            ServerHttpRequest request = ctx.getRequest();
            if (CorsUtils.isCorsRequest(request)) {
                ServerHttpResponse response = ctx.getResponse();
                HttpHeaders headers = response.getHeaders();
                
                // 允许的域名
                if ("*".equals(allowedOrigins)) {
                    String origin = request.getHeaders().getOrigin();
                    if (origin != null) {
                        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                    }
                } else {
                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins);
                }
                
                // 允许的请求方法
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods);
                // 允许的请求头
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders);
                // 是否允许携带凭证
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(allowCredentials));
                // 预检请求的有效期
                headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(maxAge));
                
                if (request.getMethod() == HttpMethod.OPTIONS) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }
            return chain.filter(ctx);
        };
    }
} 