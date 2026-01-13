package com.ryu.blog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.config.TaskWebSocketHandler;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务通知服务
 * 负责通过WebSocket发送任务状态变化通知
 * 
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNotificationService {

    private final TaskWebSocketHandler webSocketHandler;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String OFFLINE_NOTIFICATION_KEY_PREFIX = "task:notification:";
    private static final Duration OFFLINE_NOTIFICATION_TTL = Duration.ofDays(7);

    /**
     * 发送任务完成通知
     */
    public Mono<Void> notifyTaskCompleted(AsyncTask task) {
        Map<String, Object> notification = createNotification(
                "TASK_COMPLETED",
                task,
                "任务执行成功"
        );
        return sendNotification(task.getUserId(), notification);
    }

    /**
     * 发送任务失败通知
     */
    public Mono<Void> notifyTaskFailed(AsyncTask task) {
        Map<String, Object> notification = createNotification(
                "TASK_FAILED",
                task,
                "任务执行失败: " + task.getErrorMessage()
        );
        return sendNotification(task.getUserId(), notification);
    }

    /**
     * 发送任务进度更新通知
     */
    public Mono<Void> notifyProgress(Long userId, String taskId, Integer progress, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "TASK_PROGRESS");
        notification.put("taskId", taskId);
        notification.put("progress", progress);
        notification.put("message", message);
        notification.put("timestamp", LocalDateTime.now().toString());

        return sendNotification(userId, notification);
    }

    /**
     * 发送任务取消通知
     */
    public Mono<Void> notifyTaskCancelled(AsyncTask task) {
        Map<String, Object> notification = createNotification(
                "TASK_CANCELLED",
                task,
                "任务已取消"
        );
        return sendNotification(task.getUserId(), notification);
    }

    /**
     * 创建通知消息
     */
    private Map<String, Object> createNotification(String type, AsyncTask task, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("taskId", task.getTaskId());
        notification.put("taskType", task.getTaskType().name());
        notification.put("status", task.getStatus().name());
        notification.put("message", message);
        notification.put("progress", task.getProgress());
        notification.put("timestamp", LocalDateTime.now().toString());

        // 如果任务完成，包含结果预览
        if (task.getStatus() == TaskStatus.COMPLETED && task.getResultJson() != null) {
            notification.put("hasResult", true);
        }

        return notification;
    }

    /**
     * 发送通知（在线发送或离线存储）
     */
    private Mono<Void> sendNotification(Long userId, Map<String, Object> notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);

            // 检查用户是否在线
            if (webSocketHandler.isUserOnline(userId)) {
                // 在线用户：直接通过WebSocket发送
                webSocketHandler.sendToUser(userId, message);
                log.info("向在线用户 {} 发送通知: {}", userId, notification.get("type"));
                return Mono.empty();
            } else {
                // 离线用户：保存到Redis
                return saveOfflineNotification(userId, message)
                        .doOnSuccess(v -> log.info("用户 {} 离线，通知已保存到Redis", userId))
                        .then();
            }
        } catch (JsonProcessingException e) {
            log.error("序列化通知消息失败", e);
            return Mono.error(e);
        }
    }

    /**
     * 保存离线通知到Redis
     * 使用List结构存储，限制最多保留100条
     */
    private Mono<Long> saveOfflineNotification(Long userId, String notification) {
        String key = OFFLINE_NOTIFICATION_KEY_PREFIX + userId;
        return redisTemplate.opsForList()
                .rightPush(key, notification)
                .flatMap(size -> {
                    // 限制列表长度，只保留最新的100条（先限制再设置TTL，避免重复设置）
                    if (size > 100) {
                        return redisTemplate.opsForList()
                                .trim(key, -100, -1)
                                .then(redisTemplate.expire(key, OFFLINE_NOTIFICATION_TTL))
                                .thenReturn(size);
                    }
                    // 设置过期时间
                    return redisTemplate.expire(key, OFFLINE_NOTIFICATION_TTL)
                            .thenReturn(size);
                })
                .onErrorResume(error -> {
                    log.error("Failed to save offline notification for user {}: {}", userId, error.getMessage());
                    return Mono.just(0L);
                });
    }

    /**
     * 获取用户的离线通知
     */
    public Mono<java.util.List<String>> getOfflineNotifications(Long userId) {
        String key = OFFLINE_NOTIFICATION_KEY_PREFIX + userId;
        return redisTemplate.opsForList()
                .range(key, 0, -1)
                .collectList();
    }

    /**
     * 清除用户的离线通知
     */
    public Mono<Boolean> clearOfflineNotifications(Long userId) {
        String key = OFFLINE_NOTIFICATION_KEY_PREFIX + userId;
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }

    /**
     * 推送离线通知给刚上线的用户
     */
    public Mono<Void> pushOfflineNotifications(Long userId) {
        return getOfflineNotifications(userId)
                .flatMap(notifications -> {
                    if (notifications.isEmpty()) {
                        return Mono.empty();
                    }

                    log.info("向用户 {} 推送 {} 条离线通知", userId, notifications.size());

                    // 逐条发送离线通知
                    notifications.forEach(notification -> {
                        webSocketHandler.sendToUser(userId, notification);
                    });

                    // 清除已推送的离线通知
                    return clearOfflineNotifications(userId).then();
                });
    }
}
