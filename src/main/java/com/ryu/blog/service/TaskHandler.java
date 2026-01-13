package com.ryu.blog.service;

import com.ryu.blog.enums.TaskType;
import reactor.core.publisher.Mono;

/**
 * 任务处理器接口
 * 定义了任务处理的通用方法，支持多种任务类型扩展
 * 
 * @param <T> 请求参数类型
 * @param <R> 返回结果类型
 * @author ryu
 */
public interface TaskHandler<T, R> {
    
    /**
     * 获取任务类型
     * 
     * @return 任务类型
     */
    TaskType getTaskType();
    
    /**
     * 执行任务
     * 
     * @param request 任务请求参数
     * @return 任务结果
     */
    Mono<R> execute(T request);
    
    /**
     * 更新任务进度（可选）
     * 默认实现为空，子类可以根据需要重写
     * 
     * @param taskId 任务ID
     * @param progress 进度百分比（0-100）
     * @return Mono<Void>
     */
    default Mono<Void> updateProgress(String taskId, int progress) {
        return Mono.empty();
    }
    
    /**
     * 取消任务（可选）
     * 默认实现为空，子类可以根据需要重写
     * 
     * @param taskId 任务ID
     * @return Mono<Void>
     */
    default Mono<Void> cancel(String taskId) {
        return Mono.empty();
    }
}
