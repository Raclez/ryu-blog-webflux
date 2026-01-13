package com.ryu.blog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket处理器
 * 管理WebSocket连接和消息发送
 * 
 * @author ryu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;

    /**
     * 存储用户ID到WebSocket会话的映射
     * Key: userId, Value: Sink用于发送消息
     */
    private final Map<Long, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 从查询参数中提取用户ID
        String query = session.getHandshakeInfo().getUri().getQuery();
        Long userId = WebSocketConfig.extractUserId(query);

        if (userId == null) {
            log.warn("WebSocket连接失败: 无效的token");
            return session.close();
        }

        log.info("用户 {} 建立WebSocket连接", userId);

        // 创建消息发送器
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        userSinks.put(userId, sink);

        // 发送连接成功消息
        sink.tryEmitNext("{\"type\":\"connected\",\"message\":\"连接成功\"}");

        // 接收客户端消息（心跳等）
        Mono<Void> input = session.receive()
                .doOnNext(msg -> {
                    String payload = msg.getPayloadAsText();
                    log.debug("收到用户 {} 的消息: {}", userId, payload);
                    // 处理心跳消息
                    if ("ping".equals(payload)) {
                        sink.tryEmitNext("{\"type\":\"pong\"}");
                    }
                })
                .then();

        // 发送服务端消息
        Mono<Void> output = session.send(
                sink.asFlux()
                        .map(session::textMessage)
        );

        // 连接关闭时清理
        return Mono.zip(input, output)
                .doFinally(signalType -> {
                    log.info("用户 {} 断开WebSocket连接: {}", userId, signalType);
                    userSinks.remove(userId);
                })
                .then();
    }

    /**
     * 向指定用户发送消息
     */
    public void sendToUser(Long userId, String message) {
        Sinks.Many<String> sink = userSinks.get(userId);
        if (sink != null) {
            sink.tryEmitNext(message);
            log.debug("向用户 {} 发送消息: {}", userId, message);
        } else {
            log.debug("用户 {} 未连接WebSocket，消息将保存到离线通知", userId);
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        return userSinks.containsKey(userId);
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineUserCount() {
        return userSinks.size();
    }
}
