package com.ryu.blog.event;

import org.springframework.context.ApplicationEvent;

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
     * 创建配置变更事件
     * 
     * @param source 事件源
     * @param configType 配置类型
     * @param configKey 配置键
     */
    public ConfigChangeEvent(Object source, String configType, String configKey) {
        super(source);
        this.configType = configType;
        this.configKey = configKey;
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
    
    @Override
    public String toString() {
        return "ConfigChangeEvent [configType=" + configType + ", configKey=" + configKey + "]";
    }
} 