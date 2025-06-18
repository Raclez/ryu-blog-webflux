package com.ryu.blog.entity;

import jakarta.validation.constraints.Size;
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
     * 配置键，格式为"分组.子分组.配置名"
     */
    @Column("config_key")
    private String configKey;

    /**
     * 配置值
     */
    @Column("config_value")
    private String configValue;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 扩展信息
     */
    @Size(max = 1000, message = "扩展信息长度不能超过1000")
    private String extra;
    
    /**
     * 用户ID，0 表示全局
     */
    private Long userId;

    /**
     * 状态：true 启用, false 禁用
     */
    private Boolean status;

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