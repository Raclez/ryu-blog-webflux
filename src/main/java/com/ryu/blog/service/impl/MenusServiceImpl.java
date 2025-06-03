package com.ryu.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeUtil;
import com.ryu.blog.dto.MenusSaveDTO;
import com.ryu.blog.dto.MenusUpdateDTO;
import com.ryu.blog.entity.MenuPermission;
import com.ryu.blog.entity.Menus;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.repository.MenuPermissionRepository;
import com.ryu.blog.repository.MenusRepository;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.service.MenusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final MenuPermissionRepository menuPermissionRepository;
    private final PermissionsRepository permissionsRepository;

    @Override
    public Mono<List<Tree<Long>>> getMenusByTree() {
        return menusRepository.findAllOrderByParentIdAndSort()
                .collectList()
                .map(menus -> {
                    // 构建节点列表
                    List<TreeNode<Long>> nodeList = new ArrayList<>();
                    
                    for (Menus menu : menus) {
                        TreeNode<Long> node = new TreeNode<>();
                        node.setId(menu.getId());
                        node.setName(menu.getName());
                        node.setParentId(menu.getParentId());
                        node.setWeight(menu.getSort());
                        
                        // 添加扩展属性
                        Map<String, Object> extra = new HashMap<>();
                        extra.put("icon", menu.getIcon());
                        extra.put("path", menu.getPath());
                        extra.put("component", menu.getComponent());
                        extra.put("type", menu.getType());
                        extra.put("hidden", menu.getHidden());
                        extra.put("redirect", menu.getRedirect());
                        
                        node.setExtra(extra);
                        nodeList.add(node);
                    }
                    
                    // 构建树形结构，默认父节点ID为0
                    return TreeUtil.build(nodeList, 0L);
                });
    }

    @Override
    public Flux<Menus> listAll() {
        return menusRepository.findAllOrderByParentIdAndSort();
    }

    @Override
    @Transactional
    public Mono<Void> bindPermissions(List<Long> permissionIds, Long menuId) {
        log.info("绑定权限到菜单, menuId: {}, permissionIds: {}", menuId, permissionIds);
        
        // 先查询菜单是否存在
        return menusRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("菜单不存在")))
                .flatMap(menu -> {
                    // 删除原有的权限关联
                    return menuPermissionRepository.deleteByMenuId(menuId)
                            .then(Mono.defer(() -> {
                                // 如果权限ID列表为空，直接返回
                                if (permissionIds == null || permissionIds.isEmpty()) {
                                    return Mono.empty();
                                }
                                
                                // 查询权限是否都存在
                                return permissionsRepository.findByIdIn(permissionIds)
                                        .collectList()
                                        .flatMap(permissions -> {
                                            if (permissions.size() != permissionIds.size()) {
                                                return Mono.error(new IllegalArgumentException("部分权限不存在"));
                                            }
                                            
                                            // 创建新的关联
                                            List<MenuPermission> menuPermissions = new ArrayList<>();
                                            LocalDateTime now = LocalDateTime.now();
                                            
                                            for (Long permissionId : permissionIds) {
                                                MenuPermission menuPermission = MenuPermission.builder()
                                                        .menuId(menuId)
                                                        .permissionId(permissionId)
                                                        .createTime(now)
                                                        .updateTime(now)
                                                        .build();
                                                menuPermissions.add(menuPermission);
                                            }
                                            
                                            // 批量保存关联
                                            return Flux.fromIterable(menuPermissions)
                                                    .flatMap(menuPermissionRepository::save)
                                                    .then();
                                        });
                            }));
                });
    }

    @Override
    public Flux<Menus> getUserMenus(List<String> permissionIdentities) {
        if (permissionIdentities == null || permissionIdentities.isEmpty()) {
            return Flux.empty();
        }
        
        // 查询权限ID
        return permissionsRepository.findByIdentityIn(permissionIdentities)
                .map(Permissions::getId)
                .collectList()
                .flatMapMany(permissionIds -> {
                    if (permissionIds.isEmpty()) {
                        return Flux.empty();
                    }
                    
                    // 查询菜单ID
                    return menuPermissionRepository.findMenuIdsByPermissionIds(permissionIds)
                            .collectList()
                            .flatMapMany(menuIds -> {
                                if (menuIds.isEmpty()) {
                                    return Flux.empty();
                                }
                                
                                // 查询菜单
                                return menusRepository.findByIdIn(menuIds);
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Void> saveMenu(MenusSaveDTO menusSaveDTO) {
        log.info("保存菜单: {}", menusSaveDTO);
        
        // 创建菜单实体
        Menus menu = new Menus();
        BeanUtil.copyProperties(menusSaveDTO, menu);
        
        // 设置默认值
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getSort() == null) {
            menu.setSort(0);
        }
        
        // 类型转换
        menu.setType(menusSaveDTO.getMenuType());
        
        // 其他属性设置
        LocalDateTime now = LocalDateTime.now();
        menu.setCreateTime(now);
        menu.setUpdateTime(now);
        
        // 保存菜单
        return menusRepository.save(menu).then();
    }

    @Override
    @Transactional
    public Mono<Void> updateMenu(MenusUpdateDTO menusUpdateDTO) {
        log.info("更新菜单: {}", menusUpdateDTO);
        
        return menusRepository.findById(menusUpdateDTO.getId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("菜单不存在")))
                .flatMap(menu -> {
                    // 更新属性
                    menu.setName(menusUpdateDTO.getName());
                    menu.setIcon(menusUpdateDTO.getIcon());
                    menu.setParentId(menusUpdateDTO.getParentId());
                    menu.setSort(menusUpdateDTO.getSort());
                    menu.setType(menusUpdateDTO.getMenuType());
                    menu.setPath(menusUpdateDTO.getUrl());
                    menu.setComponent(menusUpdateDTO.getComponent());
                    menu.setUpdateTime(LocalDateTime.now());
                    
                    // 保存更新
                    return menusRepository.save(menu).then();
                });
    }

    @Override
    @Transactional
    public Mono<Void> deleteMenu(Long id) {
        log.info("删除菜单, id: {}", id);
        
        // 检查是否有子菜单
        return menusRepository.countByParentId(id)
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new IllegalArgumentException("存在子菜单，无法删除"));
                    }
                    
                    // 删除菜单权限关联
                    return menuPermissionRepository.deleteByMenuId(id)
                            .then(menusRepository.deleteById(id));
                });
    }
} 