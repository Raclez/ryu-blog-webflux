package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 权限拒绝异常
 * 用于权限不足场景，返回HTTP 403状态码
 * 适用场景：角色不匹配、缺少特定权限、访问被拒绝等
 * 
 * @author ryu
 */
public class PermissionDeniedException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造函数
     */
    public PermissionDeniedException() {
        super(ErrorCodeConstants.FORBIDDEN, MessageConstants.FORBIDDEN);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public PermissionDeniedException(String message) {
        super(ErrorCodeConstants.FORBIDDEN, message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public PermissionDeniedException(String message, Throwable cause) {
        super(ErrorCodeConstants.FORBIDDEN, message, cause);
    }
    
    /**
     * 缺少权限异常
     * 
     * @param permission 缺失的权限标识
     * @return 权限拒绝异常
     */
    public static PermissionDeniedException missingPermission(String permission) {
        return new PermissionDeniedException("缺少权限：" + permission);
    }
    
    /**
     * 缺少角色异常
     * 
     * @param role 缺失的角色标识
     * @return 权限拒绝异常
     */
    public static PermissionDeniedException missingRole(String role) {
        return new PermissionDeniedException("缺少角色：" + role);
    }
    
    /**
     * 访问被拒绝异常
     * 
     * @return 权限拒绝异常
     */
    public static PermissionDeniedException accessDenied() {
        return new PermissionDeniedException(MessageConstants.FORBIDDEN);
    }
    
    /**
     * 访问被拒绝异常（带详细信息）
     * 
     * @param message 错误消息
     * @return 权限拒绝异常
     */
    public static PermissionDeniedException accessDenied(String message) {
        return new PermissionDeniedException(message);
    }
} 