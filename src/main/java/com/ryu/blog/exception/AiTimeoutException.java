package com.ryu.blog.exception;

import lombok.Getter;

/**
 * AI请求超时异常
 * 
 * <p>当AI请求超时时抛出此异常。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Getter
public class AiTimeoutException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /** AI超时错误码 */
    public static final int AI_TIMEOUT = 15004;
    
    /** 超时时间（毫秒） */
    private final long timeoutMillis;
    
    /**
     * 构造函数
     * 
     * @param timeoutMillis 超时时间（毫秒）
     */
    public AiTimeoutException(long timeoutMillis) {
        super(AI_TIMEOUT, String.format("AI请求超时: %dms", timeoutMillis));
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param timeoutMillis 超时时间（毫秒）
     */
    public AiTimeoutException(String message, long timeoutMillis) {
        super(AI_TIMEOUT, message);
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param timeoutMillis 超时时间（毫秒）
     * @param cause 原始异常
     */
    public AiTimeoutException(String message, long timeoutMillis, Throwable cause) {
        super(AI_TIMEOUT, message, cause);
        this.timeoutMillis = timeoutMillis;
    }
}
