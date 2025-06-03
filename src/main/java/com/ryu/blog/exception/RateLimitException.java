package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 限流异常
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
     * @param waitSeconds 需等待的秒数
     * @return 限流异常实例
     */
    public static RateLimitException waitFor(long waitSeconds) {
        return new RateLimitException(String.format("请求频率超限，请%d秒后重试", waitSeconds));
    }
} 