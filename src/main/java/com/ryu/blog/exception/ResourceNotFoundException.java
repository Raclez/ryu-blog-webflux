package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 资源不存在异常
 * 
 * @author ryu
 */
public class ResourceNotFoundException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造函数
     */
    public ResourceNotFoundException() {
        super(ErrorCodeConstants.NOT_FOUND, MessageConstants.NOT_FOUND);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public ResourceNotFoundException(String message) {
        super(ErrorCodeConstants.NOT_FOUND, message);
    }
    
    /**
     * 构造函数
     * 
     * @param resourceName 资源名称
     * @param fieldName 字段名称
     * @param fieldValue 字段值
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(ErrorCodeConstants.NOT_FOUND, String.format("未找到%s：%s为%s的记录", resourceName, fieldName, fieldValue));
    }
} 