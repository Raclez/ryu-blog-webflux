package com.ryu.blog.utils;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Sa-Token工具类 - 响应式环境下的辅助工具
 * 封装在响应式环境中使用Sa-Token的通用操作
 * @author ryu
 */
@Slf4j
public class SaTokenUtils {
    
    /**
     * 在响应式环境中执行Sa-Token操作
     * @param exchange ServerWebExchange实例
     * @param action 要执行的操作
     * @param <T> 返回类型
     * @return 包含结果的Mono
     */
    public static <T> Mono<T> exec(ServerWebExchange exchange, Supplier<T> action) {
        return Mono.fromCallable(() -> {
            try {
                // 设置上下文
                SaReactorSyncHolder.setContext(exchange);
                // 执行操作
                return action.get();
            } finally {
                // 清理上下文
                SaReactorSyncHolder.clearContext();
            }
        });
    }
    
    /**
     * 执行登录操作
     * @param exchange ServerWebExchange实例
     * @param loginId 登录ID
     * @return 包含Token值的Mono
     */
    public static Mono<String> login(ServerWebExchange exchange, Object loginId) {
        return exec(exchange, () -> {
            StpUtil.login(loginId);
            return StpUtil.getTokenValue();
        });
    }
    
    /**
     * 获取当前Token信息
     * @param exchange ServerWebExchange实例
     * @return 包含Token值的Mono
     */
    public static Mono<String> getTokenValue(ServerWebExchange exchange) {
        return exec(exchange, StpUtil::getTokenValue);
    }
    
    /**
     * 获取Token过期时间
     * @param exchange ServerWebExchange实例
     * @return 包含Token过期时间的Mono
     */
    public static Mono<Long> getTokenTimeout(ServerWebExchange exchange) {
        return exec(exchange, StpUtil::getTokenTimeout);
    }
    
    /**
     * 检查登录状态
     * @param exchange ServerWebExchange实例
     * @return 是否已登录
     */
    public static Mono<Boolean> isLogin(ServerWebExchange exchange) {
        return exec(exchange, StpUtil::isLogin);
    }
    
    /**
     * 获取当前登录ID
     * @param exchange ServerWebExchange实例
     * @return 包含当前登录ID的Mono
     */
    public static Mono<Object> getLoginId(ServerWebExchange exchange) {
        return exec(exchange, StpUtil::getLoginId);
    }
    
    /**
     * 执行登出操作
     * @param exchange ServerWebExchange实例
     * @return 包含登出结果的Mono
     */
    public static Mono<Void> logout(ServerWebExchange exchange) {
        return exec(exchange, () -> {
            StpUtil.logout();
            return null;
        });
    }
    
    /**
     * 检查是否有指定角色
     * @param exchange ServerWebExchange实例
     * @param role 角色名
     * @return 是否拥有该角色
     */
    public static Mono<Boolean> hasRole(ServerWebExchange exchange, String role) {
        return exec(exchange, () -> StpUtil.hasRole(role));
    }
    
    /**
     * 检查是否有指定权限
     * @param exchange ServerWebExchange实例
     * @param permission 权限标识
     * @return 是否拥有该权限
     */
    public static Mono<Boolean> hasPermission(ServerWebExchange exchange, String permission) {
        return exec(exchange, () -> StpUtil.hasPermission(permission));
    }
} 