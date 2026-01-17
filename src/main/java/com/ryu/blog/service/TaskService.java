package com.ryu.blog.service;

import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskType;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 任务服务接口
 * 提供任务的创建、查询、取消、重试等核心功能
 * 
 * @author ryu
 */
public interface TaskService {
    
    /**
     * 提交任务
     * 
     * @param taskType 任务类型
     * @param request 请求参数对象
     * @param userId 用户ID
     * @param priority 任务优先级
     * @return 任务ID
     */
    Mono<Long> submitTask(TaskType taskType, Object request, Long userId, TaskPriority priority);
    
    /**
     * 查询任务状态
     * 使用SaToken自动获取当前登录用户进行权限验证
     * 
     * @param taskId 任务ID
     * @return 任务实体
     */
    Mono<AsyncTask> getTaskStatus(Long taskId);
    
    /**
     * 查询任务结果
     * 使用SaToken自动获取当前登录用户进行权限验证
     * 
     * @param taskId 任务ID
     * @return 任务结果对象
     */
    Mono<Object> getTaskResult(Long taskId);
    
    /**
     * 取消任务
     * 使用SaToken自动获取当前登录用户进行权限验证
     * 
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    Mono<Boolean> cancelTask(Long taskId);
    
    /**
     * 重试任务
     * 使用SaToken自动获取当前登录用户进行权限验证
     * 
     * @param taskId 原任务ID
     * @return 新任务ID
     */
    Mono<Long> retryTask(Long taskId);
    
    /**
     * 查询用户任务列表
     * 
     * @param userId 用户ID
     * @param taskType 任务类型（可选，null表示查询所有类型）
     * @param pageable 分页参数
     * @return 任务列表
     */
    Flux<AsyncTask> getUserTasks(Long userId, TaskType taskType, Pageable pageable);
    
    /**
     * 统计用户任务数量
     * 
     * @param userId 用户ID
     * @param taskType 任务类型（可选，null表示统计所有类型）
     * @return 任务总数
     */
    Mono<Long> countUserTasks(Long userId, TaskType taskType);
    
    /**
     * 清理过期任务
     * 
     * @return 清理的任务数量
     */
    Mono<Integer> cleanExpiredTasks();
}
