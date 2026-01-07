package com.ryu.blog.service;

import com.ryu.blog.entity.AiContentTemplate;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AI内容模板服务接口
 * 
 * <p>提供内容模板的CRUD操作和模板变量替换功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface AiTemplateService {

    /**
     * 创建模板
     * 
     * @param template 模板信息
     * @return 创建的模板
     */
    Mono<AiContentTemplate> createTemplate(AiContentTemplate template);

    /**
     * 更新模板
     * 
     * @param id 模板ID
     * @param template 模板信息
     * @return 更新后的模板
     */
    Mono<AiContentTemplate> updateTemplate(Long id, AiContentTemplate template);

    /**
     * 更新模板（从模板对象中获取ID）
     * 
     * @param template 模板信息（包含ID）
     * @return 更新后的模板
     */
    Mono<AiContentTemplate> updateTemplate(AiContentTemplate template);

    /**
     * 删除模板（逻辑删除）
     * 
     * @param id 模板ID
     * @param userId 用户ID（用于权限检查）
     * @return 是否删除成功
     */
    Mono<Boolean> deleteTemplate(Long id, Long userId);

    /**
     * 删除模板（逻辑删除，不检查权限）
     * 
     * @param id 模板ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteTemplate(Long id);

    /**
     * 根据ID获取模板
     * 
     * @param id 模板ID
     * @return 模板信息
     */
    Mono<AiContentTemplate> getTemplateById(Long id);

    /**
     * 根据类型获取模板列表
     * 
     * @param type 模板类型
     * @return 模板列表
     */
    Flux<AiContentTemplate> getTemplatesByType(String type);

    /**
     * 获取系统模板列表
     * 
     * @return 系统模板列表
     */
    Flux<AiContentTemplate> getSystemTemplates();

    /**
     * 根据用户ID获取自定义模板列表
     * 
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 自定义模板列表
     */
    Flux<AiContentTemplate> getUserTemplates(Long userId, Pageable pageable);

    /**
     * 获取所有模板列表（分页）
     * 
     * @param pageable 分页参数
     * @return 模板列表
     */
    Flux<AiContentTemplate> getAllTemplates(Pageable pageable);

    /**
     * 获取所有模板列表（不分页）
     * 
     * @return 模板列表
     */
    Flux<AiContentTemplate> getAllTemplates();

    /**
     * 获取启用的模板列表
     * 
     * @return 启用的模板列表
     */
    Flux<AiContentTemplate> getEnabledTemplates();

    /**
     * 替换模板变量
     * 
     * <p>将模板中的占位符（如 {{topic}}, {{language}}）替换为实际值。
     * 
     * @param template 模板字符串
     * @param variables 变量映射
     * @return 替换后的字符串
     */
    String replaceVariables(String template, Map<String, String> variables);

    /**
     * 根据模板ID生成提示词
     * 
     * @param templateId 模板ID
     * @param variables 变量映射
     * @return 生成的提示词
     */
    Mono<String> generatePromptFromTemplate(Long templateId, Map<String, String> variables);

    /**
     * 检查模板名称是否存在
     * 
     * @param name 模板名称
     * @return 是否存在
     */
    Mono<Boolean> existsByName(String name);

    /**
     * 统计模板数量
     * 
     * @return 模板数量
     */
    Mono<Long> countTemplates();

    /**
     * 根据类型统计模板数量
     * 
     * @param type 模板类型
     * @return 模板数量
     */
    Mono<Long> countTemplatesByType(String type);

    /**
     * 根据用户ID统计自定义模板数量
     * 
     * @param userId 用户ID
     * @return 模板数量
     */
    Mono<Long> countUserTemplates(Long userId);
}
