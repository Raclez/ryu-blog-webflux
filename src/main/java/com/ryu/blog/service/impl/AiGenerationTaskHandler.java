package com.ryu.blog.service.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.service.AiBlogService;
import com.ryu.blog.service.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * AI 生成任务处理器
 * 处理 AI 博客内容生成任务
 * 
 * @author ryu
 */
@Slf4j
@Component
public class AiGenerationTaskHandler implements TaskHandler<AiGenerationRequest, AiGenerationResult> {
    
    private final AiBlogService aiBlogService;
    
    public AiGenerationTaskHandler(AiBlogService aiBlogService) {
        this.aiBlogService = aiBlogService;
    }
    
    @Override
    public TaskType getTaskType() {
        return TaskType.AI_GENERATION;
    }
    
    @Override
    public Mono<AiGenerationResult> execute(AiGenerationRequest request) {
        log.info("Executing AI generation task with mode: {}", request.getMode());
        
        return aiBlogService.generateBlogContent(request)
                .doOnSuccess(result -> 
                    log.info("AI generation task completed successfully. Title: {}, Tokens: {}", 
                            result.getTitle(), result.getTokenCount())
                )
                .doOnError(error -> 
                    log.error("AI generation task failed: {}", error.getMessage(), error)
                );
    }
    
    @Override
    public Mono<Void> updateProgress(String taskId, int progress) {
        // AI 生成任务目前不支持进度更新
        // 可以在未来实现流式生成时添加进度跟踪
        log.debug("Progress update for task {}: {}%", taskId, progress);
        return Mono.empty();
    }
    
    @Override
    public Mono<Void> cancel(String taskId) {
        // AI 生成任务的取消逻辑
        // 由于 AI 调用通常是原子操作，取消主要是标记状态
        log.info("Cancelling AI generation task: {}", taskId);
        
        // 这里可以添加取消逻辑，例如：
        // 1. 中断正在进行的 AI 调用（如果支持）
        // 2. 清理相关资源
        // 3. 记录取消日志
        
        return Mono.empty();
    }
}
