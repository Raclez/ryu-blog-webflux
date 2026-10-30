package com.ryu.blog.service;

import com.ryu.blog.enums.TaskPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

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
    private static final String PROCESSING_KEY = "task:processing:all";

    /**
     * 延迟区：score = 计划执行时间（epoch 秒）。
     *
     * <p>主队列的 score 里高位是优先级（{@code priority.level * 1e9}），
     * 低位才是时间 —— 它表达的是「先执行谁」，不是「什么时候执行」。
     * dequeue 的 Lua 脚本里<b>没有任何到期闸门</b>：进了主队列的任务下一轮就会被取出。
     * 所以延迟任务必须待在另一个 key 里，由 {@code ScheduledTaskPromoter} 到期后再搬进主队列。
     */
    private static final String SCHEDULED_KEY = "task:scheduled:all";

    /** 单次回收的最大任务数，避免一次拉太多把队列打爆。 */
    private static final int REQUEUE_BATCH_SIZE = 100;

    /**
     * 可见性超时（秒）：任务出队后在这个时间内必须 ack，
     * 否则会被 {@link #requeueExpired()} 视为「执行者已崩溃」重新入队。
     *
     * <p>必须明显大于任务执行超时（{@code task.executor.timeout.default}，默认 600 秒），
     * 否则还在正常执行的长任务会被重复投递。
     */
    @Value("${task.queue.visibility-timeout-seconds:900}")
    private long visibilityTimeoutSeconds;

    /**
     * Lua 脚本：原子性地从队列中取出任务
     *
     * <p>出队的同时把任务写进 processing 集合（score = 可见性截止时间）。
     * 原先只做 ZRANGE + ZREM：任务一旦出队就从 Redis 消失，
     * 执行进程崩溃 = 任务永久丢失，既不会重投也没有任何痕迹可查。
     */
    private static final String DEQUEUE_SCRIPT =
            "local queueKey = KEYS[1]\n" +
            "local processingKey = KEYS[2]\n" +
            "local batchSize = tonumber(ARGV[1])\n" +
            "local deadline = tonumber(ARGV[2])\n" +
            "local tasks = redis.call('ZRANGE', queueKey, 0, batchSize - 1)\n" +
            "if #tasks > 0 then\n" +
            "    redis.call('ZREM', queueKey, unpack(tasks))\n" +
            "    for i = 1, #tasks do\n" +
            "        redis.call('ZADD', processingKey, deadline, tasks[i])\n" +
            "    end\n" +
            "end\n" +
            "return tasks";

    /**
     * Lua 脚本：把到期任务从延迟区搬进待执行队列。
     *
     * <p>{@code ZREM} 的返回值即「抢占令牌」：多实例同时跑这个脚本时，
     * 只有真正删掉记录的那个实例负责搬运，不会重复投递。
     */
    private static final String PROMOTE_SCRIPT =
            "local scheduledKey = KEYS[1]\n" +
            "local queueKey = KEYS[2]\n" +
            "local processingKey = KEYS[3]\n" +
            "local taskId = ARGV[1]\n" +
            "local score = ARGV[2]\n" +
            "local s = redis.call('ZSCORE', scheduledKey, taskId)\n" +
            "if s then\n" +
            "    if redis.call('ZREM', scheduledKey, taskId) == 1 then\n" +
            "        redis.call('ZADD', queueKey, score, taskId)\n" +
            "        return 1\n" +
            "    end\n" +
            "end\n" +
            "if not redis.call('ZSCORE', queueKey, taskId) and not redis.call('ZSCORE', processingKey, taskId) then\n" +
            "    redis.call('ZADD', queueKey, score, taskId)\n" +
            "    return 2\n" +
            "end\n" +
            "return 0";
    
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

        long deadline = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + visibilityTimeoutSeconds;

        return redisTemplate.execute(
                script,
                Arrays.asList(queueKey, PROCESSING_KEY),
                Arrays.asList(String.valueOf(batchSize), String.valueOf(deadline))
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
     * 确认任务已处理完毕，把它从 processing 集合里移除
     *
     * <p>执行器在任务结束（成功 / 失败 / 跳过）时都必须调用，
     * 否则任务会在可见性超时后被重新投递一次。
     *
     * @param taskId 任务ID
     * @return 是否真的移除了
     */
    public Mono<Boolean> ack(String taskId) {
        return redisTemplate.opsForZSet()
                .remove(PROCESSING_KEY, taskId)
                .map(count -> count > 0)
                .onErrorResume(error -> {
                    log.warn("Ack task {} failed: {}", taskId, error.getMessage(), error);
                    return Mono.just(false);
                });
    }

    /**
     * 回收可见性超时的任务：它们被视为「执行者已崩溃」，重新放回待执行队列
     *
     * <p>重新入队使用 NORMAL 优先级 —— 原始优先级没有保存在 processing 集合里，
     * 且这些任务本来就该尽快重试。每次最多回收 100 条，避免一次拉太多。
     *
     * @return 实际重新入队的任务数
     */
    public Mono<Long> requeueExpired() {
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        String queueKey = QUEUE_KEY_PREFIX + "all";

        return redisTemplate.opsForZSet()
                // Spring Data Redis 3.2 的 ReactiveZSetOperations 只有 Range<Double> 版本的
                // rangeByScore，没有 (key, min, max) 重载；Limit 让条数在 Redis 端就截断。
                .rangeByScore(PROCESSING_KEY, Range.closed(0D, (double) now), Limit.limit().count(REQUEUE_BATCH_SIZE))
                // remove 的返回值用于「抢占」：只有真正删掉这条记录的实例才负责重新入队，
                // 多实例并发回收时不会重复投递
                .flatMap(taskId -> redisTemplate.opsForZSet()
                        .remove(PROCESSING_KEY, taskId)
                        .filter(removed -> removed > 0)
                        .flatMap(removed -> redisTemplate.opsForZSet()
                                .add(queueKey, taskId, calculateScore(TaskPriority.NORMAL,
                                        LocalDateTime.now().plusSeconds(1))))
                        .doOnNext(added -> log.warn("Requeued expired task {} (visibility timeout)", taskId))
                        .thenReturn(1L)
                        .defaultIfEmpty(0L))
                .reduce(0L, Long::sum)
                .onErrorResume(error -> {
                    log.error("Requeue expired tasks failed: {}", error.getMessage(), error);
                    return Mono.just(0L);
                });
    }

    /**
     * 将延迟任务放入延迟区
     *
     * <p>延迟任务<b>不进主队列</b>：主队列的出队脚本没有到期闸门，
     * 进去就会被立刻执行。
     *
     * @param taskId    任务ID
     * @param executeAt 计划执行时间
     * @return Mono<Void>
     */
    public Mono<Void> enqueueScheduled(String taskId, LocalDateTime executeAt) {
        double score = executeAt.toEpochSecond(ZoneOffset.UTC);

        log.debug("Enqueuing scheduled task {} at {}", taskId, executeAt);

        return redisTemplate.opsForZSet()
                .add(SCHEDULED_KEY, taskId, score)
                .doOnSuccess(added -> {
                    if (Boolean.TRUE.equals(added)) {
                        log.info("Scheduled task {} added to delayed queue, fires at {}", taskId, executeAt);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to enqueue scheduled task {}: {}", taskId, error.getMessage(), error);
                    return Mono.error(error);
                })
                .then();
    }

    /**
     * 把一个到期任务从延迟区搬进待执行队列
     *
     * <p>用 Lua 保证「搬走」这件事是原子的：{@code ZREM} 的返回值就是抢占令牌 ——
     * 多实例同时跑，只有真正删掉记录的那个实例负责搬运，不会重复投递。
     *
     * <p>脚本还兜住了「Redis 丢数据」：任务不在延迟区、也不在队列和处理中时，
     * 重新把它放回队列（返回 2）。这时任务能不能执行仍由 handler 自校验决定。
     *
     * @param taskId    任务ID
     * @param priority  任务优先级
     * @param executeAt 计划执行时间（作为队列 score 的低位，保持同优先级下的到期先后顺序）
     * @return 1 = 从延迟区搬入，2 = 重建（Redis 丢过数据），0 = 已在队列/处理中或已被别人搬走
     */
    public Mono<Integer> promoteToQueue(String taskId, TaskPriority priority, LocalDateTime executeAt) {
        double score = calculateScore(priority, executeAt);

        return redisTemplate.execute(
                RedisScript.of(PROMOTE_SCRIPT, Integer.class),
                Arrays.asList(SCHEDULED_KEY, QUEUE_KEY_PREFIX + "all", PROCESSING_KEY),
                Arrays.asList(taskId, String.valueOf(score))
        )
        // ReactiveRedisTemplate#execute 返回的是 Flux（脚本可能返回多行），
        // 这里脚本只返回一个整数，取第一个即可；空结果按「没有搬运」处理。
        .next()
        .defaultIfEmpty(0)
        .doOnNext(result -> {
            if (result != null && result > 0) {
                log.info("Promoted task {} to execution queue (code={})", taskId, result);
            }
        })
        .onErrorResume(error -> {
            log.error("Failed to promote task {}: {}", taskId, error.getMessage(), error);
            return Mono.just(0);
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
                // 延迟区的同一任务也要一起删掉：否则任务会被「重新入队」复活，
                // 取消一个定时任务后它仍然会在到点时被执行。
                .flatMap(removed -> redisTemplate.opsForZSet()
                        .remove(SCHEDULED_KEY, taskId)
                        .map(scheduledRemoved -> removed || scheduledRemoved > 0)
                        .defaultIfEmpty(removed))
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
     * <p>延迟区里的任务同样算「在队列中」—— 它们只是还没到执行时间。
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
                .flatMap(inQueue -> {
                    if (inQueue) {
                        return Mono.just(true);
                    }
                    return redisTemplate.opsForZSet()
                            .score(SCHEDULED_KEY, taskId)
                            .map(score -> true)
                            .defaultIfEmpty(false);
                })
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
