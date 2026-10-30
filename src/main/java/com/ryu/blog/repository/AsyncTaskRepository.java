package com.ryu.blog.repository;

import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 异步任务存储库接口
 * 
 * @author ryu
 */
@Repository
public interface AsyncTaskRepository extends R2dbcRepository<AsyncTask, Long> {
    
    /**
     * 根据用户ID和任务类型查询任务列表
     * @param userId 用户ID
     * @param taskType 任务类型
     * @param pageable 分页参数
     * @return 任务列表
     */
    @Query("SELECT * FROM t_async_tasks WHERE user_id = :userId AND task_type = :taskType AND is_deleted = 0 " +
           "ORDER BY submit_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<AsyncTask> findByUserIdAndTaskType(Long userId, TaskType taskType, Pageable pageable);
    
    /**
     * 根据用户ID和任务类型查询任务总数
     * @param userId 用户ID
     * @param taskType 任务类型
     * @return 任务总数
     */
    @Query("SELECT COUNT(*) FROM t_async_tasks WHERE user_id = :userId AND task_type = :taskType AND is_deleted = 0")
    Mono<Long> countByUserIdAndTaskType(Long userId, TaskType taskType);
    
    /**
     * 查询指定状态下、且已完成时间早于给定阈值的任务（过期任务清理用）。
     *
     * <p>这里刻意<b>不接收 Pageable</b>：Spring Data R2DBC 会把
     * {@code :#{#pageable.pageSize}} 这类 SpEL 求值成查询参数，而
     * {@code Pageable.unpaged()} 的 {@code getPageSize()} / {@code getOffset()}
     * 会直接抛 {@code UnsupportedOperationException}
     * （见 {@code Unpaged#getPageSize}），调用方一旦传 {@code unpaged()}
     * 就会在<b>查询构造阶段</b>炸掉（不是没数据，是整个查询失败）。
     *
     * <p>过期判定也一并下推到数据库，避免把整批任务拉回内存再过滤。
     *
     * @param status    任务状态
     * @param threshold 完成时间的上界（早于该时刻即视为过期）
     * @return 过期任务流
     */
    @Query("SELECT * FROM t_async_tasks WHERE status = :status AND is_deleted = 0 " +
           "AND complete_time IS NOT NULL AND complete_time < :threshold " +
           "ORDER BY submit_time")
    Flux<AsyncTask> findExpiredByStatus(TaskStatus status, LocalDateTime threshold);

    /**
     * 根据状态查询任务总数
     * @param status 任务状态
     * @return 任务总数
     */
    @Query("SELECT COUNT(*) FROM t_async_tasks WHERE status = :status AND is_deleted = 0")
    Mono<Long> countByStatus(TaskStatus status);

    /**
     * 查询到期的延迟任务（status=PENDING 且计划执行时间已到）
     *
     * <p><b>DB 是延迟任务是否到期的唯一权威</b>：Redis 里的延迟区只是加速结构，
     * 清空 Redis 之后任务仍然能被这条查询捞回来重新入队。
     *
     * <p>刻意不接收 Pageable —— 与 {@link #findExpiredByStatus} 同理，
     * {@code :#{#pageable.pageSize}} 遇到 {@code Pageable.unpaged()} 会在查询构造阶段
     * 直接抛 {@code UnsupportedOperationException}。条数限制直接用 LIMIT 下推到 SQL。
     *
     * @param status 任务状态（PENDING）
     * @param now    当前时间
     * @param limit  最多返回多少条
     * @return 到期任务流
     */
    @Query("SELECT * FROM t_async_tasks WHERE status = :status AND is_deleted = 0 " +
           "AND execute_at IS NOT NULL AND execute_at <= :now " +
           "ORDER BY execute_at LIMIT :limit")
    Flux<AsyncTask> findDuePendingTasks(TaskStatus status, LocalDateTime now, int limit);

    /**
     * 查询某用户某类型下仍处于指定状态的任务
     *
     * <p>命中已有的 {@code idx_async_tasks_user_type_status (user_id, task_type, status)} 索引。
     * 用于「取消定时发布」时定位该文章对应的待执行任务。
     *
     * @param userId   用户ID
     * @param taskType 任务类型
     * @param status   任务状态
     * @return 任务流
     */
    @Query("SELECT * FROM t_async_tasks WHERE user_id = :userId AND task_type = :taskType " +
           "AND status = :status AND is_deleted = 0")
    Flux<AsyncTask> findActiveByUserIdAndType(Long userId, TaskType taskType, TaskStatus status);
    
    /**
     * 根据用户ID查询任务列表
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 任务列表
     */
    @Query("SELECT * FROM t_async_tasks WHERE user_id = :userId AND is_deleted = 0 " +
           "ORDER BY submit_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<AsyncTask> findByUserId(Long userId, Pageable pageable);
    
    /**
     * 根据用户ID查询任务总数
     * @param userId 用户ID
     * @return 任务总数
     */
    @Query("SELECT COUNT(*) FROM t_async_tasks WHERE user_id = :userId AND is_deleted = 0")
    Mono<Long> countByUserId(Long userId);
}
