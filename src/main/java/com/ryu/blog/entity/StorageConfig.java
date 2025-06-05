package com.ryu.blog.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 存储配置实体类，用于存储不同存储策略的配置信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_storage_config")
@Slf4j
public class StorageConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置ID
     */
    @Id
    private Long id;

    /**
     * 策略名称 - 对应数据库字段 strategy_name
     */
    @Column("strategy_name")
    private String strategyName;

    /**
     * 策略键，用于标识不同的存储策略（如local, minio, oss等）
     */
    @Column("strategy_key")
    private String strategyKey;

    /**
     * 配置项，JSON格式存储 - 对应数据库字段 config
     * 在JSON序列化时被忽略，通过getConfigMap()提供反序列化后的Map
     */
    @Column("config")
    @JsonIgnore
    private String config;

    /**
     * 是否启用
     */
    @Column("is_enable")
    private Boolean isEnable;

    /**
     * 最大文件大小限制(字节)
     */
    @Column("max_file_size")
    @Schema(description = "最大文件大小限制(字节)")
    private Long maxFileSize;

    /**
     * 默认过期时间(秒)
     */
    @Column("default_expiry")
    @Schema(description = "默认过期时间(秒)")
    private Long defaultExpiry;

    /**
     * 创建者ID
     */
    @Column("creator_id")
    @Schema(description = "创建者ID")
    private Long creatorId;

    /**
     * 访问URL
     */
    @Column("access_url")
    @Schema(description = "访问URL")
    private String accessUrl;

    /**
     * 备注
     */
    @Column("description")
    private String description;

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
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;

    /**
     * 获取配置属性Map
     * 在JSON序列化时重命名为"config"
     * 
     * @return 配置属性Map
     */
    @Transient
    @JsonProperty("config")
    public Map<String, String> getConfigMap() {
        if (config == null || config.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(config, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析配置JSON失败", e);
            return new HashMap<>();
        }
    }
    
    /**
     * 设置配置属性Map
     * @param configMap 配置属性Map
     */
    public void setConfigMap(Map<String, String> configMap) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.config = mapper.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            log.error("序列化配置JSON失败", e);
        }
    }
} 