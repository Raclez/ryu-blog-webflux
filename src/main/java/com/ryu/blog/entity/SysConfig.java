package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_sys_config")
public class SysConfig {

    /**
     * 配置ID
     */
    @Id
    private Long id;

    /**
     * 配置键
     */
    @Column("config_key")
    private String configKey;

    /**
     * 配置值
     */
    @Column("config_value")
    private String configValue;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 配置分组
     */
    @Column("config_group")
    private String configGroup;

    /**
     * 是否系统内置：0-否，1-是
     */
    @Column("is_system")
    private Integer isSystem;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;
} 