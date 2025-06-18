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
    public Mono<Result<Void>> handleBusinessException(BusinessException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleAuthenticationException(AuthenticationException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handlePermissionDeniedException(PermissionDeniedException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleResourceNotFoundException(ResourceNotFoundException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleRateLimitException(RateLimitException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleThirdPartyServiceException(ThirdPartyServiceException e, ServerWebExchange exchange) {
        log.error("第三方服务异常：{}, 路径: {}", e.getMessage(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(e.getCode(), e.getMessage()));
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
        log.warn("参数验证异常: {}, 路径: {}", e.getMessage(), exchange.getRequest().getPath());
        Map<String, List<String>> fieldErrors = getFieldErrors(e.getBindingResult());
        String message = "参数验证失败";
        Result<Map<String, List<String>>> result = Result.error(ErrorCodeConstants.BAD_REQUEST, message);
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
    public Mono<Result<Void>> handleServerWebInputException(ServerWebInputException e, ServerWebExchange exchange) {
        String message = "参数错误: " + e.getReason();
        log.warn("参数错误异常: {}, 路径: {}", message, exchange.getRequest().getPath());
        return Mono.just(Result.error(ErrorCodeConstants.BAD_REQUEST, message));
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
    public Mono<Result<Void>> handleMethodNotAllowedException(MethodNotAllowedException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleWebClientResponseException(WebClientResponseException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleNotLoginException(NotLoginException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleNotRoleException(NotRoleException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleNotPermissionException(NotPermissionException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleBadSqlGrammarException(BadSqlGrammarException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleDataAccessResourceFailureException(DataAccessResourceFailureException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleClassCastException(ClassCastException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleRedisSystemException(RedisSystemException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleNullPointerException(NullPointerException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e, ServerWebExchange exchange) {
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
    public Mono<Result<Void>> handleException(Exception e, ServerWebExchange exchange) {
        log.error("系统异常: {}, 类型: {}, 路径: {}", e.getMessage(), e.getClass().getName(), exchange.getRequest().getPath(), e);
        return Mono.just(Result.error(ErrorCodeConstants.ERROR, MessageConstants.ERROR));
    }
    
    /**
     * 处理资源未找到异常
     * @param e 资源未找到异常
     * @return 错误结果
     */
    @ExceptionHandler(org.springframework.web.reactive.resource.NoResourceFoundException.class)
    public Mono<Result<String>> handleNoResourceFoundException(org.springframework.web.reactive.resource.NoResourceFoundException e) {
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