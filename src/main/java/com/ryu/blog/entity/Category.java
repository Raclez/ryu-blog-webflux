package com.ryu.blog.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储文章的分类信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_categories")
public class Category implements Serializable {
    private static final long serialVersionUID = 6408463093822633520L;

    /**
     * 分类的唯一标识
     */
    @Id
    private Long id;

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
     * 分类创建的时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;
    
    /**
     * 文章数量（非数据库字段）
     */
    @Transient
    private Long articleCount;
} 