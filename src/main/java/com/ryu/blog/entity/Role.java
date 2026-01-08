package com.ryu.blog.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 角色实体类
 * @author ryu
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_roles")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class Role extends BaseEntity {

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色编码
     */
    private String code;

    /**
     * 是否激活：0-禁用，1-启用
     */
    private Integer isActive;

    /**
     * 是否为默认角色：0-否，1-是
     */
    private Integer isDefault;

    /**
     * 角色排序
     */
    private Integer sort;
    
    /**
     * 角色描述
     */
    private String description;
} 