package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 资源不存在异常
 * 用于资源查找失败场景，返回HTTP 404状态码
 * 适用场景：文件下载、静态资源访问等需要返回404的场景
 * 注意：普通业务查询（如查询用户）应使用 BusinessException.xxxNotFound()
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
     * @param message 错误消息
     * @param cause 原始异常
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCodeConstants.NOT_FOUND, message, cause);
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
    
    /**
     * 文件不存在异常
     * 
     * @param filename 文件名
     * @return 资源不存在异常实例
     */
    public static ResourceNotFoundException fileNotFound(String filename) {
        return new ResourceNotFoundException("文件不存在：" + filename);
    }
    
    /**
     * 资源不存在异常（通用）
     * 
     * @param resourceType 资源类型
     * @param identifier 资源标识
     * @return 资源不存在异常实例
     */
    public static ResourceNotFoundException notFound(String resourceType, Object identifier) {
        return new ResourceNotFoundException(String.format("%s不存在：%s", resourceType, identifier));
    }
} 