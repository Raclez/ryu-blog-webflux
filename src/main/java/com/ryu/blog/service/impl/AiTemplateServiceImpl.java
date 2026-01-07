package com.ryu.blog.service.impl;

import com.ryu.blog.entity.AiContentTemplate;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.AiContentTemplateRepository;
import com.ryu.blog.service.AiTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI内容模板服务实现
 * 
 * <p>提供内容模板的CRUD操作和模板变量替换功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTemplateServiceImpl implements AiTemplateService {

    private final AiContentTemplateRepository templateRepository;

    /**
     * 模板变量占位符正则表达式：匹配 {{variableName}} 格式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    @Override
    public Mono<AiContentTemplate> createTemplate(AiContentTemplate template) {
        log.debug("创建模板: name={}", template.getName());
        
        return existsByName(template.getName())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException("模板名称已存在: " + template.getName()));
                    }
                    
                    // 设置默认值
                    template.setCreateTime(LocalDateTime.now());
                    template.setUpdateTime(LocalDateTime.now());
                    template.setIsDeleted(0);
                    
                    if (template.getIsSystem() == null) {
                        template.setIsSystem(0);
                    }
                    
                    return templateRepository.save(template);
                })
                .doOnSuccess(saved -> log.info("模板创建成功: id={}, name={}", saved.getId(), saved.getName()))
                .doOnError(error -> log.error("模板创建失败: name={}", template.getName(), error));
    }

    @Override
    public Mono<AiContentTemplate> updateTemplate(Long id, AiContentTemplate template) {
        log.debug("更新模板: id={}", id);
        
        return templateRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("模板不存在: " + id)))
                .flatMap(existing -> {
                    // 系统模板不允许修改
                    if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
                        return Mono.error(new BusinessException("系统模板不允许修改"));
                    }
                    
                    // 更新字段
                    if (template.getName() != null) {
                        existing.setName(template.getName());
                    }
                    if (template.getDescription() != null) {
                        existing.setDescription(template.getDescription());
                    }
                    if (template.getType() != null) {
                        existing.setType(template.getType());
                    }
                    if (template.getPromptTemplate() != null) {
                        existing.setPromptTemplate(template.getPromptTemplate());
                    }
                    if (template.getStructure() != null) {
                        existing.setStructure(template.getStructure());
                    }
                    
                    existing.setUpdateTime(LocalDateTime.now());
                    
                    return templateRepository.save(existing);
                })
                .doOnSuccess(updated -> log.info("模板更新成功: id={}", id))
                .doOnError(error -> log.error("模板更新失败: id={}", id, error));
    }

    @Override
    public Mono<AiContentTemplate> updateTemplate(AiContentTemplate template) {
        return updateTemplate(template.getId(), template);
    }

    @Override
    public Mono<Boolean> deleteTemplate(Long id) {
        log.debug("删除模板: id={}", id);
        
        return templateRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("模板不存在: " + id)))
                .flatMap(template -> {
                    // 系统模板不允许删除
                    if (template.getIsSystem() != null && template.getIsSystem() == 1) {
                        return Mono.error(new BusinessException("系统模板不允许删除"));
                    }
                    
                    // 逻辑删除
                    template.setIsDeleted(1);
                    template.setUpdateTime(LocalDateTime.now());
                    
                    return templateRepository.save(template)
                            .thenReturn(true);
                })
                .doOnSuccess(result -> log.info("模板删除成功: id={}", id))
                .doOnError(error -> log.error("模板删除失败: id={}", id, error));
    }

    @Override
    public Mono<Boolean> deleteTemplate(Long id, Long userId) {
        log.debug("删除模板: id={}, userId={}", id, userId);
        
        return templateRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("模板不存在: " + id)))
                .flatMap(template -> {
                    // 系统模板不允许删除
                    if (template.getIsSystem() != null && template.getIsSystem() == 1) {
                        return Mono.error(new BusinessException("系统模板不允许删除"));
                    }
                    
                    // 检查权限：只能删除自己创建的模板
                    if (template.getUserId() != null && !template.getUserId().equals(userId)) {
                        return Mono.error(new BusinessException("无权删除此模板"));
                    }
                    
                    // 逻辑删除
                    template.setIsDeleted(1);
                    template.setUpdateTime(LocalDateTime.now());
                    
                    return templateRepository.save(template)
                            .thenReturn(true);
                })
                .doOnSuccess(result -> log.info("模板删除成功: id={}", id))
                .doOnError(error -> log.error("模板删除失败: id={}", id, error));
    }

    @Override
    public Mono<AiContentTemplate> getTemplateById(Long id) {
        log.debug("获取模板: id={}", id);
        
        return templateRepository.findById(id)
                .filter(template -> template.getIsDeleted() == 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("模板不存在: " + id)));
    }

    @Override
    public Flux<AiContentTemplate> getTemplatesByType(String type) {
        log.debug("根据类型获取模板列表: type={}", type);
        
        return templateRepository.findByTypeAndIsDeleted(type, 0);
    }

    @Override
    public Flux<AiContentTemplate> getSystemTemplates() {
        log.debug("获取系统模板列表");
        
        return templateRepository.findByIsSystemAndIsDeleted(1, 0);
    }

    @Override
    public Flux<AiContentTemplate> getUserTemplates(Long userId, Pageable pageable) {
        log.debug("获取用户自定义模板列表: userId={}", userId);
        
        return templateRepository.findByUserIdAndIsDeleted(userId, 0, pageable);
    }

    @Override
    public Flux<AiContentTemplate> getAllTemplates(Pageable pageable) {
        log.debug("获取所有模板列表");
        
        return templateRepository.findByIsDeletedOrderByCreateTimeDesc(0, pageable);
    }

    @Override
    public Flux<AiContentTemplate> getAllTemplates() {
        log.debug("获取所有模板列表（无分页）");
        
        return templateRepository.findByIsDeleted(0);
    }

    @Override
    public Flux<AiContentTemplate> getEnabledTemplates() {
        log.debug("获取启用的模板列表");
        
        return templateRepository.findByIsDeleted(0);
    }

    @Override
    public String replaceVariables(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        log.debug("替换模板变量: variableCount={}", variables.size());
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.getOrDefault(variableName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        
        String replaced = result.toString();
        
        // 检查是否还有未替换的占位符
        if (VARIABLE_PATTERN.matcher(replaced).find()) {
            log.warn("模板中存在未替换的变量占位符");
        }
        
        return replaced;
    }

    @Override
    public Mono<String> generatePromptFromTemplate(Long templateId, Map<String, String> variables) {
        log.debug("根据模板生成提示词: templateId={}", templateId);
        
        return getTemplateById(templateId)
                .map(template -> {
                    String promptTemplate = template.getPromptTemplate();
                    return replaceVariables(promptTemplate, variables);
                })
                .doOnSuccess(prompt -> log.debug("提示词生成成功: length={}", prompt.length()))
                .doOnError(error -> log.error("提示词生成失败: templateId={}", templateId, error));
    }

    @Override
    public Mono<Boolean> existsByName(String name) {
        return templateRepository.existsByNameAndIsDeleted(name, 0);
    }

    @Override
    public Mono<Long> countTemplates() {
        return templateRepository.countByIsDeleted(0);
    }

    @Override
    public Mono<Long> countTemplatesByType(String type) {
        return templateRepository.countByTypeAndIsDeleted(type, 0);
    }

    @Override
    public Mono<Long> countUserTemplates(Long userId) {
        return templateRepository.countByUserIdAndIsDeleted(userId, 0);
    }
}
