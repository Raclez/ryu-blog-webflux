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
import java.util.Set;
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
     * 用户ID → 该用户的所有连接（一个用户可能开多个标签页）。
     *
     * <p>原先的值是单个 Sink：同一用户第二次连接会覆盖第一次，
     * 关掉任一标签页时又会把另一个标签页的 Sink 一起移除 ——
     * 两个标签页的通知都会丢。现在每个连接各自一个 Sink。
     */
    private final Map<Long, Set<Sinks.Many<String>>> userSinks = new ConcurrentHashMap<>();

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

        // 创建消息发送器（同一用户的每个连接一个，互不覆盖）
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        userSinks.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sink);

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
                    // 只移除当前连接自己的 Sink；最后一个连接断开时才移除用户条目
                    userSinks.computeIfPresent(userId, (k, sinks) -> {
                        sinks.remove(sink);
                        return sinks.isEmpty() ? null : sinks;
                    });
                })
                .then();
    }

    /**
     * 向指定用户发送消息
     *
     * <p><b>返回是否至少有一个连接真正收到了消息</b>。
     * 原先忽略 {@code tryEmitNext} 的返回值：没有订阅者时它返回
     * {@code FAIL_ZERO_SUBSCRIBER}，消息被静默丢弃，而 {@link #isUserOnline}
     * 仍然返回 true，于是也不会回落到离线通知 —— 通知就这样永久消失。
     *
     * @return true 表示至少一个在线连接接受了消息
     */
    public boolean sendToUser(Long userId, String message) {
        Set<Sinks.Many<String>> sinks = userSinks.get(userId);
        if (sinks == null || sinks.isEmpty()) {
            log.debug("用户 {} 未连接WebSocket，消息将保存到离线通知", userId);
            return false;
        }

        boolean delivered = false;
        for (Sinks.Many<String> sink : sinks) {
            Sinks.EmitResult result = sink.tryEmitNext(message);
            if (result.isSuccess()) {
                delivered = true;
            } else {
                log.debug("向用户 {} 的某个连接发送消息失败: result={}", userId, result);
            }
        }

        if (delivered) {
            log.debug("向用户 {} 发送消息: {}", userId, message);
        }
        return delivered;
    }

    /**
     * 检查用户是否在线（存在至少一个已注册的连接）
     */
    public boolean isUserOnline(Long userId) {
        Set<Sinks.Many<String>> sinks = userSinks.get(userId);
        return sinks != null && !sinks.isEmpty();
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineUserCount() {
        return userSinks.size();
    }
}
