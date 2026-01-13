package com.ryu.blog.service.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.service.PromptEnhancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 提示词增强器实现
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptEnhancerImpl implements PromptEnhancer {

    @Override
    public Mono<String> enhance(AiGenerationRequest request) {
        log.debug("增强提示词: mode={}", request.getMode());
        
        return Mono.fromCallable(() -> {
            StringBuilder enhanced = new StringBuilder();
            
            // 添加系统指令
            String systemPrompt = buildSystemPrompt(request);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                enhanced.append(systemPrompt).append("\n\n");
            }
            
            // 根据模式处理不同的prompt构建逻辑
            if ("refine".equals(request.getMode())) {
                // 内容优化模式
                enhanced.append("## 优化任务\n");
                if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
                    enhanced.append(request.getPrompt()).append("\n\n");
                }
                
                if (request.getContent() != null && !request.getContent().isEmpty()) {
                    enhanced.append("## 原始内容\n");
                    enhanced.append(request.getContent()).append("\n\n");
                }
            } else {
                // 自由模式或模板模式
                if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
                    enhanced.append("## 创作主题\n");
                    enhanced.append(request.getPrompt()).append("\n\n");
                }
            }
            
            // 添加生成要求
            enhanced.append("## 生成要求\n");
            
            if (request.getLanguage() != null) {
                enhanced.append("- 语言：").append(request.getLanguage()).append("\n");
            }
            
            if (request.getTone() != null) {
                enhanced.append("- 语气：").append(request.getTone()).append("\n");
            }
            
            if (request.getStyle() != null) {
                enhanced.append("- 风格：").append(request.getStyle()).append("\n");
            }
            
            if (request.getLength() != null) {
                enhanced.append("- 期望长度：约").append(request.getLength()).append("字\n");
            }
            
            enhanced.append("- 格式：Markdown\n");
            enhanced.append("\n");
            
            // 添加输出格式说明
            enhanced.append("## 输出格式\n");
            enhanced.append("请按照以下格式输出：\n");
            enhanced.append("1. 标题（使用 # 一级标题）\n");
            enhanced.append("2. 摘要（简短概括，100-200字）\n");
            enhanced.append("3. 正文内容（使用Markdown格式，包含适当的标题层级）\n");
            enhanced.append("4. 建议标签（3-5个，用逗号分隔）\n");
            
            String result = enhanced.toString();
            log.debug("提示词增强完成: length={}", result.length());
            
            return result;
        });
    }

    @Override
    public String buildSystemPrompt(AiGenerationRequest request) {
        StringBuilder systemPrompt = new StringBuilder();
        
        systemPrompt.append("你是一个专业的博客内容创作助手。");
        systemPrompt.append("你的任务是根据用户提供的主题和要求，生成高质量的博客文章。\n\n");
        
        systemPrompt.append("请遵循以下原则：\n");
        systemPrompt.append("1. 内容应该准确、有价值、易于理解\n");
        systemPrompt.append("2. 使用清晰的结构和逻辑\n");
        systemPrompt.append("3. 适当使用标题、列表、代码块等Markdown元素\n");
        systemPrompt.append("4. 保持专业但不失亲和力的语气\n");
        systemPrompt.append("5. 确保内容原创，避免抄袭\n");
        
        // 根据风格添加特定指导
        if (request.getStyle() != null) {
            systemPrompt.append("\n针对").append(request.getStyle()).append("风格的特殊要求：\n");
            
            switch (request.getStyle().toLowerCase()) {
                case "tutorial":
                    systemPrompt.append("- 提供清晰的步骤说明\n");
                    systemPrompt.append("- 包含实际的代码示例\n");
                    systemPrompt.append("- 说明常见问题和注意事项\n");
                    break;
                case "review":
                    systemPrompt.append("- 客观分析优缺点\n");
                    systemPrompt.append("- 提供实际使用体验\n");
                    systemPrompt.append("- 给出适用场景建议\n");
                    break;
                case "news":
                    systemPrompt.append("- 简明扼要地介绍事件\n");
                    systemPrompt.append("- 提供必要的背景信息\n");
                    systemPrompt.append("- 分析影响和意义\n");
                    break;
                case "opinion":
                    systemPrompt.append("- 明确表达核心观点\n");
                    systemPrompt.append("- 提供充分的论据支持\n");
                    systemPrompt.append("- 逻辑清晰，论证有力\n");
                    break;
            }
        }
        
        return systemPrompt.toString();
    }

    @Override
    public Mono<String> addBlogContext(String basePrompt, Long userId) {
        log.debug("添加博客上下文: userId={}", userId);
        
        // TODO: 从数据库查询用户的博客上下文信息
        // 包括：常用分类、常用标签、写作风格偏好等
        
        return Mono.fromCallable(() -> {
            StringBuilder contextPrompt = new StringBuilder(basePrompt);
            
            // 添加上下文信息
            contextPrompt.append("\n## 博客上下文\n");
            contextPrompt.append("请参考以下信息来优化生成内容：\n");
            
            // 示例：添加常用分类
            contextPrompt.append("- 常用分类：技术、教程、经验分享\n");
            
            // 示例：添加常用标签
            contextPrompt.append("- 常用标签：Java、Spring、微服务、数据库\n");
            
            // 示例：添加写作风格
            contextPrompt.append("- 写作风格：技术深度与易读性并重\n");
            
            return contextPrompt.toString();
        });
    }
}
