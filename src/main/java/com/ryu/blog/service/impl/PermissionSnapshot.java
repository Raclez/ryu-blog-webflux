package com.ryu.blog.service.impl;

import java.util.Set;

/**
 * 一份权限快照：角色码 + 权限标识。
 *
 * <p><b>它只存在于进程内</b>（{@code UserPermissionCache} 的 Caffeine），
 * 不会被序列化到任何外部存储 —— 所以这里的字段结构<b>不是对外契约</b>，
 * 改字段不会让存量数据反序列化不了（原先放在 Redis 里时才有这个约束）。
 *
 * @param roles       角色码集合
 * @param permissions 权限标识集合
 * @author ryu
 * @since 1.0.0
 */
public record PermissionSnapshot(Set<String> roles, Set<String> permissions) {

    public static PermissionSnapshot empty() {
        return new PermissionSnapshot(Set.of(), Set.of());
    }
}
