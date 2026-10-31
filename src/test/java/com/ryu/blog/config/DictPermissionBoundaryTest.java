package com.ryu.blog.config;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ryu.blog.controller.SysDictItemController;
import com.ryu.blog.controller.SysDictTypeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典端点的权限边界护栏
 *
 * <p>字典同时服务两类人：<b>管理员</b>（增删改、看全量）和<b>普通作者</b>
 * （文章编辑器按编码取「访问权限」「许可协议」的选项）。二者的口径必须分开：
 * <ul>
 *   <li>渲染只读端点 {@code /sysDictItem/type/{code}} 等 <b>不能</b>加权限注解 ——
 *       加了普通作者就打不开文章编辑器（他们只有 system:user:edit / file:*）；</li>
 *   <li>管理端点必须加，否则任何注册用户都能改字典；</li>
 *   <li>两个控制器都<b>不能</b>加类级注解 —— 类级会被 SaPermissionAspect
 *       合并到每个方法上，等于把渲染路径一起锁死。</li>
 * </ul>
 *
 * @author ryu
 */
class DictPermissionBoundaryTest {

    /** 渲染用只读：普通作者必须能拿到 */
    private static final List<String> RENDER_ONLY_ENDPOINTS = List.of(
            "getDictItemsByType",
            "batchGetDictItemsByCode",
            "getDictItemValue",
            "getDictItemValueByCode");

    /** 字典类型侧唯一一条渲染只读 */
    private static final String RENDER_ONLY_TYPE_ENDPOINT = "getDictTypeByCode";

    private static Method method(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    @Test
    @DisplayName("渲染只读端点不得带权限注解")
    void renderEndpointsStayPermissionFree() {
        for (String name : RENDER_ONLY_ENDPOINTS) {
            Method m = method(SysDictItemController.class, name);
            assertNotNull(m, "找不到端点方法: " + name);
            assertNull(m.getAnnotation(SaCheckPermission.class),
                    "渲染只读端点不能加 @SaCheckPermission，否则普通作者拿不到字典: " + name);
        }

        Method typeByCode = method(SysDictTypeController.class, RENDER_ONLY_TYPE_ENDPOINT);
        assertNotNull(typeByCode);
        assertNull(typeByCode.getAnnotation(SaCheckPermission.class),
                "getDictTypeByCode 是渲染路径，不能加权限");
    }

    @Test
    @DisplayName("管理端点必须带权限注解")
    void adminEndpointsAreGuarded() {
        String[][] itemEndpoints = {
                {"getSysDictItem", "view"},
                {"getDictItemById", "view"},
                {"getDictItemsByTypeId", "view"},
                {"batchGetDictItems", "view"},
                {"createDictItem", "edit"},
                {"updateDictItem", "edit"},
                {"editStatus", "edit"},
                {"deleteDictItem", "edit"},
                {"clearCache", "edit"},
                {"refreshCache", "edit"},
                {"refreshCacheByCode", "edit"},
        };
        for (String[] pair : itemEndpoints) {
            Method m = method(SysDictItemController.class, pair[0]);
            assertNotNull(m, "找不到端点方法: " + pair[0]);
            SaCheckPermission ann = m.getAnnotation(SaCheckPermission.class);
            assertNotNull(ann, "管理端点缺少 @SaCheckPermission: " + pair[0]);
            assertTrue(ann.value()[0].startsWith("system:config:"),
                    "权限标识应落在 system:config:* 下: " + pair[0]);
        }

        String[] typeEndpoints = {
                "getDictTypeById", "getSysDictTypePage", "getAllSysDictTypes",
                "createDictType", "updateDictType", "deleteDictType",
                "batchDeleteSysDictType", "clearCache", "refreshCache",
        };
        for (String name : typeEndpoints) {
            Method m = method(SysDictTypeController.class, name);
            assertNotNull(m, "找不到端点方法: " + name);
            assertNotNull(m.getAnnotation(SaCheckPermission.class),
                    "管理端点缺少 @SaCheckPermission: " + name);
        }
    }

    @Test
    @DisplayName("两个控制器都不得加类级权限注解")
    void noClassLevelPermission() {
        // 类级注解会被 SaPermissionAspect 合并到渲染路径上
        assertFalse(AnnotatedElementUtils.hasAnnotation(SysDictItemController.class, SaCheckPermission.class),
                "SysDictItemController 不能有类级 @SaCheckPermission");
        assertFalse(AnnotatedElementUtils.hasAnnotation(SysDictTypeController.class, SaCheckPermission.class),
                "SysDictTypeController 不能有类级 @SaCheckPermission");
    }
}
