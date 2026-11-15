package com.ryu.blog.service.impl;

import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.entity.Role;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RoleMenuRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 撤销权限 / 删除角色之前，<b>必须先查（并订阅）受影响用户的名单</b>。
 *
 * <p><b>它守的是什么</b>：删除角色时，代码会删掉 {@code t_user_roles} 的关联行。
 * 如果「查这个角色下有哪些用户」发生在删除<b>之后</b>，问出来就是空集 ——
 * 于是：没人被刷新、权限版本号（epoch）不自增、Redis 里的旧快照继续有效到 TTL 结束
 * （5 分钟）。<b>撤销等于没撤销</b>，而调用方照样拿到 200。
 *
 * <h3>为什么用 defer 记录顺序，而不是 Mockito 的 InOrder</h3>
 *
 * <p>Mockito 记录的是<b>方法被调用的那一刻</b>，而真正的查询发生在
 * {@code Mono}/{@code Flux} 的<b>订阅</b>时刻 —— 两者可以差得很远。
 * 实测：老写法（先删再查）里 {@code findUserIdsByRoleId} 的<b>调用</b>发生在组装期，
 * 比删除还早，用 InOrder 断言「先查后删」照样通过 —— <b>守不住任何东西</b>。
 *
 * <p>所以这里用 {@code Flux.defer} / {@code Mono.defer} 在<b>订阅时刻</b>记一笔，
 * 断言的才是真正的执行顺序。
 *
 * @author ryu
 */
@ExtendWith(MockitoExtension.class)
class RevokeRefreshesAffectedUsersTest {

    private static final Long ROLE_ID = 1001L;
    private static final Long PERMISSION_ID = 2002L;
    private static final Long AFFECTED_USER_ID = 7L;

    /**
     * 订阅时刻的事件流水。
     */
    private final List<String> events = new ArrayList<>();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionsRepository permissionsRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private RoleMenuRepository roleMenuRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserPermissionCache permissionCache;

    @InjectMocks
    private RoleServiceImpl roleService;

    @InjectMocks
    private PermissionsServiceImpl permissionsService;

    @BeforeEach
    void setUp() {
        lenient().when(permissionCache.refresh(any())).thenReturn(Mono.empty());

        // 订阅时刻才记事件 —— 这才是真正的执行顺序
        lenient().when(userRoleRepository.findUserIdsByRoleId(eq(ROLE_ID))).thenReturn(
                Flux.defer(() -> {
                    events.add("查用户");
                    return Flux.just(AFFECTED_USER_ID);
                }));
        lenient().when(userRoleRepository.deleteByRoleId(eq(ROLE_ID))).thenReturn(
                Mono.defer(() -> {
                    events.add("删用户关联");
                    return Mono.empty();
                }));
        lenient().when(rolePermissionRepository.findRoleIdsByPermissionId(eq(PERMISSION_ID))).thenReturn(
                Flux.defer(() -> {
                    events.add("查角色");
                    return Flux.just(ROLE_ID);
                }));
        lenient().when(rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(eq(ROLE_ID), anyList()))
                .thenReturn(Mono.defer(() -> {
                    events.add("删权限关联");
                    return Mono.empty();
                }));
    }

    @Test
    @DisplayName("删除角色：先查（订阅）受影响用户，再删关联行")
    void deletingRoleQueriesAffectedUsersBeforeDeleting() {
        Role role = new Role();
        role.setId(ROLE_ID);
        role.setCode("EDITOR");
        role.setIsDefault(false);
        role.setIsDeleted(false);
        when(roleRepository.findById(eq(ROLE_ID))).thenReturn(Mono.just(role));
        when(roleRepository.save(any())).thenAnswer(inv -> Mono.just(role));
        when(rolePermissionRepository.deleteByRoleId(eq(ROLE_ID))).thenReturn(Mono.empty());
        when(roleMenuRepository.deleteByRoleId(eq(ROLE_ID))).thenReturn(Mono.empty());

        roleService.removeById(ROLE_ID).block();

        assertEquals(List.of("查用户", "删用户关联"), events,
                "必须先查（并订阅）受影响用户的名单，再删关联行 —— "
                        + "顺序反了，删完就查不到人，撤销等于没撤销");
        verify(permissionCache, atLeastOnce()).refresh(eq(AFFECTED_USER_ID));
    }

    @Test
    @DisplayName("删除权限：先查（订阅）受影响角色与用户，再删关联行")
    void deletingPermissionQueriesAffectedUsersBeforeDeleting() {
        Permissions permission = new Permissions();
        permission.setId(PERMISSION_ID);
        permission.setIdentity(PermissionConstants.USER_VIEW);
        permission.setIsDeleted(false);
        when(permissionsRepository.findById(eq(PERMISSION_ID))).thenReturn(Mono.just(permission));
        when(permissionsRepository.save(any())).thenAnswer(inv -> Mono.just(permission));

        permissionsService.removeById(PERMISSION_ID).block();

        assertEquals(List.of("查角色", "查用户", "删权限关联"), events,
                "必须先查（并订阅）受影响的名单，再删关联行 —— 顺序反了，撤销等于没撤销");
        verify(permissionCache, atLeastOnce()).refresh(eq(AFFECTED_USER_ID));
    }
}
