package com.ryu.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PermissionsAssignDTO;
import com.ryu.blog.dto.RoleDTO;
import com.ryu.blog.dto.RoleListDTO;
import com.ryu.blog.dto.RoleUpdateDTO;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.entity.Role;
import com.ryu.blog.entity.User;
import com.ryu.blog.entity.RolePermission;
import com.ryu.blog.entity.UserRole;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RoleMenuRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.RoleService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.RolePermissionsVO;
import com.ryu.blog.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现类
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionsRepository permissionsRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final UserPermissionCache permissionCache;

    // =====================================================================
    // 写入
    // =====================================================================

    @Override
    @Transactional
    public Mono<Void> saveRole(OperatorContext ctx, RoleDTO roleDTO) {
        log.info("保存角色: name={}, code={}", roleDTO.getName(), roleDTO.getCode());

        // 唯一性校验：t_roles 上有 uk_code，不校验的话会直接撞唯一键报 400
        return roleRepository.existsByCode(roleDTO.getCode())
                .flatMap(codeExists -> {
                    if (Boolean.TRUE.equals(codeExists)) {
                        return Mono.error(BusinessException.roleCodeExists(roleDTO.getCode()));
                    }
                    return roleRepository.existsByName(roleDTO.getName())
                            .flatMap(nameExists -> {
                                if (Boolean.TRUE.equals(nameExists)) {
                                    return Mono.error(BusinessException.roleNameExists(roleDTO.getName()));
                                }
                                return doSaveRole(roleDTO);
                            });
                });
    }

    private Mono<Void> doSaveRole(RoleDTO roleDTO) {
        Role role = new Role();
        role.setName(roleDTO.getName());
        role.setCode(roleDTO.getCode());
        role.setDescription(roleDTO.getDescription());
        role.setSort(roleDTO.getSort() != null ? roleDTO.getSort() : 0);
        role.setIsActive(true);  // 默认激活
        role.setIsDefault(false); // 默认非默认角色
        role.setIsDeleted(false); // 未删除

        LocalDateTime now = LocalDateTime.now();
        role.setCreateTime(now);
        role.setUpdateTime(now);

        return roleRepository.save(role).then();
    }

    @Override
    @Transactional
    public Mono<Void> assignPermissions(OperatorContext ctx, PermissionsAssignDTO permissionsAssignDTO) {
        log.info("为角色分配权限: roleId={}, permissionIds={}",
                permissionsAssignDTO.getRoleId(), permissionsAssignDTO.getPermissionIds());

        Long roleId = permissionsAssignDTO.getRoleId();
        List<Long> permissionIds = permissionsAssignDTO.getPermissionIds();

        return findExistingRole(roleId)
                .flatMap(role -> {
                    if (permissionIds == null || permissionIds.isEmpty()) {
                        // 清空该角色的全部权限
                        return rolePermissionRepository.deleteByRoleId(roleId);
                    }
                    // 校验权限都真实存在 —— 否则会写入孤儿 permission_id
                    return permissionsRepository.findByIdIn(permissionIds)
                            .collectList()
                            .flatMap(existing -> {
                                if (existing.size() != permissionIds.stream().distinct().count()) {
                                    return Mono.error(BusinessException.permissionNotExists());
                                }
                                return replaceRolePermissions(roleId, permissionIds);
                            });
                })
                // 角色的权限变了 → 拥有该角色的用户的权限快照必须重建
                .then(refreshPermissionsOfRole(roleId));
    }

    /**
     * 覆盖式重建角色权限关联
     *
     * <p>两个关键点：
     * <ol>
     *   <li><b>必须显式 setIsDeleted(false)</b> —— {@code t_role_permissions.is_deleted}
     *       是 {@code NOT NULL}，而 Spring Data R2DBC 会把 null 属性<b>显式写成 NULL</b>
     *       （数据库的 DEFAULT 完全不生效），不赋值就是
     *       {@code Column 'is_deleted' cannot be null}；</li>
     *   <li>用 {@code concatMap} 而不是 {@code flatMap} —— 本方法在 {@code @Transactional} 内，
     *       整条链共用同一条 R2DBC 连接，flatMap 的并发执行会让语句在同一连接上交错。</li>
     * </ol>
     */
    private Mono<Void> replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        LocalDateTime now = LocalDateTime.now();
        return rolePermissionRepository.deleteByRoleId(roleId)
                .then(Flux.fromIterable(permissionIds.stream().distinct().toList())
                        .concatMap(permissionId -> {
                            RolePermission rp = new RolePermission();
                            rp.setRoleId(roleId);
                            rp.setPermissionId(permissionId);
                            rp.setIsDeleted(false);
                            rp.setCreateTime(now);
                            rp.setUpdateTime(now);
                            return rolePermissionRepository.save(rp);
                        })
                        .then());
    }

    @Override
    @Transactional
    public Mono<Void> removePermission(OperatorContext ctx, PermissionsAssignDTO permissionsAssignDTO) {
        log.info("删除角色权限: roleId={}, permissionIds={}",
                permissionsAssignDTO.getRoleId(), permissionsAssignDTO.getPermissionIds());

        Long roleId = permissionsAssignDTO.getRoleId();
        List<Long> permissionIds = permissionsAssignDTO.getPermissionIds();

        // 空集合必须提前挡掉：底层 SQL 是 `permission_id IN (:permissionIds)`，
        // 集合为空时驱动会把占位符展开成 `IN ()` —— MySQL 报语法错误 1064。
        // 这个接口是 REST 暴露的，客户端传空数组就会把请求打成 500。
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Mono.empty();
        }

        return findExistingRole(roleId)
                .flatMap(role -> rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(roleId, permissionIds))
                .then(refreshPermissionsOfRole(roleId));
    }

    @Override
    @Transactional
    public Mono<Boolean> changeRoleStatus(OperatorContext ctx, Long roleId, Boolean isActive) {
        log.info("变更角色状态, roleId: {}, isActive: {}", roleId, isActive);

        return findExistingRole(roleId)
                .flatMap(role -> {
                    role.setIsActive(isActive);
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role);
                })
                // 禁用角色后，该角色的权限必须立即失效
                .then(refreshPermissionsOfRole(roleId))
                .thenReturn(true);
    }

    @Override
    @Transactional
    public Mono<Boolean> batchAssignUserRoles(OperatorContext ctx, List<Long> userIds, Long roleId) {
        log.info("批量分配用户角色, userIds: {}, roleId: {}", userIds, roleId);

        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(false);
        }

        return findExistingRole(roleId)
                .flatMap(role -> {
                    // 校验用户都存在 —— t_user_roles 没有外键约束，否则会写入孤儿 user_id
                    return userRepository.findByIdIn(userIds)
                            .map(User::getId)
                            .collect(Collectors.toSet())
                            .flatMap(existingIds -> {
                                if (existingIds.size() != userIds.stream().distinct().count()) {
                                    return Mono.error(BusinessException.userNotFound());
                                }
                                LocalDateTime now = LocalDateTime.now();
                                return Flux.fromIterable(userIds.stream().distinct().toList())
                                        .concatMap(userId -> userRoleRepository.deleteByUserIdAndRoleId(userId, roleId)
                                                .then(userRoleRepository.save(newUserRole(userId, roleId, now))))
                                        .then();
                            });
                })
                .then(refreshPermissionsOfRole(roleId))
                .thenReturn(true);
    }

    @Override
    @Transactional
    public Mono<Void> removeById(OperatorContext ctx, Long id) {
        log.info("删除角色, id: {}", id);

        return findExistingRole(id)
                .flatMap(role -> {
                    // 内置角色不允许删除 —— 删掉 ADMIN 会让整个系统失去管理员
                    if (PermissionConstants.ROLE_CODE_ADMIN.equalsIgnoreCase(role.getCode())
                            || Boolean.TRUE.equals(role.getIsDefault())) {
                        return Mono.error(PermissionDeniedException.accessDenied("内置角色不允许删除"));
                    }
                    // 逻辑删除
                    role.setIsDeleted(true);
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role)
                            .then(rolePermissionRepository.deleteByRoleId(id))
                            // 可见性轴：角色没了，它占的菜单名额也要一起收走 ——
                            // 否则 t_sys_role_menus 里会留下一批永远查不到角色的悬空行
                            .then(roleMenuRepository.deleteByRoleId(id))
                            .then(userRoleRepository.deleteByRoleId(id));
                })
                .then(refreshPermissionsOfRole(id));
    }

    @Override
    @Transactional
    public Mono<Role> updateRole(OperatorContext ctx, RoleUpdateDTO roleUpdateDTO) {
        log.info("更新角色信息, id: {}", roleUpdateDTO.getId());

        return findExistingRole(roleUpdateDTO.getId())
                .flatMap(role -> {
                    // 角色编码唯一性校验（t_roles 上有 uk_code）
                    if (roleUpdateDTO.getCode() != null
                            && !roleUpdateDTO.getCode().equals(role.getCode())) {
                        // 内置角色的编码不允许改 —— 改了所有基于 code 的判断全部失效
                        if (PermissionConstants.ROLE_CODE_ADMIN.equalsIgnoreCase(role.getCode())) {
                            return Mono.error(PermissionDeniedException.accessDenied("内置角色的编码不允许修改"));
                        }
                        return roleRepository.existsByCode(roleUpdateDTO.getCode())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(BusinessException.roleCodeExists(roleUpdateDTO.getCode()));
                                    }
                                    return doUpdateRole(role, roleUpdateDTO);
                                });
                    }
                    return doUpdateRole(role, roleUpdateDTO);
                });
    }

    private Mono<Role> doUpdateRole(Role role, RoleUpdateDTO roleUpdateDTO) {
        if (roleUpdateDTO.getName() != null) {
            role.setName(roleUpdateDTO.getName());
        }
        if (roleUpdateDTO.getCode() != null) {
            role.setCode(roleUpdateDTO.getCode());
        }
        if (roleUpdateDTO.getDescription() != null) {
            role.setDescription(roleUpdateDTO.getDescription());
        }
        if (roleUpdateDTO.getSort() != null) {
            role.setSort(roleUpdateDTO.getSort());
        }
        if (roleUpdateDTO.getIsActive() != null) {
            role.setIsActive(roleUpdateDTO.getIsActive());
        }

        role.setUpdateTime(LocalDateTime.now());
        return roleRepository.save(role);
    }

    // =====================================================================
    // 查询
    // =====================================================================

    @Override
    public Mono<PageResult<Role>> getRolesByConditions(RoleListDTO roleListDTO) {
        long currentPage = roleListDTO.getCurrentPage() != null ? roleListDTO.getCurrentPage() : 1L;
        long pageSize = roleListDTO.getPageSize() != null ? roleListDTO.getPageSize() : 10L;
        // 分页在数据库里做 —— 原先是把全表拉进内存再 skip/limit
        PageRequest pageRequest = PageRequest.of((int) (currentPage - 1), (int) pageSize);

        boolean byName = roleListDTO.getName() != null && !roleListDTO.getName().isBlank();
        Flux<Role> roles = byName
                ? roleRepository.findByNameContaining(roleListDTO.getName(), pageRequest)
                : roleRepository.findAllRoles(pageRequest);
        Mono<Long> count = byName
                ? roleRepository.countByNameContaining(roleListDTO.getName())
                : roleRepository.countAllRoles();

        return Mono.zip(roles.collectList(), count)
                .map(tuple -> buildPageResult(tuple.getT1(), tuple.getT2(), currentPage, pageSize));
    }

    private PageResult<Role> buildPageResult(List<Role> pageData, Long total, long currentPage, long pageSize) {
        PageResult<Role> pageResult = new PageResult<>();
        pageResult.setRecords(pageData);
        pageResult.setTotal(total == null ? 0L : total);
        pageResult.setSize(pageSize);
        pageResult.setCurrent(currentPage);
        pageResult.setPages(pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize);
        return pageResult;
    }

    @Override
    public Mono<RoleVO> getRoleDetails(Long id) {
        return findExistingRole(id)
                .flatMap(role -> {
                    RoleVO roleVO = new RoleVO();
                    BeanUtil.copyProperties(role, roleVO);
                    return fillRolePermissions(roleVO, id);
                });
    }

    @Override
    public Mono<RolePermissionsVO> getRolePermissions(Long id) {
        return findExistingRole(id)
                .flatMap(role -> {
                    RolePermissionsVO rolePermissionsVO = new RolePermissionsVO();
                    rolePermissionsVO.setRole(role);
                    return rolePermissionRepository.findPermissionIdsByRoleId(id)
                            .collectList()
                            .flatMap(permissionIds -> {
                                if (permissionIds.isEmpty()) {
                                    rolePermissionsVO.setPermissions(new ArrayList<>());
                                    return Mono.just(rolePermissionsVO);
                                }
                                return permissionsRepository.findByIdIn(permissionIds)
                                        .collectList()
                                        .doOnNext(rolePermissionsVO::setPermissions)
                                        .thenReturn(rolePermissionsVO);
                            });
                });
    }

    private Mono<RoleVO> fillRolePermissions(RoleVO roleVO, Long roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId)
                .collectList()
                .flatMap(permissionIds -> {
                    if (permissionIds.isEmpty()) {
                        roleVO.setPermissions(new ArrayList<>());
                        return Mono.just(roleVO);
                    }
                    return permissionsRepository.findByIdIn(permissionIds)
                            .collectList()
                            .doOnNext(roleVO::setPermissions)
                            .thenReturn(roleVO);
                });
    }

    @Override
    public Mono<Role> getDefaultRole() {
        // 用 findDefaultRoles().next() 而不是派生查询 findByIsDefaultAndIsDeleted：
        // 后者在出现多个默认角色时会抛 IncorrectResultSizeDataAccessException
        return roleRepository.findDefaultRoles().next();
    }

    @Override
    public Flux<Role> getUserRoles(OperatorContext ctx, Long userId) {
        // 归属校验：只能查自己的角色，或持有 system:user:manage
        OwnershipPolicy.requireCanActOn(ctx, userId);
        return roleRepository.findActiveRolesByUserId(userId);
    }

    @Override
    public Flux<Role> getAllRoles() {
        return roleRepository.findAllRoles();
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    /**
     * 查出角色并确认它没被逻辑删除
     *
     * <p>原先写的是 {@code !Integer.valueOf(1).equals(role.getIsDeleted())}，
     * 而 {@code isDeleted} 是 {@code Boolean} —— {@code Integer(1).equals(Boolean.TRUE)}
     * 恒为 false，取反后恒为 true，于是这个 filter <b>什么也没过滤</b>：
     * 已删除的角色照样能被查出、被修改（等于复活）、被重复删除。
     */
    private Mono<Role> findExistingRole(Long roleId) {
        return roleRepository.findById(roleId)
                .filter(role -> !Boolean.TRUE.equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.error(BusinessException.roleNotExists()));
    }

    private UserRole newUserRole(Long userId, Long roleId, LocalDateTime now) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setCreateTime(now);
        userRole.setUpdateTime(now);
        return userRole;
    }

    /**
     * 角色相关的任何变更之后，重建拥有该角色的用户的权限快照
     */
    private Mono<Void> refreshPermissionsOfRole(Long roleId) {
        return userRoleRepository.findUserIdsByRoleId(roleId)
                .concatMap(userId -> permissionCache.refresh(userId))
                .then();
    }
}
