package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 资源组表，用于将文件分组管理
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_resource_groups")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class ResourceGroup extends BaseEntity {

    /**
     * 资源组名称
     */
    private String groupName;

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
     * 排序号
     */
    private Integer sort;
} 