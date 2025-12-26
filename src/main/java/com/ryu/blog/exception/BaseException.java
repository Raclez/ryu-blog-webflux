package com.ryu.blog.exception;

import lombok.Getter;

/**
 * 基础异常类
 * 所有自定义异常的基类，提供统一的错误码和错误消息管理
 * 
 * @author ryu
 */
@Getter
public class BaseException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private final Integer code;
    
    /**
     * 错误消息
     */
    private final String message;
    
    /**
     * 错误明细，内部调试错误
     */
    private String detailMessage;
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     */
    public BaseException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param detailMessage 错误详细信息
     */
    public BaseException(Integer code, String message, String detailMessage) {
        super(message);
        this.code = code;
        this.message = message;
        this.detailMessage = detailMessage;
    }
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原始异常
     */
    public BaseException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param detailMessage 错误详细信息
     * @param cause 原始异常
     */
    public BaseException(Integer code, String message, String detailMessage, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
        this.detailMessage = detailMessage;
    }
    
    /**
     * 设置详细错误信息
     * 
     * @param detailMessage 详细错误信息
     * @return 当前异常实例
     */
    public BaseException withDetail(String detailMessage) {
        this.detailMessage = detailMessage;
        return this;
    }
} 