package com.ryu.blog.config;

import cn.dev33.satoken.stp.StpInterface;
import com.ryu.blog.service.impl.PermissionSnapshot;
import com.ryu.blog.service.impl.UserPermissionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 的权限 / 角色数据源
 *
 * <p>这个 Bean <b>此前从未存在</b> —— 全项目 0 处 {@code StpInterface} 实现。
 * 没有它，Sa-Token 的默认实现返回空集合，于是：
 * <ul>
 *   <li>{@code StpUtil.checkRole("admin")} 对<b>所有人</b>抛 {@code NotRoleException}；</li>
 *   <li>{@code StpUtil.hasPermission(...)} <b>永远返回 false</b>；</li>
 *   <li>{@code utils/SaTokenUtils} 的 {@code hasRole}/{@code hasPermission} 形同虚设。</li>
 * </ul>
 *
 * <p><b>为什么读的是内存快照而不是数据库</b>：Sa-Token 的 {@code StpInterface}
 * 是同步接口，而本项目是全响应式栈。在这里查库意味着要在事件循环上阻塞
 * （或 {@code block()}，在 R2DBC 上有死锁风险）。
 * 所以：数据库查询由 {@code UserPermissionWarmupFilter} 在鉴权前<b>反应式</b>完成，
 * 这里只读内存快照，零阻塞。
 *
 * <p>快照未就绪时返回空集合 —— <b>fail-closed</b>：宁可拒绝一次请求，
 * 也不能因为缓存没命中就放行。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenStpInterface implements StpInterface {

    private final UserPermissionCache permissionCache;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = toUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        PermissionSnapshot snapshot = permissionCache.getSnapshot(userId);
        if (snapshot == null) {
            log.debug("用户权限快照未就绪，按无权限处理: userId={}", userId);
            return List.of();
        }
        return new ArrayList<>(snapshot.permissions());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = toUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        PermissionSnapshot snapshot = permissionCache.getSnapshot(userId);
        if (snapshot == null) {
            return List.of();
        }
        return new ArrayList<>(snapshot.roles());
    }

    /**
     * 把 Sa-Token 传进来的 loginId 转成用户ID
     *
     * @param loginId Sa-Token 传入的登录ID（可能是 Long 或 String）
     * @return 用户ID；无法解析时返回 null
     */
    private Long toUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(loginId.toString());
        } catch (NumberFormatException e) {
            log.warn("无法解析的 loginId: {}", loginId);
            return null;
        }
    }
}
