package com.ryu.blog.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.ryu.blog.entity.Menus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单树构建规则的单元测试
 *
 * <p>这条规则出过真实故障：Hutool 的 {@code TreeUtil.build} 对
 * 「父节点不在集合里」的节点是<b>静默丢弃</b> —— 接口不报错，前端菜单凭空消失。
 *
 * <p>纯函数，所以不 mock 任何东西。
 */
class MenuTreeBuilderTest {

    private static Menus menu(Long id, Long parentId, Integer sort, String name) {
        Menus menus = new Menus();
        menus.setId(id);
        menus.setParentId(parentId);
        menus.setSort(sort);
        menus.setName(name);
        menus.setType(1);
        menus.setHidden(0);
        return menus;
    }

    @Test
    @DisplayName("正常层级 → 构成正确的父子关系")
    void normalHierarchyBuildsTree() {
        List<Menus> all = List.of(
                menu(1L, 0L, 1, "系统管理"),
                menu(2L, 1L, 1, "用户管理"),
                menu(3L, 1L, 2, "角色管理"),
                menu(4L, 0L, 2, "内容管理"));

        List<Tree<Long>> roots = MenuTreeBuilder.buildTree(all);

        assertEquals(2, roots.size());
        Tree<Long> systemRoot = roots.get(0);
        assertEquals(1L, systemRoot.getId());
        assertEquals(2, systemRoot.getChildren().size());
        assertEquals("系统管理", systemRoot.get("name"));
    }

    @Test
    @DisplayName("父节点缺失的孤儿 → 当作根节点，而不是被丢弃")
    void orphanBecomesRootInsteadOfBeingDropped() {
        // 父菜单 id=99 不在集合里
        List<Menus> all = List.of(menu(1L, 99L, 1, "孤儿菜单"));

        List<Tree<Long>> roots = MenuTreeBuilder.buildTree(all);

        assertEquals(1, roots.size(), "孤儿节点不应被丢弃");
        assertEquals(1L, roots.get(0).getId());
    }

    @Test
    @DisplayName("parent_id 为 null → 当作根节点")
    void nullParentBecomesRoot() {
        List<Menus> all = List.of(menu(1L, null, 1, "无父菜单"));

        List<Tree<Long>> roots = MenuTreeBuilder.buildTree(all);

        assertEquals(1, roots.size());
        assertEquals(1L, roots.get(0).getId());
    }

    @Test
    @DisplayName("父子成环 → 不死循环，节点仍被返回")
    void cycleDoesNotHang() {
        // 1 的父是 2，2 的父是 1
        List<Menus> all = List.of(
                menu(1L, 2L, 1, "A"),
                menu(2L, 1L, 2, "B"));

        List<Tree<Long>> roots = MenuTreeBuilder.buildTree(all);

        // 成环时两个节点都会被当作根处理 —— 关键是「不丢、不挂死」
        assertEquals(2, roots.size());
    }

    @Test
    @DisplayName("withAncestors：补齐全部祖先目录")
    void withAncestorsAddsAllAncestors() {
        List<Menus> all = List.of(
                menu(1L, 0L, 1, "系统管理"),
                menu(2L, 1L, 1, "用户管理"),
                menu(3L, 2L, 1, "用户详情"));

        // 只绑了叶子菜单
        Set<Long> visible = MenuTreeBuilder.withAncestors(all, Set.of(3L));

        assertEquals(Set.of(1L, 2L, 3L), visible, "祖先目录必须一并补齐");
    }

    @Test
    @DisplayName("withAncestors：只有叶子可见时，构树后父级也在")
    void treeBuiltFromLeafOnlyStillHasParent() {
        List<Menus> all = List.of(
                menu(1L, 0L, 1, "系统管理"),
                menu(2L, 1L, 1, "用户管理"),
                menu(3L, 2L, 1, "用户详情"));

        Set<Long> visible = MenuTreeBuilder.withAncestors(all, Set.of(3L));
        List<Menus> visibleMenus = all.stream().filter(m -> visible.contains(m.getId())).toList();

        List<Tree<Long>> roots = MenuTreeBuilder.buildTree(visibleMenus);

        assertEquals(1, roots.size());
        Tree<Long> root = roots.get(0);
        assertEquals(1L, root.getId());
        assertEquals(1, root.getChildren().size());
        assertEquals(1, root.getChildren().get(0).getChildren().size());
    }

    @Test
    @DisplayName("withAncestors：祖先链成环时不会死循环")
    void withAncestorsTerminatesOnCycle() {
        List<Menus> all = List.of(
                menu(1L, 2L, 1, "A"),
                menu(2L, 1L, 2, "B"));

        Set<Long> visible = MenuTreeBuilder.withAncestors(all, Set.of(1L));

        assertNotNull(visible);
        assertTrue(visible.contains(1L));
    }

    @Test
    @DisplayName("排序：sort 升序，同 sort 时按 id 兜底")
    void sortingIsStable() {
        List<Menus> all = List.of(
                menu(3L, 0L, 2, "C"),
                menu(1L, 0L, 1, "A"),
                menu(2L, 0L, 1, "B"));

        List<Tree<Long>> roots = MenuTreeBuilder.buildTree(all);

        assertEquals(1L, roots.get(0).getId());
        assertEquals(2L, roots.get(1).getId());
        assertEquals(3L, roots.get(2).getId());
    }
}
