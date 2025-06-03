package com.ryu.blog.exception;

import lombok.Getter;

/**
 * 基础异常类
 * 
 * @author ryu
 */
@Getter
public class BaseException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private Integer code;
    
    /**
     * 错误消息
     */
    private String message;
    
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
        super(cause);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public BaseException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public BaseException(String message) {
        super(message);
        this.message = message;
    }
    
    /**
     * 构造函数
     * 
     * @param cause 原始异常
     */
    public BaseException(Throwable cause) {
        super(cause);
    }
} 