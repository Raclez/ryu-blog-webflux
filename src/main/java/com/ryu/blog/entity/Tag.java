package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 存储文章的标签信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_tags")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class Tag extends BaseEntity {

    /**
     * 标签名称，必须唯一
     */
    private String name;
    
    /**
     * 标签别名，用于 SEO
     */
    private String slug;

    /**
     * 标签的描述信息
     */
    private String description;
} 