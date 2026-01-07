package com.ryu.blog.exception;

/**
 * AI配置异常
 * 
 * <p>当AI配置错误或缺失时抛出此异常。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public class AiConfigurationException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /** AI配置错误码 */
    public static final int AI_CONFIG_ERROR = 15002;
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public AiConfigurationException(String message) {
        super(AI_CONFIG_ERROR, "AI配置错误: " + message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public AiConfigurationException(String message, Throwable cause) {
        super(AI_CONFIG_ERROR, "AI配置错误: " + message, cause);
    }
    
    /**
     * 配置缺失
     */
    public static AiConfigurationException configMissing(String configKey) {
        return new AiConfigurationException(String.format("缺少必要配置: %s", configKey));
    }
    
    /**
     * 配置无效
     */
    public static AiConfigurationException configInvalid(String configKey, String reason) {
        return new AiConfigurationException(String.format("配置 [%s] 无效: %s", configKey, reason));
    }
    
    /**
     * 提供商未配置
     */
    public static AiConfigurationException providerNotConfigured(String providerName) {
        return new AiConfigurationException(String.format("AI提供商 [%s] 未配置", providerName));
    }
}
