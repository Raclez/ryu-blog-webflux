package com.ryu.blog.service.impl;

import com.ryu.blog.entity.Permissions;
import com.ryu.blog.entity.Role;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户权限解析器 —— 权限判定的<b>唯一权威实现</b>
 *
 * <p>整改前，同一个「用户有哪些权限」的问题有四条读路径，答案各不相同：
 * <table border="1">
 *   <caption>整改前的口径分歧</caption>
 *   <tr><th>实现</th><th>permission.is_active</th><th>role.is_active</th><th>is_deleted</th></tr>
 *   <tr><td>{@code PermissionsServiceImpl.getUserPermissionIdentities}</td><td>过滤</td><td><b>不过滤</b></td><td>只过滤 permission</td></tr>
 *   <tr><td>{@code PermissionsServiceImpl.hasPermission}</td><td>过滤</td><td><b>不过滤</b></td><td>只过滤 permission</td></tr>
 *   <tr><td>{@code MenusServiceImpl.getUserMenus}</td><td><b>不过滤</b></td><td><b>不过滤</b></td><td><b>不过滤</b></td></tr>
 *   <tr><td>{@code RoleRepository.findByUserId}</td><td>—</td><td><b>不过滤</b></td><td>只过滤 role</td></tr>
 * </table>
 *
 * <p>现在全部收敛到这里。过滤规则统一为：
 * <ul>
 *   <li>角色必须 {@code is_active = 1} 且 {@code is_deleted = 0}；</li>
 *   <li>权限必须 {@code is_active = 1} 且 {@code is_deleted = 0}；</li>
 *   <li>授权关系必须 {@code is_deleted = 0}。</li>
 * </ul>
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionResolver {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionsRepository permissionsRepository;

    /**
     * 用户拥有的角色码（仅启用中的角色）
     *
     * @param userId 用户ID
     * @return 角色码集合
     */
    public Mono<Set<String>> resolveRoleCodes(Long userId) {
        if (userId == null) {
            return Mono.just(Collections.emptySet());
        }
        return roleRepository.findActiveRolesByUserId(userId)
                .map(Role::getCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * 用户拥有的权限ID（经由启用中的角色）
     *
     * @param userId 用户ID
     * @return 权限ID列表
     */
    public Mono<List<Long>> resolvePermissionIds(Long userId) {
        if (userId == null) {
            return Mono.just(Collections.emptyList());
        }
        return rolePermissionRepository.findPermissionIdsByUserId(userId)
                .collect(Collectors.toList());
    }

    /**
     * 用户拥有的权限标识集合
     *
     * @param userId 用户ID
     * @return 权限标识集合（{@code identity}）
     */
    public Mono<Set<String>> resolvePermissionIdentities(Long userId) {
        return resolvePermissionIds(userId)
                .flatMap(permissionIds -> {
                    if (permissionIds.isEmpty()) {
                        return Mono.just(Collections.emptySet());
                    }
                    return permissionsRepository.findEnabledByIdIn(permissionIds)
                            .map(Permissions::getIdentity)
                            .filter(identity -> identity != null && !identity.isBlank())
                            .collect(Collectors.toSet());
                });
    }

    /**
     * 用户是否拥有指定权限标识
     *
     * @param userId   用户ID
     * @param identity 权限标识
     * @return 拥有返回 true
     */
    public Mono<Boolean> hasPermission(Long userId, String identity) {
        if (userId == null || identity == null || identity.isBlank()) {
            return Mono.just(false);
        }
        return resolvePermissionIdentities(userId)
                .map(identities -> identities.contains(identity));
    }

    /**
     * 用户当前启用中的角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    public Flux<Role> resolveActiveRoles(Long userId) {
        if (userId == null) {
            return Flux.empty();
        }
        return roleRepository.findActiveRolesByUserId(userId);
    }

    /**
     * 用户当前启用中的角色ID
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    public Mono<List<Long>> resolveRoleIds(Long userId) {
        if (userId == null) {
            return Mono.just(Collections.emptyList());
        }
        return roleRepository.findActiveRoleIdsByUserId(userId)
                .collect(Collectors.toList());
    }
}
