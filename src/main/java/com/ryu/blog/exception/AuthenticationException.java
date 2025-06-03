package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 认证异常
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
     * 用户名或密码错误异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("用户名或密码错误");
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
     * 账号被锁定异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException accountLocked() {
        return new AuthenticationException(MessageConstants.USER_ACCOUNT_LOCKED);
    }
    
    /**
     * 账号被禁用异常
     * 
     * @return 认证异常实例
     */
    public static AuthenticationException accountDisabled() {
        return new AuthenticationException(MessageConstants.USER_ACCOUNT_DISABLED);
    }
} 