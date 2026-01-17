package com.ryu.blog.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * @author ryu
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.ryu.blog.controller")
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     * 
     * @param e 业务异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(BusinessException.class)
    public Mono<Result<?>> handleBusinessException(BusinessException e, ServerWebExchange exchange) {
        log.warn("业务异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理认证异常
     * 
     * @param e 认证异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Mono<Result<?>> handleAuthenticationException(AuthenticationException e, ServerWebExchange exchange) {
        log.warn("认证异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理权限异常
     * 
     * @param e 权限异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Mono<Result<?>> handlePermissionDeniedException(PermissionDeniedException e, ServerWebExchange exchange) {
        log.warn("权限异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理资源不存在异常
     * 
     * @param e 资源不存在异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<Result<?>> handleResourceNotFoundException(ResourceNotFoundException e, ServerWebExchange exchange) {
        log.warn("资源不存在：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理参数验证异常
     * 
     * @param e 参数验证异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseEntity<Result<Map<String, List<String>>>>> handleValidationException(ValidationException e, ServerWebExchange exchange) {
        log.warn("参数验证异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        Result<Map<String, List<String>>> result = Result.error(e.getCode(), e.getMessage());
        if (e.getFieldErrors() != null) {
            result.setData(e.getFieldErrors());
        }
        return Mono.just(ResponseEntity.badRequest().body(result));
    }
    
    /**
     * 处理限流异常
     * 
     * @param e 限流异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Mono<Result<?>> handleRateLimitException(RateLimitException e, ServerWebExchange exchange) {
        log.warn("限流异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理第三方服务异常
     * 
     * @param e 第三方服务异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(ThirdPartyServiceException.class)
    public Mono<Result<?>> handleThirdPartyServiceException(ThirdPartyServiceException e, ServerWebExchange exchange) {
        log.error("第三方服务异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理AI提供商异常
     * 
     * @param e AI提供商异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(AiProviderException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Mono<Result<?>> handleAiProviderException(AiProviderException e, ServerWebExchange exchange) {
        log.error("AI提供商异常：provider={}, message={}, 路径: {}", 
                e.getProviderName(), e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理AI配置异常
     * 
     * @param e AI配置异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(AiConfigurationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleAiConfigurationException(AiConfigurationException e, ServerWebExchange exchange) {
        log.error("AI配置异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理AI生成异常
     * 
     * @param e AI生成异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(AiGenerationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleAiGenerationException(AiGenerationException e, ServerWebExchange exchange) {
        log.error("AI生成异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理AI超时异常
     * 
     * @param e AI超时异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(AiTimeoutException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    public Mono<Result<?>> handleAiTimeoutException(AiTimeoutException e, ServerWebExchange exchange) {
        log.warn("AI请求超时：timeout={}ms, 路径: {}", e.getTimeoutMillis(), exchange.getRequest().getPath());
        return Mono.just(Result.error(e.getCode(), "请求超时，请稍后重试"));
    }
    
    /**
     * 处理速率限制超出异常
     * 
     * @param e 速率限制超出异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Mono<Result<?>> handleRateLimitExceededException(RateLimitExceededException e, ServerWebExchange exchange) {
        log.warn("速率限制超出：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.TOO_MANY_REQUESTS, e.getMessage()));
    }
    
    /**
     * 处理WebExchange绑定异常（@Valid注解校验失败）
     * 
     * @param e WebExchange绑定异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseEntity<Result<Map<String, List<String>>>>> handleWebExchangeBindException(WebExchangeBindException e, ServerWebExchange exchange) {
        // 日志记录详细的验证错误
        Map<String, List<String>> fieldErrors = getFieldErrors(e.getBindingResult());
        log.warn("参数验证失败 - 路径: {}, 错误字段: {}", 
                exchange.getRequest().getPath(), 
                fieldErrors.keySet());
        log.debug("参数验证详细错误: {}", fieldErrors);
        
        // 返回给前端的验证错误信息（包含字段级别的错误）
        String message = "参数验证失败";
        Result<Map<String, List<String>>> result = Result.error(ErrorCodeConstants.BAD_REQUEST, message);
        result.setData(fieldErrors);
        return Mono.just(ResponseEntity.badRequest().body(result));
    }
    
    /**
     * 处理约束违反异常（@Validated注解在方法参数上的校验失败）
     * 
     * @param e 约束违反异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseEntity<Result<Map<String, List<String>>>>> handleConstraintViolationException(
            jakarta.validation.ConstraintViolationException e, ServerWebExchange exchange) {
        
        // 收集字段错误信息
        Map<String, List<String>> fieldErrors = new HashMap<>();
        e.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            
            if (!fieldErrors.containsKey(fieldName)) {
                fieldErrors.put(fieldName, new ArrayList<>());
            }
            fieldErrors.get(fieldName).add(message);
        });
        
        // 日志记录详细的约束违反信息
        log.warn("参数约束违反 - 路径: {}, 错误字段: {}", 
                exchange.getRequest().getPath(), 
                fieldErrors.keySet());
        log.debug("参数约束详细错误: {}", fieldErrors);
        
        // 返回给前端的验证错误信息
        Result<Map<String, List<String>>> result = Result.error(ErrorCodeConstants.BAD_REQUEST, "参数验证失败");
        result.setData(fieldErrors);
        return Mono.just(ResponseEntity.badRequest().body(result));
    }
    
    /**
     * 处理服务器Web输入异常
     * 
     * @param e 服务器Web输入异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(ServerWebInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Result<?>> handleServerWebInputException(ServerWebInputException e, ServerWebExchange exchange) {
        // 日志记录详细信息
        Throwable cause = e.getCause();
        if (cause != null) {
            log.warn("参数错误异常 - 路径: {}, 原因: {}, 详细信息: {}", 
                    exchange.getRequest().getPath(), 
                    e.getReason(),
                    cause.getMessage(), 
                    e);
        } else {
            log.warn("参数错误异常 - 路径: {}, 原因: {}", 
                    exchange.getRequest().getPath(), 
                    e.getReason(), 
                    e);
        }
        
        // 返回给前端的简化信息
        String userMessage = "请求参数格式错误，请检查后重试";
        return Mono.just(Result.error(ErrorCodeConstants.BAD_REQUEST, userMessage));
    }
    
    /**
     * 处理JSON解析异常
     * 
     * @param e JSON解析异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler({
        com.fasterxml.jackson.core.JsonProcessingException.class,
        com.fasterxml.jackson.databind.JsonMappingException.class,
        com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
        com.fasterxml.jackson.databind.exc.MismatchedInputException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Result<?>> handleJsonException(Exception e, ServerWebExchange exchange) {
        String userMessage = "请求数据格式错误";
        String logMessage = "JSON解析异常";
        
        // 日志记录详细信息
        if (e instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException) {
            com.fasterxml.jackson.databind.exc.InvalidFormatException ife = 
                (com.fasterxml.jackson.databind.exc.InvalidFormatException) e;
            String fieldName = ife.getPath().stream()
                .map(ref -> ref.getFieldName())
                .reduce((a, b) -> a + "." + b)
                .orElse("unknown");
            logMessage = String.format("字段 '%s' 的值格式不正确: %s, 目标类型: %s", 
                    fieldName, ife.getValue(), ife.getTargetType().getSimpleName());
            userMessage = String.format("字段 '%s' 的格式不正确", fieldName);
        } else if (e instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException) {
            com.fasterxml.jackson.databind.exc.MismatchedInputException mie = 
                (com.fasterxml.jackson.databind.exc.MismatchedInputException) e;
            String fieldName = mie.getPath().stream()
                .map(ref -> ref.getFieldName())
                .reduce((a, b) -> a + "." + b)
                .orElse("unknown");
            logMessage = String.format("字段 '%s' 的类型不匹配, 目标类型: %s", 
                    fieldName, mie.getTargetType() != null ? mie.getTargetType().getSimpleName() : "unknown");
            userMessage = String.format("字段 '%s' 的类型不正确", fieldName);
        } else {
            logMessage = "JSON解析失败: " + e.getMessage();
        }
        
        log.warn("JSON解析异常 - 路径: {}, 详细信息: {}", exchange.getRequest().getPath(), logMessage, e);
        return Mono.just(Result.error(ErrorCodeConstants.BAD_REQUEST, userMessage));
    }
    
    /**
     * 处理请求方法不允许异常
     * 
     * @param e 方法不允许异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(MethodNotAllowedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Mono<Result<?>> handleMethodNotAllowedException(MethodNotAllowedException e, ServerWebExchange exchange) {
        String message = "不支持的请求方法";
        log.warn("请求方法不支持异常: {}, 路径: {}", message, exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.METHOD_NOT_ALLOWED, message));
    }
    
    /**
     * 处理WebClient响应异常
     * 
     * @param e WebClient响应异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(WebClientResponseException.class)
    public Mono<Result<?>> handleWebClientResponseException(WebClientResponseException e, ServerWebExchange exchange) {
        String message = "调用外部服务失败：" + e.getStatusCode() + " " + e.getStatusText();
        log.error("WebClient调用异常: {}, 路径: {}", message, exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.THIRD_SERVICE_ERROR, message));
    }
    
    /**
     * 处理Sa-Token未登录异常
     * 
     * @param e Sa-Token未登录异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Mono<Result<?>> handleNotLoginException(NotLoginException e, ServerWebExchange exchange) {
        log.warn("未登录异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.UNAUTHORIZED, MessageConstants.UNAUTHORIZED));
    }
    
    /**
     * 处理Sa-Token角色异常
     * 
     * @param e Sa-Token角色异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Mono<Result<?>> handleNotRoleException(NotRoleException e, ServerWebExchange exchange) {
        log.warn("角色权限不足异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.FORBIDDEN, "角色权限不足"));
    }
    
    /**
     * 处理Sa-Token权限异常
     * 
     * @param e Sa-Token权限异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Mono<Result<?>> handleNotPermissionException(NotPermissionException e, ServerWebExchange exchange) {
        log.warn("权限不足异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.FORBIDDEN, "权限不足"));
    }
    
    /**
     * 处理数据库完整性约束异常
     * 
     * @param e 数据库完整性约束异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Result<?>> handleDataIntegrityViolationException(DataIntegrityViolationException e, ServerWebExchange exchange) {
        log.error("数据库操作异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.DATA_ALREADY_EXISTS, "数据操作失败，可能存在重复数据或违反约束"));
    }
    
    /**
     * 处理SQL语法错误异常
     * 
     * @param e SQL语法错误异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleBadSqlGrammarException(BadSqlGrammarException e, ServerWebExchange exchange) {
        log.error("SQL语法错误: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, "数据库查询错误，请联系管理员"));
    }
    
    /**
     * 处理数据库资源访问失败异常
     * 
     * @param e 数据库资源访问失败异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleDataAccessResourceFailureException(DataAccessResourceFailureException e, ServerWebExchange exchange) {
        log.error("数据库资源访问失败: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, "数据库访问失败，请稍后重试"));
    }
    
    /**
     * 处理类型转换异常
     * 
     * @param e 类型转换异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleClassCastException(ClassCastException e, ServerWebExchange exchange) {
        log.error("类型转换异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, "系统处理数据时发生错误，请稍后再试"));
    }
    
    /**
     * 处理Redis操作异常
     * 
     * @param e Redis操作异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(RedisSystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleRedisSystemException(RedisSystemException e, ServerWebExchange exchange) {
        log.error("Redis操作异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, "系统缓存处理发生异常，请稍后再试"));
    }
    
    /**
     * 处理空指针异常
     * 
     * @param e 空指针异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleNullPointerException(NullPointerException e, ServerWebExchange exchange) {
        log.error("空指针异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, "系统处理数据时遇到问题，请稍后再试"));
    }
    
    /**
     * 处理非法参数异常
     * 
     * @param e 非法参数异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Result<?>> handleIllegalArgumentException(IllegalArgumentException e, ServerWebExchange exchange) {
        log.error("非法参数异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.BAD_REQUEST, "请求参数有误，请检查后重试"));
    }
    
    /**
     * 处理未预期的异常
     * 
     * @param e 异常
     * @param exchange 请求交换对象
     * @return 响应结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Result<?>> handleException(Exception e, ServerWebExchange exchange) {
        log.error("系统异常: {}, 类型: {}, 路径: {}", e.getMessage(), e.getClass().getName(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, MessageConstants.ERROR));
    }
    
    /**
     * 处理资源未找到异常
     * @param e 资源未找到异常
     * @return 错误结果
     */
    @ExceptionHandler(org.springframework.web.reactive.resource.NoResourceFoundException.class)
    public Mono<Result<?>> handleNoResourceFoundException(org.springframework.web.reactive.resource.NoResourceFoundException e) {
        // 对于favicon.ico的请求，不记录错误日志
        if (e.getMessage() != null && e.getMessage().contains("favicon.ico")) {
            return Mono.just(Result.error(HttpStatus.NOT_FOUND.value(), "资源不存在"));
        }
        
        log.error("资源未找到: {}", e.getMessage());
        return Mono.just(Result.error(HttpStatus.NOT_FOUND.value(), "资源不存在"));
    }
    
    /**
     * 从BindingResult中获取字段错误信息
     * 
     * @param bindingResult 绑定结果
     * @return 字段错误信息
     */
    private Map<String, List<String>> getFieldErrors(BindingResult bindingResult) {
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        bindingResult.getFieldErrors().forEach(fieldError -> {
            String field = fieldError.getField();
            String defaultMessage = fieldError.getDefaultMessage();
            
            if (!fieldErrors.containsKey(field)) {
                fieldErrors.put(field, new ArrayList<>());
            }
            fieldErrors.get(field).add(defaultMessage);
        });
        
        return fieldErrors;
    }
} 