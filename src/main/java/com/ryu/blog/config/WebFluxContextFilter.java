package com.ryu.blog.config;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux环境下的上下文过滤器
 * 自动为每个请求设置和清理Sa-Token上下文
 * 使用此过滤器后，所有Handler中无需手动管理上下文
 * @author ryu
 */
@Slf4j
@Component
public class WebFluxContextFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 设置上下文
        SaReactorSyncHolder.setContext(exchange);
        
        // 继续过滤器链，并在响应完成后清理上下文
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // 请求结束时，清理上下文
                    SaReactorSyncHolder.clearContext();
                    if (log.isTraceEnabled()) {
                        log.trace("Sa-Token context cleared for path: {}", exchange.getRequest().getPath());
                    }
                });
    }

    @Override
    public int getOrder() {
        // 确保在Sa-Token过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
} 