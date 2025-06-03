package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义校验异常处理器
 * 
 * @author ryu
 */
public class ValidatorExceptionHandler {
    
    private final Validator validator;
    
    /**
     * 构造函数
     * 
     * @param validator 校验器
     */
    public ValidatorExceptionHandler(Validator validator) {
        this.validator = validator;
    }
    
    /**
     * 验证对象
     * 
     * @param <T> 对象类型
     * @param target 目标对象
     * @param objectName 对象名称
     * @return Mono对象
     */
    public <T> Mono<T> validate(T target, String objectName) {
        Errors errors = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, errors);
        
        if (errors.hasErrors()) {
            Map<String, List<String>> fieldErrors = new HashMap<>();
            
            errors.getFieldErrors().forEach(fieldError -> {
                String field = fieldError.getField();
                String defaultMessage = fieldError.getDefaultMessage();
                
                if (!fieldErrors.containsKey(field)) {
                    fieldErrors.put(field, new ArrayList<>());
                }
                fieldErrors.get(field).add(defaultMessage);
            });
            
            return Mono.error(new ValidationException("参数验证失败", fieldErrors));
        }
        
        return Mono.just(target);
    }
    
    /**
     * 验证对象，失败时抛出业务异常
     * 
     * @param <T> 对象类型
     * @param target 目标对象
     * @param objectName 对象名称
     * @return Mono对象
     */
    public <T> Mono<T> validateWithBusinessException(T target, String objectName) {
        Errors errors = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, errors);
        
        if (errors.hasErrors()) {
            String errorMessage = errors.getFieldErrors().get(0).getDefaultMessage();
            return Mono.error(new BusinessException(ErrorCodeConstants.PARAM_ERROR, errorMessage));
        }
        
        return Mono.just(target);
    }
    
    /**
     * 验证对象，并返回错误信息
     * 
     * @param <T> 对象类型
     * @param target 目标对象
     * @param objectName 对象名称
     * @return 错误信息或null
     */
    public <T> String validateAndGetErrors(T target, String objectName) {
        Errors errors = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, errors);
        
        if (errors.hasErrors()) {
            return errors.getFieldErrors().get(0).getDefaultMessage();
        }
        
        return null;
    }
    
    /**
     * 验证对象，并返回所有错误信息
     * 
     * @param <T> 对象类型
     * @param target 目标对象
     * @param objectName 对象名称
     * @return 错误信息Map
     */
    public <T> Map<String, List<String>> validateAndGetAllErrors(T target, String objectName) {
        Errors errors = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, errors);
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        if (errors.hasErrors()) {
            errors.getFieldErrors().forEach(fieldError -> {
                String field = fieldError.getField();
                String defaultMessage = fieldError.getDefaultMessage();
                
                if (!fieldErrors.containsKey(field)) {
                    fieldErrors.put(field, new ArrayList<>());
                }
                fieldErrors.get(field).add(defaultMessage);
            });
        }
        
        return fieldErrors;
    }
    
    /**
     * 检查对象是否有效
     * 
     * @param <T> 对象类型
     * @param target 目标对象
     * @param objectName 对象名称
     * @return 是否有效
     */
    public <T> boolean isValid(T target, String objectName) {
        Errors errors = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, errors);
        return !errors.hasErrors();
    }
} 