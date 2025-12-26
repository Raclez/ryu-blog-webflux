package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 认证异常
 * 用于认证失败场景，返回HTTP 401状态码
 * 适用场景：JWT验证失败、Session过期、未登录访问受保护资源等
 * 
 * @author ryu
 */
public class AuthenticationException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造函数
     */
    public AuthenticationException() {
        super(ErrorCodeConstants.UNAUTHORIZED, MessageConstants.UNAUTHORIZED);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public AuthenticationException(String message) {
        super(ErrorCodeConstants.UNAUTHORIZED, message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public AuthenticationException(String message, Throwable cause) {
        super(ErrorCodeConstants.UNAUTHORIZED, message, cause);
    }
    
    /**
     * 令牌过期异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException("登录已过期，请重新登录");
    }
    
    /**
     * 无效令牌异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException invalidToken() {
        return new AuthenticationException("无效的认证令牌");
    }
    
    /**
     * 令牌缺失异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException missingToken() {
        return new AuthenticationException("缺少认证令牌");
    }
    
    /**
     * 未登录异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException notLogin() {
        return new AuthenticationException(MessageConstants.UNAUTHORIZED);
    }
    
    /**
     * 认证失败异常（通用）
     * 
     * @param message 错误消息
     * @return 认证异常实例
     */
    public static AuthenticationException authFailed(String message) {
        return new AuthenticationException(message);
    }
} 