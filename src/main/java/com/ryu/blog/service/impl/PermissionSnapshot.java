package com.ryu.blog.service.impl;

import java.util.Set;

/**
 * 一份权限快照：角色码 + 权限标识。
 *
 * <p><b>它会被序列化进 Redis</b>（{@code perm:snap:{userId}}），
 * 所以字段名与结构是<b>对外契约</b>：改了就要考虑老数据反序列化不了。
 * 当前用「读不懂就当没有、重新查库」的方式兜底（见 {@code PermissionSnapshotStore}）。
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
