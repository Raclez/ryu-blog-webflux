package com.ryu.blog.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 存储文章的分类信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_categories")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class Category extends BaseEntity {

    /**
     * 分类名称，必须唯一
     */
    private String name;

    /**
     * 分类的描述信息
     */
    private String description;
    
    /**
     * 排序字段
     */
    private Integer sort;
    
    /**
     * 文章数量（非数据库字段）
     */
    @Transient
    private Long articleCount;
} 