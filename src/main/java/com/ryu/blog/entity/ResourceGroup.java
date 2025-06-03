package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源组表，用于将文件分组管理
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_resource_groups")
public class ResourceGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 资源组唯一标识
     */
    @Id
    private Long id;

    /**
     * 资源组名称
     */
    private String name;

    /**
     * 资源组描述
     */
    private String description;

    /**
     * 创建者用户ID
     */
    @Column("creator_id")
    private Long creatorId;

    /**
     * 父资源组ID，顶级资源组为NULL
     */
    @Column("parent_id")
    private Long parentId;

    /**
     * 资源组类型（如：project, gallery, folder等）
     */
    private String type;

    /**
     * 资源组路径，类似于文件系统路径
     */
    private String path;

    /**
     * 资源组状态（1-正常，0-已删除）
     */
    private Integer status;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 创建时间
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
} 