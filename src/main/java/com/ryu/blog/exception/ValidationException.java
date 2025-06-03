package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 参数验证异常
 * 
 * @author ryu
 */
@Getter
public class ValidationException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误字段信息
     */
    private final Map<String, List<String>> fieldErrors;
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public ValidationException(String message) {
        super(ErrorCodeConstants.BAD_REQUEST, message);
        this.fieldErrors = null;
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param fieldErrors 字段错误信息
     */
    public ValidationException(String message, Map<String, List<String>> fieldErrors) {
        super(ErrorCodeConstants.BAD_REQUEST, message);
        this.fieldErrors = fieldErrors;
    }
} 