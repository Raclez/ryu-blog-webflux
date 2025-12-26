package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数验证异常
 * 用于参数验证失败场景，返回HTTP 400状态码
 * 支持字段级别的错误信息返回
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
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public ValidationException(String message, Throwable cause) {
        super(ErrorCodeConstants.BAD_REQUEST, message, cause);
        this.fieldErrors = null;
    }
    
    /**
     * 参数验证失败异常
     * 
     * @param message 错误消息
     * @return 验证异常实例
     */
    public static ValidationException of(String message) {
        return new ValidationException(message);
    }
    
    /**
     * 参数验证失败异常（带字段错误）
     * 
     * @param message 错误消息
     * @param fieldErrors 字段错误信息
     * @return 验证异常实例
     */
    public static ValidationException of(String message, Map<String, List<String>> fieldErrors) {
        return new ValidationException(message, fieldErrors);
    }
    
    /**
     * 单个字段验证失败异常
     * 
     * @param fieldName 字段名
     * @param errorMessage 错误消息
     * @return 验证异常实例
     */
    public static ValidationException fieldError(String fieldName, String errorMessage) {
        Map<String, List<String>> fieldErrors = new HashMap<>();
        fieldErrors.put(fieldName, List.of(errorMessage));
        return new ValidationException("参数验证失败", fieldErrors);
    }
    
    /**
     * 参数为空异常
     * 
     * @param paramName 参数名
     * @return 验证异常实例
     */
    public static ValidationException paramRequired(String paramName) {
        return new ValidationException(paramName + "不能为空");
    }
    
    /**
     * 参数格式错误异常
     * 
     * @param paramName 参数名
     * @return 验证异常实例
     */
    public static ValidationException paramInvalid(String paramName) {
        return new ValidationException(paramName + "格式不正确");
    }
} 