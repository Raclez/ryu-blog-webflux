package com.ryu.blog.service.impl;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import com.ryu.blog.entity.Menus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单树构建
 *
 * <p><b>为什么不用 Hutool 的 {@code TreeUtil.build} 了事</b>：
 * 它对「父节点不在集合里」的节点是<b>静默丢弃</b> —— 接口不报错，
 * 前端菜单就那样凭空消失。原先的三个成因：
 * <ul>
 *   <li>{@code saveMenu} 不校验 {@code parentId} 存在，父菜单被删后子菜单变孤儿；</li>
 *   <li>{@code updateMenu} 用 {@code BeanUtil.copyProperties} 且默认不忽略 null，
 *       部分更新会把 {@code parent_id} 抹成 null；</li>
 *   <li>没有成环检测，{@code A → B → A} 的菜单互相指向。</li>
 * </ul>
 *
 * <p>本类把「补齐祖先」与「构树」两件事显式做掉：
 * <ul>
 *   <li>{@link #withAncestors} —— 可见菜单之外，把它们的<b>全部祖先</b>也补进来，
 *       否则只绑了叶子菜单时，前端建树会出现没有父级的孤儿节点；</li>
 *   <li>{@link #buildTree} —— 父节点缺失的当根节点处理（而不是丢弃），
 *       成环的当根节点处理（而不是死循环）。</li>
 * </ul>
 *
 * <p>纯函数，无 Spring 依赖，可直接单测。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    /**
     * 计算「可见菜单 + 其全部祖先」的 ID 集合
     *
     * @param allMenus   全量菜单（用于回溯祖先）
     * @param visibleIds 可见菜单ID
     * @return 需要返回给前端的菜单ID集合
     */
    public static Set<Long> withAncestors(List<Menus> allMenus, Collection<Long> visibleIds) {
        Map<Long, Long> parentMap = new HashMap<>();
        for (Menus menu : allMenus) {
            parentMap.put(menu.getId(), menu.getParentId());
        }

        Set<Long> result = new LinkedHashSet<>(visibleIds);
        for (Long id : visibleIds) {
            Long parentId = parentMap.get(id);
            // visited 防环：parent_id 成环时这里会死循环
            Set<Long> visited = new HashSet<>();
            while (parentId != null && parentId != 0L && result.add(parentId) && visited.add(parentId)) {
                parentId = parentMap.get(parentId);
            }
        }
        return result;
    }

    /**
     * 把菜单列表构造成树
     *
     * <p>与 Hutool {@code TreeUtil.build} 的区别：
     * <ul>
     *   <li>父节点不在集合里 → <b>当作根节点</b>（Hutool 是静默丢弃）；</li>
     *   <li>父子成环 → <b>当作根节点</b>（Hutool 也会丢弃，但这里语义明确）。</li>
     * </ul>
     *
     * @param menus 菜单列表
     * @return 根节点列表
     */
    public static List<Tree<Long>> buildTree(List<Menus> menus) {
        Map<Long, Menus> byId = new HashMap<>();
        for (Menus menu : menus) {
            byId.put(menu.getId(), menu);
        }

        // 找出真正的根：parent_id 为空/0，或父节点不在集合里，或成环
        Set<Long> rootIds = new HashSet<>();
        for (Menus menu : menus) {
            if (isRoot(menu, byId)) {
                rootIds.add(menu.getId());
            }
        }

        List<TreeNode<Long>> nodeList = new ArrayList<>(menus.size());
        for (Menus menu : menus) {
            nodeList.add(toTreeNode(menu));
        }

        Map<Long, Tree<Long>> treeMap = new HashMap<>();
        for (TreeNode<Long> node : nodeList) {
            // Hutool 的 Tree 没有 Tree(TreeNode) 构造器，手工构建；
            // extra 字段要「平铺」进 Tree（它本身就是个 Map），
            // 与 Hutool DefaultNodeParser 的行为保持一致
            Tree<Long> tree = new Tree<>();
            tree.setId(node.getId());
            tree.setName(node.getName());
            tree.setParentId(node.getParentId());
            tree.setWeight(node.getWeight());
            if (node.getExtra() != null) {
                tree.putAll(node.getExtra());
            }
            treeMap.put(node.getId(), tree);
        }

        List<Tree<Long>> roots = new ArrayList<>();
        for (Long rootId : rootIds) {
            Tree<Long> root = treeMap.get(rootId);
            if (root != null) {
                roots.add(root);
            }
        }

        // 挂子节点：逐个向上找到最近的、已在集合内的祖先
        for (Menus menu : menus) {
            if (rootIds.contains(menu.getId())) {
                continue;
            }
            Tree<Long> node = treeMap.get(menu.getId());
            Long parentId = nearestAncestor(menu, byId);
            Tree<Long> parent = parentId == null ? null : treeMap.get(parentId);
            if (parent != null) {
                parent.addChildren(node);
            }
        }

        // 排序：sort 升序，id 升序兜底
        roots.sort(MenuTreeBuilder::compareTree);
        for (Tree<Long> root : roots) {
            sortChildren(root);
        }
        return roots;
    }

    /**
     * 判断菜单是不是根节点
     */
    private static boolean isRoot(Menus menu, Map<Long, Menus> byId) {
        Long parentId = menu.getParentId();
        if (parentId == null || parentId == 0L) {
            return true;
        }
        if (!byId.containsKey(parentId)) {
            // 父节点不在集合里 —— 当根，而不是丢弃
            return true;
        }
        // 成环检测：沿着 parent 链往上走，若又回到自己，则成环
        Set<Long> visited = new HashSet<>();
        visited.add(menu.getId());
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (!visited.add(cursor)) {
                return true;
            }
            Menus parent = byId.get(cursor);
            if (parent == null) {
                return true;
            }
            cursor = parent.getParentId();
        }
        return false;
    }

    /**
     * 找到要挂载到的父节点ID
     *
     * <p>就是<b>直接父节点</b>：非根节点的直接父节点必然也在集合里
     * （否则 {@link #isRoot} 会把它判成根），所以不需要往上找。
     *
     * @param menu  当前菜单
     * @param byId  id → 菜单
     * @return 父节点ID；没有可挂载的父节点时返回 null
     */
    private static Long nearestAncestor(Menus menu, Map<Long, Menus> byId) {
        Long parentId = menu.getParentId();
        if (parentId == null || parentId == 0L) {
            return null;
        }
        return byId.containsKey(parentId) ? parentId : null;
    }

    private static int compareTree(Tree<Long> a, Tree<Long> b) {
        int byWeight = Integer.compare(weight(a), weight(b));
        if (byWeight != 0) {
            return byWeight;
        }
        return Long.compare(id(a), id(b));
    }

    private static void sortChildren(Tree<Long> node) {
        List<Tree<Long>> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        children.sort(MenuTreeBuilder::compareTree);
        for (Tree<Long> child : children) {
            sortChildren(child);
        }
    }

    private static int weight(Tree<Long> tree) {
        Object weight = tree.get("weight");
        return weight instanceof Number number ? number.intValue() : 0;
    }

    private static long id(Tree<Long> tree) {
        Object id = tree.get("id");
        return id instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 菜单实体 → Hutool 树节点（字段与 {@code MenusServiceImpl.convertToTreeNode} 保持一致）
     */
    private static TreeNode<Long> toTreeNode(Menus menu) {
        TreeNode<Long> node = new TreeNode<>();
        node.setId(menu.getId());
        node.setName(menu.getName());
        node.setParentId(menu.getParentId());
        node.setWeight(menu.getSort());

        Map<String, Object> extra = new HashMap<>();
        extra.put("routeName", menu.getRouteName());
        extra.put("icon", menu.getIcon());
        extra.put("path", menu.getPath());
        extra.put("component", menu.getComponent());
        extra.put("type", menu.getType());
        extra.put("hidden", menu.getHidden());
        extra.put("redirect", menu.getRedirect());
        extra.put("isLink", menu.getIsLink());

        node.setExtra(extra);
        return node;
    }
}
