package com.ryu.blog.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 * 
 * 数据库索引：
 * - uk_config_key: 联合唯一索引 (config_key, user_id)
 * - idx_config_key: 普通索引 (config_key)
 * - idx_user_id: 普通索引 (user_id)
 * - idx_user_key_deleted: 复合索引 (user_id, config_key, is_deleted)
 * - idx_key_deleted: 复合索引 (config_key, is_deleted)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
@Table("t_sys_config")
@Schema(description = "系统配置")
public class SysConfig {

    /**
     * 配置ID
     */
    @Id
    @Schema(description = "配置ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 配置键，格式为"分组.子分组.配置名"
     * 数据库应有联合唯一索引：uk_config_key (config_key, user_id)
     * 数据库应有复合索引：idx_key_deleted (config_key, is_deleted)
     */
    @Column("config_key")
    @Size(max = 200, message = "配置键长度不能超过200")
    @Schema(description = "配置键，格式为'分组.子分组.配置名'")
    private String configKey;

    /**
     * 配置值
     */
    @Column("config_value")
    @Size(max = 2000, message = "配置值长度不能超过2000")
    @Schema(description = "配置值")
    private String configValue;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    @Schema(description = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @Column("create_time")
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 扩展信息（JSON格式）
     */
    @Size(max = 1000, message = "扩展信息长度不能超过1000")
    @Schema(description = "扩展信息")
    private String extra;
    
    /**
     * 用户ID，0 表示全局配置
     * 数据库应有索引：idx_user_id
     * 数据库应有复合索引：idx_user_key_deleted (user_id, config_key, is_deleted)
     */
    @Column("user_id")
    @Schema(description = "用户ID，0表示全局配置")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 更新时间
     */
    @Column("update_time")
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 是否删除：0-未删除，1-已删除
     * 不暴露给前端
     */
    @Column("is_deleted")
    @JsonIgnore
    private Integer isDeleted;
} 