package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 限流异常
 * 用于请求频率超限场景，返回HTTP 429状态码
 * 适用场景：API限流、防刷、频率控制等
 * 
 * @author ryu
 */
public class RateLimitException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造函数
     */
    public RateLimitException() {
        super(ErrorCodeConstants.TOO_MANY_REQUESTS, MessageConstants.TOO_MANY_REQUESTS);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public RateLimitException(String message) {
        super(ErrorCodeConstants.TOO_MANY_REQUESTS, message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public RateLimitException(String message, Throwable cause) {
        super(ErrorCodeConstants.TOO_MANY_REQUESTS, message, cause);
    }
    
    /**
     * 限流异常（带等待时间）
     * 
     * @param waitSeconds 需等待的秒数
     * @return 限流异常实例
     */
    public static RateLimitException waitFor(long waitSeconds) {
        return new RateLimitException(String.format("请求频率超限，请%d秒后重试", waitSeconds));
    }
    
    /**
     * 限流异常（通用）
     * 
     * @return 限流异常实例
     */
    public static RateLimitException tooManyRequests() {
        return new RateLimitException(MessageConstants.TOO_MANY_REQUESTS);
    }
    
    /**
     * 限流异常（自定义消息）
     * 
     * @param message 错误消息
     * @return 限流异常实例
     */
    public static RateLimitException of(String message) {
        return new RateLimitException(message);
    }
} 