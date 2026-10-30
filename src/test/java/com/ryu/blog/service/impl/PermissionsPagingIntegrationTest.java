package com.ryu.blog.service.impl;

import com.ryu.blog.dto.PermissionsQueryDTO;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限分页查询的集成测试 —— 验证「过滤下推到数据库」之后的<b>结果与改造前完全等价</b>。
 *
 * <p><b>为什么必须连真库</b>：改造前这个接口是
 * 「{@code findByIsDeletedOrderByIdAsc(0)} 全表拉出来 → 内存 filter → 内存分页」，
 * 改造后是「{@code Criteria} 拼 WHERE → 数据库过滤 + LIMIT/OFFSET」。
 * 两条路的语义必须一模一样（否则就是悄悄改了接口行为），而这一点
 * 只有真库能证明：mock 掉 repository 的话，两条路都「能返回数据」，测不出差别。
 *
 * <p><b>只读</b>：本测试<b>不写库</b>。期望值全部由 repository 的现有查询算出来，
 * 再和新接口的返回值逐项比对 —— 等于把「旧实现」当作 oracle。
 *
 * @author ryu
 */
@SpringBootTest
class PermissionsPagingIntegrationTest {

    @Autowired
    private PermissionsServiceImpl permissionsService;

    @Autowired
    private PermissionsRepository permissionsRepository;

    @Test
    @DisplayName("不带过滤条件：total 与条数都与 repository 的全量查询一致")
    void noFilterMatchesRepositoryCount() {
        Long expectedTotal = permissionsRepository.countByIsDeleted(0).block();
        assertNotNull(expectedTotal);

        PermissionsQueryDTO query = new PermissionsQueryDTO();
        query.setCurrentPage(1L);
        query.setPageSize(20L);

        PageResult<Permissions> page = permissionsService.getPermissionsByPage(query).block();

        assertNotNull(page);
        assertEquals(expectedTotal, page.getTotal());
        assertEquals((int) Math.min(20L, expectedTotal), page.getRecords().size());
        // 排序按 id 升序
        assertEquals(page.getRecords().stream().map(Permissions::getId).sorted().toList(),
                page.getRecords().stream().map(Permissions::getId).toList());
    }

    @Test
    @DisplayName("按名称模糊查询：结果与旧的「内存过滤」实现逐条一致")
    void nameFilterMatchesLegacyInMemoryFilter() {
        // 旧实现的语义：内存里 contains(name)，再按 id 升序切页
        String keyword = "user";
        List<Permissions> legacyAll = permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                .filter(p -> p.getName() != null && p.getName().contains(keyword))
                .collectList()
                .block();

        assertNotNull(legacyAll);

        PermissionsQueryDTO query = new PermissionsQueryDTO();
        query.setName(keyword);
        query.setCurrentPage(1L);
        query.setPageSize(10L);

        PageResult<Permissions> page = permissionsService.getPermissionsByPage(query).block();

        assertNotNull(page);
        assertEquals((long) legacyAll.size(), page.getTotal(),
                "数据库侧过滤的条数必须等于旧实现内存过滤的条数");
        assertEquals(legacyAll.stream().limit(10).map(Permissions::getId).toList(),
                page.getRecords().stream().map(Permissions::getId).toList(),
                "第 1 页的内容与顺序必须与旧实现一致");
    }

    @Test
    @DisplayName("按标识模糊查询：结果与旧的「内存过滤」实现逐条一致")
    void identityFilterMatchesLegacyInMemoryFilter() {
        String keyword = "system";
        List<Permissions> legacyAll = permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                .filter(p -> p.getIdentity() != null && p.getIdentity().contains(keyword))
                .collectList()
                .block();

        assertNotNull(legacyAll);

        PermissionsQueryDTO query = new PermissionsQueryDTO();
        query.setIdentity(keyword);
        query.setCurrentPage(1L);
        query.setPageSize(5L);

        PageResult<Permissions> page = permissionsService.getPermissionsByPage(query).block();

        assertNotNull(page);
        assertEquals((long) legacyAll.size(), page.getTotal());
        assertEquals(legacyAll.stream().limit(5).map(Permissions::getId).toList(),
                page.getRecords().stream().map(Permissions::getId).toList());
    }

    @Test
    @DisplayName("按模块前缀查询：与 repository 的前缀查询一致")
    void modulePrefixMatchesRepositoryPrefixQuery() {
        String prefix = "system";
        Long expectedTotal = permissionsRepository
                .countByIdentityStartingWithAndIsDeleted(prefix + ":", 0).block();
        assertNotNull(expectedTotal);

        PermissionsQueryDTO query = new PermissionsQueryDTO();
        query.setModulePrefix(prefix);
        query.setCurrentPage(1L);
        query.setPageSize(50L);

        PageResult<Permissions> page = permissionsService.getPermissionsByPage(query).block();

        assertNotNull(page);
        assertEquals(expectedTotal, page.getTotal());
        page.getRecords().forEach(p -> assertTrue(p.getIdentity().startsWith(prefix + ":"),
                "modulePrefix 必须按 identity 前缀过滤: " + p.getIdentity()));
    }

    @Test
    @DisplayName("翻页：第 2 页的内容与旧实现的切片完全一致")
    void secondPageMatchesLegacySlice() {
        List<Permissions> legacyAll = permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                .collectList()
                .block();
        assertNotNull(legacyAll);

        long pageSize = 10L;
        PermissionsQueryDTO query = new PermissionsQueryDTO();
        query.setCurrentPage(2L);
        query.setPageSize(pageSize);

        PageResult<Permissions> page = permissionsService.getPermissionsByPage(query).block();

        assertNotNull(page);
        long from = Math.min(pageSize, legacyAll.size());
        long to = Math.min(from + pageSize, legacyAll.size());
        assertEquals(legacyAll.subList((int) from, (int) to).stream().map(Permissions::getId).toList(),
                page.getRecords().stream().map(Permissions::getId).toList(),
                "第 2 页必须等于旧实现切片的 [10, 20)");
        assertEquals((long) legacyAll.size(), page.getTotal());
    }
}
