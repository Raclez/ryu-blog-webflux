package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * AI生成历史实体类
 * 
 * 数据库索引：
 * - idx_user_created: 复合索引 (user_id, create_time DESC)
 * - idx_provider: 普通索引 (provider_name)
 * - idx_is_deleted: 普通索引 (is_deleted)
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, of = {"id"})
@Table("t_ai_generation_history")
public class AiGenerationHistory extends BaseEntity {

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 原始提示词
     */
    private String prompt;

    /**
     * 增强后的提示词
     */
    @Column("enhanced_prompt")
    private String enhancedPrompt;

    /**
     * 生成结果（JSON格式）
     */
    private String result;

    /**
     * 提供商名称（openai, azure, anthropic等）
     */
    @Column("provider_name")
    private String providerName;

    /**
     * 模型名称（gpt-4, claude-3等）
     */
    @Column("model_name")
    private String modelName;

    /**
     * 使用的令牌数
     */
    @Column("token_count")
    private Integer tokenCount;

    /**
     * 成本（美元）
     */
    private Double cost;

    /**
     * 生成耗时（毫秒）
     */
    @Column("generation_time")
    private Long generationTime;
}
