package com.ryu.blog.service;

import com.ryu.blog.enums.TaskType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务处理器注册器
 * 自动扫描并注册所有 TaskHandler 实现
 * 
 * @author ryu
 */
@Slf4j
@Component
public class TaskHandlerRegistry {
    
    private final Map<TaskType, TaskHandler<?, ?>> handlers = new ConcurrentHashMap<>();
    
    private final List<TaskHandler<?, ?>> taskHandlers;
    
    /**
     * 构造函数，Spring 会自动注入所有 TaskHandler 实现
     * 
     * @param taskHandlers 所有 TaskHandler 实现的列表
     */
    public TaskHandlerRegistry(List<TaskHandler<?, ?>> taskHandlers) {
        this.taskHandlers = taskHandlers;
    }
    
    /**
     * 初始化方法，在 Bean 创建后自动执行
     * 扫描并注册所有 TaskHandler 实现
     */
    @PostConstruct
    public void init() {
        log.info("Initializing TaskHandlerRegistry...");
        
        for (TaskHandler<?, ?> handler : taskHandlers) {
            TaskType taskType = handler.getTaskType();
            
            if (handlers.containsKey(taskType)) {
                log.warn("Duplicate handler found for task type: {}. Overwriting with {}",
                        taskType, handler.getClass().getSimpleName());
            }
            
            handlers.put(taskType, handler);
            log.info("Registered handler for task type {}: {}",
                    taskType, handler.getClass().getSimpleName());
        }
        
        log.info("TaskHandlerRegistry initialized with {} handlers", handlers.size());
    }
    
    /**
     * 根据任务类型获取对应的处理器
     * 
     * @param taskType 任务类型
     * @return TaskHandler 实例
     * @throws IllegalArgumentException 如果找不到对应的处理器
     */
    @SuppressWarnings("unchecked")
    public <T, R> TaskHandler<T, R> getHandler(TaskType taskType) {
        TaskHandler<?, ?> handler = handlers.get(taskType);
        
        if (handler == null) {
            log.error("No handler found for task type: {}", taskType);
            throw new IllegalArgumentException("No handler found for task type: " + taskType);
        }
        
        return (TaskHandler<T, R>) handler;
    }
    
    /**
     * 检查是否存在指定任务类型的处理器
     * 
     * @param taskType 任务类型
     * @return 是否存在处理器
     */
    public boolean hasHandler(TaskType taskType) {
        return handlers.containsKey(taskType);
    }
    
    /**
     * 获取所有已注册的任务类型
     * 
     * @return 任务类型集合
     */
    public java.util.Set<TaskType> getRegisteredTaskTypes() {
        return handlers.keySet();
    }
}
