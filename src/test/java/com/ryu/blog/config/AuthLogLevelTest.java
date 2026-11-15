package com.ryu.blog.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 鉴权失败的日志级别要分档。
 *
 * <p><b>它守的是什么</b>：未登录是常态 —— token 过期、匿名扫描、爬虫试探，
 * 每秒都可能来几次。原先一律 {@code log.error}，于是
 * <b>ERROR 日志被正常的 401 刷爆</b>，真正需要告警的异常反而淹在里面。
 *
 * <p>分档：
 * <ul>
 *   <li>未登录（{@code NotLoginException}）→ DEBUG：常态，不值得占用告警通道；</li>
 *   <li>权限不足（{@code NotPermissionException} / {@code NotRoleException}）→ INFO：
 *       业务性的（用户点了没权限的按钮），看一眼就行；</li>
 *   <li>其它 → ERROR：说不清为什么失败的，才配得上告警。</li>
 * </ul>
 *
 * @author ryu
 */
class AuthLogLevelTest {

    @Test
    @DisplayName("未登录是常态，用 DEBUG")
    void notLoginIsDebug() {
        assertEquals(Level.DEBUG, SaTokenAuthFilter.resolveAuthLogLevel(new NotLoginException("未登录", "login", "1")));
    }

    @Test
    @DisplayName("权限不足是业务性的，用 INFO")
    void permissionDeniedIsInfo() {
        assertEquals(Level.INFO, SaTokenAuthFilter.resolveAuthLogLevel(new NotPermissionException("权限不足", "system:user:view")));
        assertEquals(Level.INFO, SaTokenAuthFilter.resolveAuthLogLevel(new NotRoleException("角色不足", "ADMIN")));
    }

    @Test
    @DisplayName("说不清为什么失败的才用 ERROR")
    void unknownFailureIsError() {
        assertEquals(Level.ERROR, SaTokenAuthFilter.resolveAuthLogLevel(new IllegalStateException("???")));
    }
}
