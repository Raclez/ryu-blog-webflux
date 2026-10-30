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
@Table("t_resource_group_file_rels")
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
     * 创建时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     *
     * <p>表里的 {@code update_time} 是 {@code NOT NULL} 且<b>没有默认值</b>，
     * 而 R2DBC 插入时会把实体的 null 属性<b>显式写成 NULL</b>（不会走 DB 的 DEFAULT），
     * 所以这里必须映射上，否则插入直接报
     * {@code Column 'update_time' cannot be null}。
     */
    @Column("update_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime updateTime = LocalDateTime.now();

}