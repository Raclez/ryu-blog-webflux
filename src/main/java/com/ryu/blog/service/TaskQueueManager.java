package com.ryu.blog.service;

import com.ryu.blog.enums.TaskPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

/**
 * 任务队列管理器
 * 使用 Redis Sorted Set 实现优先级队列
 * 
 * <p>特性：
 * <ul>
 *   <li>基于优先级和时间的排序</li>
 *   <li>使用 Lua 脚本保证原子性出队操作</li>
 *   <li>支持批量出队</li>
 *   <li>防止任务重复处理</li>
 * </ul>
 * 
 * @author ryu
 */
@Slf4j
@Component
public class TaskQueueManager {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    private static final String QUEUE_KEY_PREFIX = "task:queue:";
    
    /**
     * Lua 脚本：原子性地从队列中取出任务
     * 先获取任务列表，然后立即删除，保证原子性
     */
    private static final String DEQUEUE_SCRIPT = 
            "local queueKey = KEYS[1]\n" +
            "local batchSize = tonumber(ARGV[1])\n" +
            "local tasks = redis.call('ZRANGE', queueKey, 0, batchSize - 1)\n" +
            "if #tasks > 0 then\n" +
            "    redis.call('ZREM', queueKey, unpack(tasks))\n" +
            "end\n" +
            "return tasks";
    
    public TaskQueueManager(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 将任务加入队列
     * 
     * @param taskId 任务ID
     * @param priority 优先级
     * @param submitTime 提交时间
     * @return Mono<Void>
     */
    public Mono<Void> enqueue(String taskId, TaskPriority priority, LocalDateTime submitTime) {
        String queueKey = QUEUE_KEY_PREFIX + "all";
        double score = calculateScore(priority, submitTime);
        
        log.debug("Enqueuing task {} with priority {} and score {}", taskId, priority, score);
        
        return redisTemplate.opsForZSet()
                .add(queueKey, taskId, score)
                .doOnSuccess(added -> {
                    if (Boolean.TRUE.equals(added)) {
                        log.info("Task {} successfully added to queue", taskId);
                    } else {
                        log.warn("Task {} was already in queue", taskId);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to enqueue task {}: {}", taskId, error.getMessage(), error);
                    return Mono.error(error);
                })
                .then();
    }
    
    /**
     * 从队列取出任务（批量）
     * 使用 Lua 脚本保证原子性，防止重复获取
     * 
     * @param batchSize 批量大小
     * @return Flux<String> 任务ID列表
     */
    public Flux<String> dequeue(int batchSize) {
        String queueKey = QUEUE_KEY_PREFIX + "all";
        
        log.debug("Dequeuing up to {} tasks from queue", batchSize);
        
        RedisScript<java.util.List> script = RedisScript.of(DEQUEUE_SCRIPT, java.util.List.class);
        
        return redisTemplate.execute(
                script,
                Collections.singletonList(queueKey),
                Collections.singletonList(String.valueOf(batchSize))
        )
        .flatMap(list -> Flux.fromIterable(list))
        .map(Object::toString)
        .doOnNext(taskId -> log.debug("Task {} dequeued from queue", taskId))
        .onErrorResume(error -> {
            log.error("Dequeue operation failed: {}", error instanceof Throwable ? ((Throwable) error).getMessage() : error.toString(), error instanceof Throwable ? (Throwable) error : null);
            return Flux.empty();
        });
    }
    
    /**
     * 移除任务
     * 
     * @param taskId 任务ID
     * @return Mono<Boolean> 是否成功移除
     */
    public Mono<Boolean> remove(String taskId) {
        String queueKey = QUEUE_KEY_PREFIX + "all";
        
        log.debug("Removing task {} from queue", taskId);
        
        return redisTemplate.opsForZSet()
                .remove(queueKey, taskId)
                .map(count -> {
                    boolean removed = count > 0;
                    if (removed) {
                        log.info("Task {} successfully removed from queue", taskId);
                    } else {
                        log.debug("Task {} was not in queue", taskId);
                    }
                    return removed;
                })
                .onErrorResume(error -> {
                    log.error("Failed to remove task {}: {}", taskId, error.getMessage(), error);
                    return Mono.just(false);
                });
    }
    
    /**
     * 获取队列长度
     * 
     * @return Mono<Long> 队列中的任务数量
     */
    public Mono<Long> getQueueSize() {
        String queueKey = QUEUE_KEY_PREFIX + "all";
        
        return redisTemplate.opsForZSet()
                .size(queueKey)
                .doOnSuccess(size -> log.debug("Queue size: {}", size))
                .onErrorResume(error -> {
                    log.error("Failed to get queue size: {}", error.getMessage());
                    return Mono.just(0L);
                });
    }
    
    /**
     * 检查任务是否在队列中
     * 
     * @param taskId 任务ID
     * @return Mono<Boolean> 是否在队列中
     */
    public Mono<Boolean> isInQueue(String taskId) {
        String queueKey = QUEUE_KEY_PREFIX + "all";
        
        return redisTemplate.opsForZSet()
                .score(queueKey, taskId)
                .map(score -> true)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.error("Failed to check if task {} is in queue: {}", taskId, error.getMessage());
                    return Mono.just(false);
                });
    }
    
    /**
     * 计算任务的分数
     * Score = priority.level * 1000000000 + timestamp
     * 这样可以实现：优先级高的先执行（level越小优先级越高），同优先级按时间先后
     * 
     * @param priority 任务优先级
     * @param submitTime 提交时间
     * @return 分数
     */
    private double calculateScore(TaskPriority priority, LocalDateTime submitTime) {
        long timestamp = submitTime.toEpochSecond(ZoneOffset.UTC);
        return priority.getLevel() * 1_000_000_000L + timestamp;
    }
}
