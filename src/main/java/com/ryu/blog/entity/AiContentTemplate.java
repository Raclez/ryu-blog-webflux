package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * AI内容模板实体类
 * 
 * 数据库索引：
 * - idx_type: 普通索引 (type)
 * - idx_user: 普通索引 (user_id)
 * - idx_is_system: 普通索引 (is_system)
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
@Table("t_ai_content_template")
public class AiContentTemplate extends BaseEntity {

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 模板类型（tutorial, review, news, opinion等）
     */
    private String type;

    /**
     * 提示词模板（支持变量占位符，如 {{topic}}, {{language}}）
     */
    @Column("prompt_template")
    private String promptTemplate;

    /**
     * 内容结构（JSON格式）
     */
    private String structure;

    /**
     * 是否系统模板：0-否，1-是
     * 系统模板不可删除
     */
    @Column("is_system")
    private Integer isSystem;

    /**
     * 创建用户ID（自定义模板）
     * 系统模板此字段为null
     */
    @Column("user_id")
    private Long userId;

    /**
     * 使用次数（统计该模板被使用的次数）
     */
    @Column("usage_count")
    private Integer usageCount;
}
