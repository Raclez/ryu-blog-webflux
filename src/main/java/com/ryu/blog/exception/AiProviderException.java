package com.ryu.blog.exception;

import lombok.Getter;

/**
 * AI提供商异常
 * 
 * <p>当AI提供商（OpenAI、Anthropic等）返回错误时抛出此异常。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Getter
public class AiProviderException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /** AI提供商错误码 */
    public static final int AI_PROVIDER_ERROR = 15001;
    
    /** 提供商名称 */
    private final String providerName;
    
    /**
     * 构造函数
     * 
     * @param providerName 提供商名称
     * @param message 错误消息
     */
    public AiProviderException(String providerName, String message) {
        super(AI_PROVIDER_ERROR, String.format("AI提供商 [%s] 错误: %s", providerName, message));
        this.providerName = providerName;
    }
    
    /**
     * 构造函数
     * 
     * @param providerName 提供商名称
     * @param message 错误消息
     * @param cause 原始异常
     */
    public AiProviderException(String providerName, String message, Throwable cause) {
        super(AI_PROVIDER_ERROR, String.format("AI提供商 [%s] 错误: %s", providerName, message), cause);
        this.providerName = providerName;
    }
    
    /**
     * 提供商不可用
     */
    public static AiProviderException providerUnavailable(String providerName) {
        return new AiProviderException(providerName, "服务不可用");
    }
    
    /**
     * 提供商限流
     */
    public static AiProviderException providerRateLimited(String providerName) {
        return new AiProviderException(providerName, "请求过于频繁，请稍后重试");
    }
    
    /**
     * 提供商认证失败
     */
    public static AiProviderException providerAuthFailed(String providerName) {
        return new AiProviderException(providerName, "认证失败，请检查API密钥配置");
    }
}
