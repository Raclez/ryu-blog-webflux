package com.ryu.blog.utils;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果类
 * @author ryu
 */
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 成功
     * @param data 数据
     * @return 结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "操作成功";
        result.data = data;
        return result;
    }

    /**
     * 成功
     * @return 结果
     */
    public static <T> Result<T> success() {
        return success(null);
    }
    
    /**
     * 成功，自定义消息，不返回数据
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> successMsg(String message) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = message;
        return result;
    }

    /**
     * 失败
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.code = 500;
        result.message = message;
        return result;
    }

    /**
     * 失败
     * @param code 状态码
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    /**
     * 失败
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.code = 500;
        result.message = message;
        return result;
    }

    /**
     * 未授权
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> unauthorized(String message) {
        Result<T> result = new Result<>();
        result.code = 401;
        result.message = message;
        return result;
    }

    /**
     * 禁止访问
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> forbidden(String message) {
        Result<T> result = new Result<>();
        result.code = 403;
        result.message = message;
        return result;
    }

    /**
     * 资源不存在
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> notFound(String message) {
        Result<T> result = new Result<>();
        result.code = 404;
        result.message = message;
        return result;
    }

    /**
     * 参数错误
     * @param message 消息
     * @return 结果
     */
    public static <T> Result<T> badRequest(String message) {
        Result<T> result = new Result<>();
        result.code = 400;
        result.message = message;
        return result;
    }
} 