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
     * 根据状态查询任务列表
     * @param status 任务状态
     * @param pageable 分页参数
     * @return 任务列表
     */
    @Query("SELECT * FROM t_async_tasks WHERE status = :status AND is_deleted = 0 " +
           "ORDER BY priority, submit_time LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<AsyncTask> findByStatus(TaskStatus status, Pageable pageable);
    
    /**
     * 根据状态查询任务总数
     * @param status 任务状态
     * @return 任务总数
     */
    @Query("SELECT COUNT(*) FROM t_async_tasks WHERE status = :status AND is_deleted = 0")
    Mono<Long> countByStatus(TaskStatus status);
    
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
