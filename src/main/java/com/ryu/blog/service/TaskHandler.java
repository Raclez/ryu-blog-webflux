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

    /**
     * 声明请求参数的运行时类型（可选）
     *
     * <p>任务在 DB 里存的是 JSON 字符串，执行前必须还原成对象。
     * 此前这一步是在执行器里按任务类型硬编码分支（只有 AI_GENERATION 有分支），
     * 其余类型一律退化成 {@code Map} —— handler 拿到的根本不是自己声明的类型，
     * 泛型擦除之后在运行期才炸成 ClassCastException。
     *
     * <p>实现这个方法即可让注册器按声明的类型反序列化；
     * 返回 {@code null}（默认）表示不声明类型，退化为 {@code Map}。
     *
     * @return 请求参数类型，未声明时返回 null
     */
    default Class<T> getRequestType() {
        return null;
    }

    /**
     * 成功通知文案（可选）
     *
     * <p>返回 {@code null} 表示使用默认文案「任务执行成功」。
     *
     * @param result 任务执行结果
     * @return 通知文案
     */
    default String successMessage(Object result) {
        return null;
    }

    /**
     * 失败通知文案（可选）
     *
     * <p>返回 {@code null} 表示使用默认文案「任务执行失败: {错误信息}」。
     *
     * @param error 执行异常
     * @return 通知文案
     */
    default String failureMessage(Throwable error) {
        return null;
    }
}
