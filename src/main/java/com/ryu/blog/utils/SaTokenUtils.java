package com.ryu.blog.utils;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.PermissionConstants;
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
public final class SaTokenUtils {

    private SaTokenUtils() {
    }

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

    /**
     * 取当前登录用户 ID，未登录 / 上下文不可用 / 取值出错时返回 {@code null}
     *
     * <p><b>用途：参与缓存 key 的计算。</b>
     *
     * <p>可见性依赖于「当前是谁」的数据（例如文章详情：未公开的文章只允许作者本人读）。
     * 如果缓存 key 里不含用户身份，命中缓存就会<b>跳过方法体里的可见性校验</b>，
     * 结果是作者缓存下来的私有内容被匿名访客直接读走。
     *
     * <p>必须在<b>组装响应式链时</b>（而不是订阅之后）取值：Sa-Token 的上下文
     * 靠 {@link SaReactorSyncHolder} 随响应式链传播，进了 {@code flatMap} 之后
     * 可能已经取不到了。取不到就当匿名处理 —— 宁可多查一次库，也不能把私有内容放出去。
     *
     * @return 当前登录用户 ID，匿名或取不到时为 {@code null}
     */
    public static Long currentUserIdOrNull() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            log.debug("获取当前登录用户失败，按未登录处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 当前操作者是否拥有「管理他人资源」的能力（{@code system:user:manage}）
     *
     * <p><b>用途：参与缓存 key 的计算。</b>
     *
     * <p>文章可见性有两档：作者本人、以及「能管别人」的管理者。
     * 只把用户 ID 放进缓存 key 是不够的 —— 管理员和「恰好同 ID 的普通用户」
     * 会被算成同一个 key，管理员读到的草稿详情就可能被缓存下来发给普通用户。
     * 所以凡是可见性依赖这个标记的缓存，key 里必须<b>同时</b>带上它。
     *
     * <p>与 {@link #currentUserIdOrNull()} 一样，必须在<b>组装响应式链时</b>取值；
     * 取不到就按「不是管理员」处理 —— 宁可多查一次库，也不能把私有内容放出去。
     *
     * @return 拥有管理他人资源的能力返回 true
     */
    public static boolean currentCanManageOthers() {
        try {
            if (!StpUtil.isLogin()) {
                return false;
            }
            return StpUtil.hasPermission(PermissionConstants.USER_MANAGE);
        } catch (Exception e) {
            log.debug("获取当前操作者能力失败，按普通用户处理: {}", e.getMessage());
            return false;
        }
    }
} 