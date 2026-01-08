package com.ryu.blog.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 配置变更事件
 * 用于通知系统中的组件配置已经变更
 * 
 * @author ryu 475118582@qq.com
 */
public class ConfigChangeEvent extends ApplicationEvent {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 配置类型，如 "storage", "system", "mail" 等
     */
    private final String configType;
    
    /**
     * 配置键，标识具体变更的配置项
     */
    private final String configKey;
    
    /**
     * 事件版本号，用于防止重复处理
     */
    private final Long eventVersion;
    
    /**
     * 事件时间戳
     */
    private final LocalDateTime eventTimestamp;
    
    /**
     * 创建配置变更事件（兼容旧版本构造函数）
     * 
     * @param source 事件源
     * @param configType 配置类型
     * @param configKey 配置键
     */
    public ConfigChangeEvent(Object source, String configType, String configKey) {
        this(source, configType, configKey, null, LocalDateTime.now());
    }
    
    /**
     * 创建配置变更事件（完整版本）
     * 
     * @param source 事件源
     * @param configType 配置类型
     * @param configKey 配置键
     * @param eventVersion 事件版本号
     * @param eventTimestamp 事件时间戳
     */
    public ConfigChangeEvent(Object source, String configType, String configKey, Long eventVersion, LocalDateTime eventTimestamp) {
        super(source);
        this.configType = configType;
        this.configKey = configKey;
        this.eventVersion = eventVersion;
        this.eventTimestamp = eventTimestamp;
    }
    
    /**
     * 获取配置类型
     * 
     * @return 配置类型
     */
    public String getConfigType() {
        return configType;
    }
    
    /**
     * 获取配置键
     * 
     * @return 配置键
     */
    public String getConfigKey() {
        return configKey;
    }
    
    /**
     * 获取事件版本号
     * 
     * @return 事件版本号
     */
    public Long getEventVersion() {
        return eventVersion;
    }
    
    /**
     * 获取事件时间戳
     * 
     * @return 事件时间戳
     */
    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }
    
    @Override
    public String toString() {
        return "ConfigChangeEvent [configType=" + configType + ", configKey=" + configKey + 
               ", eventVersion=" + eventVersion + ", eventTimestamp=" + eventTimestamp + "]";
    }
} 