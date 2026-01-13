package com.ryu.blog.config;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket配置类
 * 用于任务状态实时通知
 * 
 * @author ryu
 */
@Slf4j
@Configuration
public class WebSocketConfig {

    /**
     * WebSocket处理器适配器
     */
    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    /**
     * WebSocket路由映射
     */
    @Bean
    public HandlerMapping webSocketMapping(TaskWebSocketHandler taskWebSocketHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/tasks", taskWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1); // 设置高优先级
        return mapping;
    }

    /**
     * 从查询参数中提取用户ID
     * WebSocket连接时需要传递token参数
     * 例如: ws://localhost:8080/ws/tasks?token=xxx
     */
    public static Long extractUserId(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        try {
            // 解析查询参数
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    String token = keyValue[1];
                    // 使用SaToken验证token并获取用户ID
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        return Long.parseLong(loginId.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析token失败", e);
        }

        return null;
    }
}
