package com.ryu.blog.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.AiGenerationHistory;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.AiGenerationHistoryRepository;
import com.ryu.blog.service.*;
import com.ryu.blog.service.ai.AiProvider;
import com.ryu.blog.service.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI博客服务实现
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBlogServiceImpl implements AiBlogService {

    private final AiProviderFactory providerFactory;
    private final RateLimitService rateLimitService;
    private final AiTemplateService templateService;
    private final PromptEnhancer promptEnhancer;
    private final AiUsageStatisticsService statisticsService;
    private final ContentSafetyService contentSafetyService;
    private final AiGenerationHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<AiGenerationResult> generateBlogContent(AiGenerationRequest request) {
        log.info("开始生成博客内容: userId={}, mode={}", request.getUserId(), request.getMode());
        
        long startTime = System.currentTimeMillis();
        
        return rateLimitService.checkAndIncrement(request.getUserId())
                .then(promptEnhancer.enhance(request))
                .flatMap(enhancedPrompt -> {
                    // 更新请求中的提示词
                    AiGenerationRequest enhancedRequest = AiGenerationRequest.builder()
                            .mode(request.getMode())
                            .prompt(enhancedPrompt)
                            .content(request.getContent())
                            .templateId(request.getTemplateId())
                            .templateFields(request.getTemplateFields())
                            .language(request.getLanguage())
                            .tone(request.getTone())
                            .length(request.getLength())
                            .style(request.getStyle())
                            .providerName(request.getProviderName())
                            .modelName(request.getModelName())
                            .userId(request.getUserId())
                            .build();
                    
                    // 获取AI提供商并生成内容
                    return providerFactory.getProvider(request.getProviderName())
                            .flatMap(provider -> provider.generate(enhancedRequest)
                                    .map(result -> {
                                        // 计算生成时间
                                        long generationTime = System.currentTimeMillis() - startTime;
                                        result.setGenerationTime(generationTime);
                                        
                                        // 估算成本
                                        if (result.getTokenCount() != null) {
                                            double cost = statisticsService.estimateCost(
                                                    result.getProviderName(),
                                                    result.getModelName(),
                                                    result.getTokenCount() / 2, // 简化：假设一半是提示词
                                                    result.getTokenCount() / 2  // 一半是完成
                                            );
                                            result.setEstimatedCost(cost);
                                        }
                                        
                                        return result;
                                    })
                                    // 内容安全检查
                                    .flatMap(result -> checkContentSafety(result, request.getUserId()))
                                    .flatMap(result -> saveHistory(request, enhancedPrompt, result)
                                            .thenReturn(result))
                                    .flatMap(result -> recordUsage(request.getUserId(), result)
                                            .thenReturn(result)));
                })
                .doOnSuccess(result -> log.info("博客内容生成成功: userId={}, tokens={}, time={}ms",
                        request.getUserId(), result.getTokenCount(), result.getGenerationTime()))
                .doOnError(error -> log.error("博客内容生成失败: userId={}, error={}",
                        request.getUserId(), error.getMessage(), error));
    }

    @Override
    public Flux<String> generateBlogContentStream(AiGenerationRequest request) {
        log.info("开始流式生成博客内容: userId={}, mode={}", request.getUserId(), request.getMode());
        
        return rateLimitService.checkAndIncrement(request.getUserId())
                .then(promptEnhancer.enhance(request))
                .flatMapMany(enhancedPrompt -> {
                    // 更新请求中的提示词
                    AiGenerationRequest enhancedRequest = AiGenerationRequest.builder()
                            .mode(request.getMode())
                            .prompt(enhancedPrompt)
                            .content(request.getContent())
                            .templateId(request.getTemplateId())
                            .templateFields(request.getTemplateFields())
                            .language(request.getLanguage())
                            .tone(request.getTone())
                            .length(request.getLength())
                            .style(request.getStyle())
                            .providerName(request.getProviderName())
                            .modelName(request.getModelName())
                            .userId(request.getUserId())
                            .build();
                    
                    // 获取AI提供商并流式生成内容
                    return providerFactory.getProvider(request.getProviderName())
                            .flatMapMany(provider -> provider.generateStream(enhancedRequest));
                })
                .doOnComplete(() -> log.info("流式博客内容生成完成: userId={}", request.getUserId()))
                .doOnError(error -> log.error("流式博客内容生成失败: userId={}, error={}",
                        request.getUserId(), error.getMessage(), error));
    }

    @Override
    public Mono<AiGenerationResult> refineContent(AiGenerationRequest request) {
        log.info("开始优化内容: userId={}, contentLength={}", 
                request.getUserId(), 
                request.getContent() != null ? request.getContent().length() : 0);
        
        if (request.getContent() == null || request.getContent().isEmpty()) {
            return Mono.error(new BusinessException("优化内容不能为空"));
        }
        
        // 复用生成逻辑
        return generateBlogContent(request);
    }

    @Override
    public Mono<AiGenerationResult> generateWithTemplate(Long templateId, Map<String, String> variables, Long userId) {
        log.info("使用模板生成内容: userId={}, templateId={}", userId, templateId);
        
        return templateService.generatePromptFromTemplate(templateId, variables)
                .flatMap(prompt -> {
                    AiGenerationRequest request = AiGenerationRequest.builder()
                            .mode("template")
                            .prompt(prompt)
                            .userId(userId)
                            .templateId(templateId)
                            .templateFields(variables)
                            .build();
                    
                    return generateBlogContent(request);
                });
    }

    @Override
    public Flux<AiGenerationHistory> getGenerationHistory(Long userId, Pageable pageable) {
        log.debug("获取生成历史: userId={}", userId);
        
        return historyRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0, pageable);
    }

    @Override
    public Mono<AiGenerationHistory> getHistoryById(Long id, Long userId) {
        log.debug("获取历史记录: id={}, userId={}", id, userId);
        
        return historyRepository.findById(id)
                .filter(history -> Integer.valueOf(0).equals(history.getIsDeleted()))
                .filter(history -> history.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("历史记录不存在或无权访问")));
    }

    @Override
    public Mono<Boolean> deleteHistory(Long id, Long userId) {
        log.info("删除历史记录: id={}, userId={}", id, userId);
        
        return getHistoryById(id, userId)
                .flatMap(history -> {
                    history.setIsDeleted(1);
                    return historyRepository.save(history);
                })
                .thenReturn(true)
                .doOnSuccess(result -> log.info("历史记录删除成功: id={}", id))
                .doOnError(error -> log.error("历史记录删除失败: id={}", id, error));
    }

    @Override
    public Mono<AiGenerationResult> regenerate(Long historyId, Long userId) {
        log.info("重新生成内容: historyId={}, userId={}", historyId, userId);
        
        return getHistoryById(historyId, userId)
                .flatMap(history -> {
                    // 从历史记录重建请求
                    AiGenerationRequest request = AiGenerationRequest.builder()
                            .mode("free") // 历史记录重新生成默认使用自由模式
                            .prompt(history.getPrompt())
                            .providerName(history.getProviderName())
                            .modelName(history.getModelName())
                            .userId(userId)
                            .build();
                    
                    return generateBlogContent(request);
                });
    }

    @Override
    public Mono<Long> countGenerations(Long userId) {
        return historyRepository.countByUserIdAndIsDeleted(userId, 0);
    }

    /**
     * 保存生成历史
     */
    private Mono<Void> saveHistory(AiGenerationRequest request, String enhancedPrompt, AiGenerationResult result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            
            // 根据模式保存不同的prompt信息
            String originalPrompt = getOriginalPromptForHistory(request);
            
            AiGenerationHistory history = AiGenerationHistory.builder()
                    .userId(request.getUserId())
                    .prompt(originalPrompt)
                    .enhancedPrompt(enhancedPrompt)
                    .result(resultJson)
                    .providerName(result.getProviderName())
                    .modelName(result.getModelName())
                    .tokenCount(result.getTokenCount())
                    .cost(result.getEstimatedCost())
                    .generationTime(result.getGenerationTime())
                    .createTime(LocalDateTime.now())
                    .isDeleted(0)
                    .build();
            
            return historyRepository.save(history)
                    .doOnSuccess(saved -> log.debug("历史记录保存成功: id={}", saved.getId()))
                    .then();
        } catch (JsonProcessingException e) {
            log.error("序列化生成结果失败", e);
            return Mono.empty();
        }
    }

    /**
     * 获取用于保存历史记录的原始prompt
     */
    private String getOriginalPromptForHistory(AiGenerationRequest request) {
        if ("template".equals(request.getMode())) {
            // 模板模式：保存模板信息
            return String.format("Template[%d]: %s", 
                    request.getTemplateId(), 
                    request.getTemplateFields());
        } else if ("refine".equals(request.getMode())) {
            // 内容优化：保存优化指令
            return String.format("Refine: %s\nOriginal: %s", 
                    request.getPrompt(), 
                    request.getContent());
        } else {
            // 自由模式：直接保存prompt
            return request.getPrompt();
        }
    }

    /**
     * 记录使用情况
     */
    private Mono<Void> recordUsage(Long userId, AiGenerationResult result) {
        return statisticsService.recordUsage(
                userId,
                result.getProviderName(),
                result.getModelName(),
                result.getTokenCount(),
                result.getEstimatedCost(),
                result.getGenerationTime(),
                true
        );
    }

    /**
     * 检查内容安全
     * 
     * @param result 生成结果
     * @param userId 用户ID
     * @return 检查后的结果
     */
    private Mono<AiGenerationResult> checkContentSafety(AiGenerationResult result, Long userId) {
        return contentSafetyService.check(result.getContent())
                .flatMap(checkResult -> {
                    if (!checkResult.isSafe()) {
                        // 记录不安全内容事件
                        log.warn("检测到不安全内容: userId={}, reason={}, keywords={}",
                                userId, checkResult.getReason(), checkResult.getMatchedKeywords());
                        
                        // 拒绝不安全内容
                        return Mono.error(new BusinessException(
                                "生成的内容包含不安全或敏感信息，已被拒绝。原因: " + checkResult.getReason()));
                    }
                    
                    log.debug("内容安全检查通过: userId={}", userId);
                    return Mono.just(result);
                })
                .doOnError(error -> {
                    if (!(error instanceof BusinessException)) {
                        log.error("内容安全检查失败: userId={}", userId, error);
                    }
                });
    }
}
