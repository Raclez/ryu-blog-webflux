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
 * 资源组与文件的关联表
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_resource_group_file_rel")
public class ResourceGroupFileRel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联记录唯一标识
     */
    @Id
    private Long id;

    /**
     * 资源组ID
     */
    @Column("group_id")
    private Long groupId;

    /**
     * 文件ID
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 添加文件的用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 文件在资源组中的排序位置
     */
    private Integer sort;

    /**
     * 文件在资源组中的显示名称
     */
    @Column("display_name")
    private String displayName;

    /**
     * 是否为封面文件(0-否, 1-是)
     */
    @Column("is_cover")
    private Integer isCover;

    /**
     * 关联状态(1-正常, 0-已删除)
     */
    private Integer status;

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