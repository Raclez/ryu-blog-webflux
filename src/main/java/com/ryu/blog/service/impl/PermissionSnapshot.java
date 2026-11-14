package com.ryu.blog.service.impl;

import java.util.Set;

/**
 * 一份权限快照：角色码 + 权限标识。
 *
 * <p><b>它会被序列化进 Redis</b>（{@code perm:snap:{userId}}），
 * 所以字段名与结构是<b>对外契约</b>：改了就要考虑老数据反序列化不了。
 * 当前用「读不懂就当没有、重新查库」的方式兜底（见 {@code PermissionSnapshotStore}）。
 *
 * <p><b>epoch 是「这份快照是第几版的权限」的标记</b>：权限变更时该用户的 epoch 会
 * 自增，一并写进快照。读的时候拿快照里的 epoch 和当前的 epoch 比 —— 对不上就说明
 * 这份快照是旧版权限（很可能是上一次写 Redis 失败了），必须回库重取。
 * 没有它的话，一次写失败就会让<b>别的实例</b>继续按旧权限放行，直到 Redis 里的
 * 那份自然过期。
 *
 * @param roles       角色码集合
 * @param permissions 权限标识集合
 * @param epoch       这份快照对应的权限版本号
 * @author ryu
 * @since 1.0.0
 */
public record PermissionSnapshot(Set<String> roles, Set<String> permissions, long epoch) {

    public PermissionSnapshot(Set<String> roles, Set<String> permissions) {
        this(roles, permissions, 0L);
    }

    public static PermissionSnapshot empty() {
        return new PermissionSnapshot(Set.of(), Set.of(), 0L);
    }
}
