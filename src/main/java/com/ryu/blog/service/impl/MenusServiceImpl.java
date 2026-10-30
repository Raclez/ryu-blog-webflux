package com.ryu.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.tree.Tree;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.MenusSaveDTO;
import com.ryu.blog.dto.MenusUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.Menus;
import com.ryu.blog.entity.RoleMenu;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.repository.MenusRepository;
import com.ryu.blog.repository.RoleMenuRepository;
import com.ryu.blog.service.MenusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenusServiceImpl implements MenusService {

    private final MenusRepository menusRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final UserPermissionResolver permissionResolver;

    /**
     * 自身代理 —— 让类内部的调用也能命中 {@code @Cacheable}。
     *
     * <p>{@code getMenusByTree()} 需要用 {@code listAll()} 的缓存结果。
     * 直接写 {@code listAll()} 是<b>自调用</b>：Spring AOP 的 CGLIB 代理里，
     * 目标对象内部的 {@code this} 指向的是目标实例而不是代理，
     * 拦截器根本不会触发 —— 缓存形同虚设，每次请求都打一次全表查询。
     *
     * <p>{@code @Lazy} 是为了打破「代理依赖自己」的循环依赖。
     */
    private MenusService self;

    @Autowired
    public void setSelf(@Lazy MenusService self) {
        this.self = self;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    @Override
    public Mono<List<Tree<Long>>> getMenusByTree() {
        // 缓存的是<b>平铺的菜单列表</b>，树在每次请求时构建。
        // 原先缓存的是构建好的 Tree 对象 —— 而 Tree extends LinkedHashMap，
        // 是可变 Map，被所有请求共享，任何一处 in-place 修改都会污染全实例缓存。
        //
        // 走 self 代理：直接写 listAll() 是自调用，会绕过 Spring 的缓存拦截器，
        // 每次请求都会重新查一次整张菜单表。
        return self.listAll()
                .map(MenuTreeBuilder::buildTree);
    }

    @Override
    @Cacheable(value = CacheConstants.MENUS_CACHE, key = "'" + CacheConstants.MENUS_ALL_KEY + "'")
    public Mono<List<Menus>> listAll() {
        return menusRepository.findAllOrderByParentIdAndSort()
                .collectList();
    }

    @Override
    public Mono<List<Tree<Long>>> getUserMenusTree(Long userId) {
        return permissionResolver.resolveRoleIds(userId)
                .flatMap(roleIds -> {
                    if (roleIds.isEmpty()) {
                        return Mono.just(List.<Tree<Long>>of());
                    }
                    return roleMenuRepository.findMenuIdsByRoleIds(roleIds)
                            .collectList()
                            .flatMap(menuIds -> {
                                if (menuIds.isEmpty()) {
                                    return Mono.just(List.<Tree<Long>>of());
                                }
                                return buildUserMenuTree(menuIds);
                            });
                });
    }

    /**
     * 构建用户可见菜单树
     *
     * <p>关键：<b>补齐祖先目录</b>。只绑了叶子菜单而没绑它的父目录时，
     * 直接返回会让前端建树出现没有父级的孤儿节点。
     */
    private Mono<List<Tree<Long>>> buildUserMenuTree(List<Long> menuIds) {
        return menusRepository.findAllOrderByParentIdAndSort()
                .collectList()
                .map(allMenus -> {
                    Set<Long> visible = MenuTreeBuilder.withAncestors(allMenus, menuIds);
                    List<Menus> visibleMenus = allMenus.stream()
                            .filter(menu -> visible.contains(menu.getId()))
                            .collect(Collectors.toList());
                    return MenuTreeBuilder.buildTree(visibleMenus);
                });
    }

    // =====================================================================
    // 写入
    // =====================================================================

    @Override
    @Transactional
    @CacheEvict(value = CacheConstants.MENUS_CACHE, allEntries = true)
    public Mono<Void> bindRoles(OperatorContext ctx, List<Long> roleIds, Long menuId) {
        log.info("为菜单分配角色, menuId: {}, roleIds: {}", menuId, roleIds);

        return menusRepository.findById(menuId)
                .switchIfEmpty(Mono.error(BusinessException.menuNotFound()))
                .flatMap(menu -> {
                    // 先删掉原有绑定
                    return roleMenuRepository.deleteByMenuId(menuId)
                            .then(Mono.defer(() -> {
                                if (roleIds == null || roleIds.isEmpty()) {
                                    return Mono.empty();
                                }
                                LocalDateTime now = LocalDateTime.now();
                                List<RoleMenu> bindings = roleIds.stream()
                                        .distinct()
                                        .map(roleId -> {
                                            RoleMenu binding = new RoleMenu();
                                            binding.setRoleId(roleId);
                                            binding.setMenuId(menuId);
                                            binding.setIsDeleted(false);
                                            binding.setCreateTime(now);
                                            binding.setUpdateTime(now);
                                            return binding;
                                        })
                                        .collect(Collectors.toList());
                                return roleMenuRepository.saveAll(bindings).then();
                            }));
                })
                .then();
    }

    @Override
    public Mono<List<Long>> getRoleIdsByMenu(Long menuId) {
        // 先确认菜单存在 —— 不存在时返回 404，而不是空列表
        // （空列表会被前端理解成「这个菜单谁都能看」）
        return menusRepository.findById(menuId)
                .switchIfEmpty(Mono.error(BusinessException.menuNotFound()))
                .flatMap(menu -> roleMenuRepository.findRoleIdsByMenuId(menuId).collectList());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConstants.MENUS_CACHE, allEntries = true)
    public Mono<Void> saveMenu(OperatorContext ctx, MenusSaveDTO menusSaveDTO) {
        log.info("保存菜单: name={}", menusSaveDTO.getName());

        Menus menu = new Menus();
        BeanUtil.copyProperties(menusSaveDTO, menu, "menuType", "url");
        menu.setType(menusSaveDTO.getMenuType());
        menu.setPath(menusSaveDTO.getUrl());

        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getSort() == null) {
            menu.setSort(0);
        }
        if (menu.getHidden() == null) {
            menu.setHidden(0);
        }
        if (menu.getIsLink() == null) {
            menu.setIsLink(false);
        }

        // 父菜单必须存在 —— 否则这个菜单会变成孤儿，
        // 在树里被静默丢弃（接口不报错，菜单却凭空消失）
        return validateParent(menu.getParentId(), null)
                .then(Mono.defer(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    menu.setCreateTime(now);
                    menu.setUpdateTime(now);
                    // is_deleted 必须显式赋值：t_menus.is_deleted 虽然可空，
                    // 但写成 NULL 会让将来补上的 `is_deleted = 0` 过滤把存量菜单全滤掉
                    menu.setIsDeleted(false);
                    return menusRepository.save(menu).then();
                }));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConstants.MENUS_CACHE, allEntries = true)
    public Mono<Void> updateMenu(OperatorContext ctx, MenusUpdateDTO menusUpdateDTO) {
        log.info("更新菜单: id={}", menusUpdateDTO.getId());

        return menusRepository.findById(menusUpdateDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.menuNotFound()))
                .flatMap(menu -> {
                    // 【关键】忽略 null —— Hutool 的 CopyOptions 默认会把 null 写进目标，
                    // 于是「只改名字」的请求会把 parent_id / path 抹成 null，
                    // 菜单就从树上凭空消失了
                    BeanUtil.copyProperties(menusUpdateDTO, menu,
                            "id", "menuType", "url", "parentId", "sort", "hidden",
                            "component", "redirect", "icon");
                    if (menusUpdateDTO.getMenuType() != null) {
                        menu.setType(menusUpdateDTO.getMenuType());
                    }
                    if (menusUpdateDTO.getUrl() != null) {
                        menu.setPath(menusUpdateDTO.getUrl());
                    }
                    if (menusUpdateDTO.getParentId() != null) {
                        menu.setParentId(menusUpdateDTO.getParentId());
                    }
                    if (menusUpdateDTO.getSort() != null) {
                        menu.setSort(menusUpdateDTO.getSort());
                    }
                    if (menusUpdateDTO.getHidden() != null) {
                        menu.setHidden(menusUpdateDTO.getHidden());
                    }
                    if (menusUpdateDTO.getComponent() != null) {
                        menu.setComponent(menusUpdateDTO.getComponent());
                    }
                    if (menusUpdateDTO.getRedirect() != null) {
                        menu.setRedirect(menusUpdateDTO.getRedirect());
                    }
                    if (menusUpdateDTO.getIcon() != null) {
                        menu.setIcon(menusUpdateDTO.getIcon());
                    }
                    if (menusUpdateDTO.getIsLink() != null) {
                        menu.setIsLink(menusUpdateDTO.getIsLink());
                    }
                    menu.setUpdateTime(LocalDateTime.now());

                    // 父菜单必须存在，且不能成环
                    return validateParent(menu.getParentId(), menu.getId())
                            .then(menusRepository.save(menu))
                            .then();
                });
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConstants.MENUS_CACHE, allEntries = true)
    public Mono<Void> deleteMenu(OperatorContext ctx, Long id) {
        log.info("删除菜单, id: {}", id);

        return menusRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.menuNotFound()))
                .flatMap(menu -> menusRepository.countByParentId(id)
                        .flatMap(count -> {
                            if (count > 0) {
                                return Mono.error(new IllegalArgumentException("存在子菜单，无法删除"));
                            }
                            // 逻辑删除 + 清掉角色绑定
                            menu.setIsDeleted(true);
                            menu.setUpdateTime(LocalDateTime.now());
                            return menusRepository.save(menu)
                                    .then(roleMenuRepository.deleteByMenuId(id))
                                    .then();
                        }));
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    /**
     * 校验父菜单：必须存在，且不能形成环
     *
     * @param parentId   父菜单ID
     * @param menuId     当前菜单ID（新建时为 null）
     * @return 校验通过
     */
    private Mono<Void> validateParent(Long parentId, Long menuId) {
        if (parentId == null || parentId == 0L) {
            return Mono.empty();
        }
        return menusRepository.findById(parentId)
                .switchIfEmpty(Mono.error(BusinessException.menuParentNotExists()))
                .flatMap(parent -> {
                    if (menuId == null) {
                        return Mono.empty();
                    }
                    // 把菜单挂到自己的子孙下面会成环
                    return isAncestor(parent.getId(), menuId, 0)
                            .flatMap(cyclic -> cyclic
                                    ? Mono.error(PermissionDeniedException.accessDenied("父菜单设置会形成循环"))
                                    : Mono.empty());
                });
    }

    /**
     * 判断 candidateId 是否是 menuId 的祖先（沿 parent 链向上走）
     *
     * <p>纯反应式实现 —— 不用 {@code block()}：那会在事件循环上阻塞，
     * 且 R2DBC 的连接可能正等着同一个事件循环，直接死锁。
     *
     * @param candidateId 候选祖先ID
     * @param menuId      当前菜单ID
     * @param depth       当前递归深度（用于兜底防环）
     * @return 是祖先返回 true
     */
    private Mono<Boolean> isAncestor(Long candidateId, Long menuId, int depth) {
        if (depth > 64) {
            return Mono.just(false);
        }
        return menusRepository.findById(candidateId)
                .map(Menus::getParentId)
                .defaultIfEmpty(0L)
                .flatMap(parentId -> {
                    if (parentId == null || parentId == 0L) {
                        return Mono.just(false);
                    }
                    if (parentId.equals(menuId)) {
                        return Mono.just(true);
                    }
                    return isAncestor(parentId, menuId, depth + 1);
                });
    }
}
