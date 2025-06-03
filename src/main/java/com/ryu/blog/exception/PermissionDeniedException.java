package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 权限拒绝异常
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
     * @param permission 缺失的权限标识
     * @return 权限拒绝异常
     */
    public static PermissionDeniedException missingPermission(String permission) {
        return new PermissionDeniedException("缺少权限：" + permission);
    }
} 