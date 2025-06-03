package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 异常工具类
 * 
 * @author ryu
 */
public class ExceptionUtils {
    
    /**
     * 将异常转换为Mono
     * 
     * @param <T> 返回类型
     * @param exceptionSupplier 异常提供者
     * @return Mono对象
     */
    public static <T> Mono<T> monoError(Supplier<? extends Throwable> exceptionSupplier) {
        return Mono.error(exceptionSupplier.get());
    }
    
    /**
     * 判断条件，如果为true则抛出异常
     * 
     * @param <T> 返回类型
     * @param condition 条件
     * @param exceptionSupplier 异常提供者
     * @return Mono对象
     */
    public static <T> Mono<T> throwIf(boolean condition, Supplier<? extends Throwable> exceptionSupplier) {
        if (condition) {
            return monoError(exceptionSupplier);
        }
        return Mono.empty();
    }
    
    /**
     * 判断对象是否为null，如果为null则抛出异常
     * 
     * @param <T> 对象类型
     * @param obj 对象
     * @param exceptionSupplier 异常提供者
     * @return Mono对象
     */
    public static <T> Mono<T> throwIfNull(T obj, Supplier<? extends Throwable> exceptionSupplier) {
        if (obj == null) {
            return monoError(exceptionSupplier);
        }
        return Mono.just(obj);
    }
    
    /**
     * 处理资源不存在的情况
     * 
     * @param <T> 对象类型
     * @param obj 对象
     * @param resourceName 资源名称
     * @param fieldName 字段名称
     * @param fieldValue 字段值
     * @return Mono对象
     */
    public static <T> Mono<T> throwIfNotFound(T obj, String resourceName, String fieldName, Object fieldValue) {
        if (obj == null) {
            return monoError(() -> new ResourceNotFoundException(resourceName, fieldName, fieldValue));
        }
        return Mono.just(obj);
    }
    
    /**
     * 判断条件，如果为true则抛出业务异常
     * 
     * @param <T> 返回类型
     * @param condition 条件
     * @param code 错误码
     * @param message 错误消息
     * @return Mono对象
     */
    public static <T> Mono<T> throwBusinessIf(boolean condition, Integer code, String message) {
        if (condition) {
            return monoError(() -> new BusinessException(code, message));
        }
        return Mono.empty();
    }
    
    /**
     * 处理异常并返回默认值
     * 
     * @param <T> 返回类型
     * @param mono Mono对象
     * @param defaultValue 默认值
     * @return Mono对象
     */
    public static <T> Mono<T> onErrorReturn(Mono<T> mono, T defaultValue) {
        return mono.onErrorReturn(defaultValue);
    }
    
    /**
     * 处理异常并执行操作
     * 
     * @param <T> 返回类型
     * @param mono Mono对象
     * @param errorHandler 异常处理函数
     * @return Mono对象
     */
    public static <T> Mono<T> onErrorResume(Mono<T> mono, Function<Throwable, Mono<T>> errorHandler) {
        return mono.onErrorResume(errorHandler);
    }
    
    /**
     * 打印异常堆栈并重新抛出
     * 
     * @param <T> 返回类型
     * @param throwable 异常
     * @return 异常
     */
    public static <T extends Throwable> T printStackTrace(T throwable) {
        throwable.printStackTrace();
        return throwable;
    }
    
    /**
     * 将检查异常转换为非检查异常
     * 
     * @param e 异常
     * @return 运行时异常
     */
    public static RuntimeException wrapException(Exception e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new RuntimeException(e.getMessage(), e);
    }
    
    /**
     * 创建资源不存在异常
     * 
     * @param resourceName 资源名称
     * @param fieldName 字段名称
     * @param fieldValue 字段值
     * @return 资源不存在异常
     */
    public static ResourceNotFoundException notFound(String resourceName, String fieldName, Object fieldValue) {
        return new ResourceNotFoundException(resourceName, fieldName, fieldValue);
    }
    
    /**
     * 创建参数验证异常
     * 
     * @param message 错误消息
     * @return 参数验证异常
     */
    public static ValidationException validationError(String message) {
        return new ValidationException(message);
    }
    
    /**
     * 创建业务异常
     * 
     * @param message 错误消息
     * @return 业务异常
     */
    public static BusinessException businessError(String message) {
        return new BusinessException(message);
    }
    
    /**
     * 创建业务异常
     * 
     * @param code 错误码
     * @param message 错误消息
     * @return 业务异常
     */
    public static BusinessException businessError(Integer code, String message) {
        return new BusinessException(code, message);
    }
    
    /**
     * 创建认证异常
     * 
     * @param message 错误消息
     * @return 认证异常
     */
    public static AuthenticationException authError(String message) {
        return new AuthenticationException(message);
    }
    
    /**
     * 创建权限异常
     * 
     * @param message 错误消息
     * @return 权限异常
     */
    public static PermissionDeniedException permissionError(String message) {
        return new PermissionDeniedException(message);
    }
    
    /**
     * 断言对象不为null，否则抛出业务异常
     * 
     * @param <T> 对象类型
     * @param obj 对象
     * @param message 错误消息
     * @return 对象本身
     */
    public static <T> T assertNotNull(T obj, String message) {
        if (obj == null) {
            throw new BusinessException(ErrorCodeConstants.DATA_NOT_EXISTS, message);
        }
        return obj;
    }
    
    /**
     * 断言条件为true，否则抛出业务异常
     * 
     * @param condition 条件
     * @param message 错误消息
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCodeConstants.OPERATION_FAILED, message);
        }
    }
    
    /**
     * 断言条件为false，否则抛出业务异常
     * 
     * @param condition 条件
     * @param message 错误消息
     */
    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new BusinessException(ErrorCodeConstants.OPERATION_FAILED, message);
        }
    }
} 