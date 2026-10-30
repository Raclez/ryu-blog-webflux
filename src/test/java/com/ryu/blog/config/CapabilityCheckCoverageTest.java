package com.ryu.blog.config;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 能力校验（{@code @SaCheckPermission}）覆盖率护栏。
 *
 * <p><b>它守的是什么</b>：本项目里 {@code @SaCheckPermission} 只出现在
 * <b>四个 RBAC 控制器</b>上（users / roles / menus / permissions）。
 * 其余 161 个端点<b>一个都没有</b> —— 它们只被 {@code SaReactorFilter} 的
 * 「必须登录」挡着，于是<b>任何一个注册用户</b>都能：
 * 删除别人的文件、删改别人的文章、把评论审核通过、清空全部缓存、
 * 读服务器线程栈、读系统配置（含 AI Provider 的 API Key）、
 * 读存储策略（含对象存储的 AK/SK）……
 *
 * <p><b>为什么现在不直接补齐</b>：补齐的前提是
 * 「{@code t_permissions} 里有这条 identity，且 {@code is_active = 1}，
 * 且某个角色已经授予它」—— 三者缺一，加了注解就等于
 * <b>把这个端点对所有人锁死（包括管理员）</b>。
 * 实测开发库当前状态：
 * <ul>
 *   <li>{@code system:config:*} / {@code system:storage:*} —— <b>库里根本没有这两行</b>；</li>
 *   <li>{@code comment:*} / {@code content:category:view|add} /
 *       {@code content:tag:view|edit} / {@code monitor:*} —— {@code is_active = 0}，
 *       解析器过滤掉，等于没有。</li>
 * </ul>
 * 而修库需要执行迁移脚本，那是<b>运维的动作</b>，不该由测试偷偷改库。
 *
 * <p><b>所以本用例的作用是「让缺口可见、且不能悄悄变大」</b>：
 * 每一个没有能力校验的端点，都必须在 {@link #CAPABILITY_FREE} 里<b>逐个登记</b>，
 * 并写明它为什么可以没有、以及由哪个 Stage 负责补。
 * 新增端点时如果不写登记，本用例立刻失败 ——
 * 也就是<b>「漏加能力校验」从一个沉默的漏洞变成一个编译期可见的待办</b>。
 *
 * <p>随着 Stage 5/6/7/9 逐个补齐，对应条目会从登记表里删除。
 */
class CapabilityCheckCoverageTest {

    /**
     * 已登记的「没有能力校验」的端点。
     *
     * <p>key = {@code 控制器简名.方法名}；value = 原因说明。
     *
     * <p>登记的三类：
     * <ul>
     *   <li>{@code 公开接口} —— 匿名可访问（{@code SaReactorFilter} 白名单内）；</li>
     *   <li>{@code 自身数据} —— 只操作当前登录用户自己的数据，
     *       Service 层按登录用户过滤/校验归属；</li>
     *   <li>{@code 待补能力校验} —— 管理类端点，缺能力校验，
     *       由对应 Stage 补（括号里是负责的 Stage 与依赖的权限标识）。</li>
     * </ul>
     */
    private static final Map<String, String> CAPABILITY_FREE = new LinkedHashMap<>();

    static {
        // ---------- AuthController：登录/注册/验证码相关，本来就该公开 ----------
        CAPABILITY_FREE.put("AuthController.register", "公开接口：注册");
        CAPABILITY_FREE.put("AuthController.logout", "公开接口：登出");
        CAPABILITY_FREE.put("AuthController.getCaptcha", "公开接口：图形验证码");
        CAPABILITY_FREE.put("AuthController.checkUsername", "公开接口：注册前的用户名查重");
        CAPABILITY_FREE.put("AuthController.checkEmail", "公开接口：注册前的邮箱查重");

        // ---------- CacheController ----------
        CAPABILITY_FREE.put("CacheController.getCacheStats", "待补能力校验（Stage 9：monitor:log:view）");
        CAPABILITY_FREE.put("CacheController.clearCache", "待补能力校验（Stage 9：monitor:log:clean）");
        CAPABILITY_FREE.put("CacheController.clearAllCaches", "待补能力校验（Stage 9：monitor:log:clean）");
        CAPABILITY_FREE.put("CacheController.getCacheNames", "待补能力校验（Stage 9：monitor:log:view）");
        // 注：MonitorController / StorageConfigController / SysConfigController
        // 已经是类级 @SaCheckPermission（整类都要权限），不在登记表里。

        // ---------- AI 模块 ----------
        CAPABILITY_FREE.put("AiAssistController.assist", "自身数据：编辑器 AI 助手（Stage 7 收口）");
        CAPABILITY_FREE.put("AiAssistController.assistStream", "自身数据：编辑器 AI 助手（Stage 7 收口）");
        CAPABILITY_FREE.put("AiAssistController.actions", "自身数据：编辑器 AI 助手（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.generateBlogContent", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.generateBlogContentStream", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.refineContent", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.getGenerationHistory", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.getHistoryById", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.deleteHistory", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.regenerate", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.countGenerations", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.saveToDraft", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiBlogController.generateBlogContentAsync", "自身数据：AI 生成（Stage 7 收口）");
        CAPABILITY_FREE.put("AiQuotaController.getQuota", "自身数据：配额（Stage 7 收口）");
        CAPABILITY_FREE.put("AiQuotaController.getRemainingQuota", "自身数据：配额（Stage 7 收口）");
        CAPABILITY_FREE.put("AiQuotaController.getUsageStatistics", "自身数据：配额（Stage 7 收口）");
        CAPABILITY_FREE.put("AiQuotaController.getTodayUsage", "自身数据：配额（Stage 7 收口）");
        CAPABILITY_FREE.put("AiQuotaController.getMonthUsage", "自身数据：配额（Stage 7 收口）");
        CAPABILITY_FREE.put("AiQuotaController.getTotalCost", "自身数据：配额（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.getTemplates", "自身数据：模板（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.getTemplateById", "自身数据：模板（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.getTemplatesByType", "自身数据：模板（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.createTemplate", "自身数据：模板（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.updateTemplate", "自身数据：模板（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.deleteTemplate", "自身数据：模板（Stage 7 收口）");
        CAPABILITY_FREE.put("AiTemplateController.generateWithTemplate", "自身数据：模板（Stage 7 收口）");

        // ---------- CategoryController ----------
        // 管理类端点已补上 @SaCheckPermission（content:category:add|edit|remove|view）；
        // 只读的前台接口留在登记表里。
        CAPABILITY_FREE.put("CategoryController.getCategoryById", "公开只读：分类详情（分类本身是公开数据）");
        CAPABILITY_FREE.put("CategoryController.getAllCategories", "公开接口：前台分类下拉");
        CAPABILITY_FREE.put("CategoryController.getCategoriesByPage", "公开接口：前台分类分页");
        CAPABILITY_FREE.put("CategoryController.checkCategoryName", "公开接口：新增前的分类名查重");

        // ---------- CommentController ----------
        // 能力校验已补：comment:view（看别人的评论）、comment:approve（审核）。
        // 剩下这三个靠「归属」而不是「能力」把守 —— 评论属于评论作者：
        //   createComment  登录即可，但审核状态由服务端强制为「待审核」
        //   updateComment  只有作者本人（或管理员）能改，且改不到审核状态
        //   deleteComment  只有作者本人（或管理员）能删
        CAPABILITY_FREE.put("CommentController.createComment", "自身数据：登录后发表评论（审核状态由服务端强制）");
        CAPABILITY_FREE.put("CommentController.updateComment", "自身数据 + 归属校验：只有作者本人（或管理员）能改");
        CAPABILITY_FREE.put("CommentController.getCommentById", "公开只读：单条评论详情");
        CAPABILITY_FREE.put("CommentController.deleteComment", "自身数据 + 归属校验：只有作者本人（或管理员）能删");
        CAPABILITY_FREE.put("CommentController.getCommentsByArticleId", "公开接口：前台评论列表（校验文章可见性）");
        CAPABILITY_FREE.put("CommentController.getMyComments", "自身数据：我的评论");

        // ---------- FavoriteController ----------
        CAPABILITY_FREE.put("FavoriteController.toggleFavorite", "自身数据：收藏");
        CAPABILITY_FREE.put("FavoriteController.checkFavorited", "自身数据：收藏");
        CAPABILITY_FREE.put("FavoriteController.getFavoriteCount", "公开接口：收藏数");
        CAPABILITY_FREE.put("FavoriteController.getUserFavorites", "自身数据：收藏");
        CAPABILITY_FREE.put("FavoriteController.getUserFavoritesPaged", "自身数据：收藏");
        CAPABILITY_FREE.put("FavoriteController.batchGetFavoriteCounts", "公开接口：批量收藏数");

        // ---------- FileController ----------
        // Stage 6 起，18 个端点全部带 @SaCheckPermission：
        //   system:file:upload  上传 / 批量上传 / 分片三件套
        //   system:file:remove  删除 / 批量删除
        //   system:file:view    下载 / 预览 / 预览下载链接 / 详情 / 版本 / 分组 / 搜索 / 批量链接
        //   system:file:edit    改文件信息
        // 归属（creatorId）由 FileAccessPolicy 在服务层把守，
        // 所以这里不需要再登记为「无能力校验」。
        // 留一条说明，避免后人误以为这里漏了。
        // 唯一例外是公开直链：它<b>本来就要匿名可访问</b>（<img src> 带不了
        // Authorization 头），可见性由 FileDownloadService 按 access_type 判定。
        CAPABILITY_FREE.put("FileController.publicFile", "公开接口：公开文件直链（应用内再判 access_type=0）");

        // ---------- MenusController ----------
        CAPABILITY_FREE.put("MenusController.getMyMenus", "自身数据：我的菜单");
        CAPABILITY_FREE.put("MenusController.getUserMenus", "自身数据：指定用户的菜单（登录用户可读）");

        // ---------- RoleController ----------
        CAPABILITY_FREE.put("RoleController.getDefaultRole", "公开信息：默认角色（只读，登录即可）");

        // ---------- UserController ----------
        CAPABILITY_FREE.put("UserController.getCurrentUser", "自身数据：当前登录用户的信息");
        CAPABILITY_FREE.put("UserController.updatePassword", "自身数据：改自己的密码");

        // ---------- MonitorController ----------
        // （类级 @SaCheckPermission(monitor:server:view)，整类都要权限，不登记）

        // ---------- PostsController ----------
        // 管理类端点已补上 @SaCheckPermission（content:post:*），且这 6 条 identity
        // 在开发库里 is_active = 1 且已授予 ADMIN 角色（实测 2026-10-29）。
        // 公开接口（前台列表/详情/相关推荐）留在登记表里。
        CAPABILITY_FREE.put("PostsController.getFrontPosts", "公开接口：前台文章列表");
        CAPABILITY_FREE.put("PostsController.getPostDetail", "公开接口：前台文章详情");
        CAPABILITY_FREE.put("PostsController.getRelatedPosts", "公开接口：相关文章");

        // ---------- PostVersionController ----------
        // 全部端点已补上 @SaCheckPermission（读 content:post:view / 写 content:post:edit），
        // 且 Service 层还有文章可见性 / 归属校验。

        // ---------- StorageConfigController ----------
        // （类级 @SaCheckPermission(system:storage:view)，整类都要权限，不登记）

        // ---------- SysConfigController ----------
        // （类级 @SaCheckPermission(system:config:view)，整类都要权限，不登记）

        // ---------- SysDictItemController ----------
        CAPABILITY_FREE.put("SysDictItemController.getSysDictItem", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictItemController.getDictItemById", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictItemController.getDictItemsByTypeId", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictItemController.getDictItemsByType", "公开接口：按字典类型取值");
        CAPABILITY_FREE.put("SysDictItemController.batchGetDictItems", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictItemController.batchGetDictItemsByCode", "公开接口：按字典编码批量取值");
        CAPABILITY_FREE.put("SysDictItemController.getDictItemValue", "公开接口：取字典值");
        CAPABILITY_FREE.put("SysDictItemController.getDictItemValueByCode", "公开接口：取字典值");
        CAPABILITY_FREE.put("SysDictItemController.createDictItem", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictItemController.updateDictItem", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictItemController.editStatus", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictItemController.deleteDictItem", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictItemController.clearCache", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictItemController.refreshCache", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictItemController.refreshCacheByCode", "待补能力校验（Stage 9：system:config:edit）");

        // ---------- SysDictTypeController ----------
        CAPABILITY_FREE.put("SysDictTypeController.getDictTypeById", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictTypeController.getDictTypeByCode", "公开接口：按字典编码取值");
        CAPABILITY_FREE.put("SysDictTypeController.getSysDictTypePage", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictTypeController.getAllSysDictTypes", "待补能力校验（Stage 9：system:config:view）");
        CAPABILITY_FREE.put("SysDictTypeController.createDictType", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictTypeController.updateDictType", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictTypeController.deleteDictType", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictTypeController.batchDeleteSysDictType", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictTypeController.clearCache", "待补能力校验（Stage 9：system:config:edit）");
        CAPABILITY_FREE.put("SysDictTypeController.refreshCache", "待补能力校验（Stage 9：system:config:edit）");

        // ---------- TagController ----------
        // 管理类端点已补上 @SaCheckPermission（content:tag:add|edit|remove）；
        // 只读的前台接口留在登记表里。
        CAPABILITY_FREE.put("TagController.getTagById", "公开只读：标签详情（标签本身是公开数据）");
        CAPABILITY_FREE.put("TagController.getAllTags", "公开接口：前台标签下拉");
        CAPABILITY_FREE.put("TagController.getTagsByArticleId", "公开接口：文章标签");
        CAPABILITY_FREE.put("TagController.checkTagName", "公开接口：新增前的标签名查重");
        CAPABILITY_FREE.put("TagController.getHotTags", "公开接口：热门标签");
        CAPABILITY_FREE.put("TagController.getTagByPage", "公开接口：前台标签分页");

        // ---------- TaskController ----------
        CAPABILITY_FREE.put("TaskController.submitTask", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.getTaskStatus", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.getTaskResult", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.cancelTask", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.retryTask", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.getUserTasks", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.getOfflineNotifications", "自身数据：任务队列");
        CAPABILITY_FREE.put("TaskController.clearOfflineNotifications", "自身数据：任务队列");

        // ---------- ViewHistoryController ----------
        // 注意：getViewHistoryStats **不在**这张表里 —— 它统计的是全站而不是当前用户，
        // 已挂 @SaCheckPermission(monitor:stats:view)。
        CAPABILITY_FREE.put("ViewHistoryController.addViewHistory", "自身数据：浏览历史（登录即可，userId 取自登录态）");
        CAPABILITY_FREE.put("ViewHistoryController.getUserViewHistory", "自身数据：浏览历史");
        CAPABILITY_FREE.put("ViewHistoryController.getUserViewHistoryPaged", "自身数据：浏览历史");
        CAPABILITY_FREE.put("ViewHistoryController.getArticleViewCount", "公开接口：文章浏览数");
        CAPABILITY_FREE.put("ViewHistoryController.batchGetArticleViewCounts", "公开接口：批量浏览数");
        CAPABILITY_FREE.put("ViewHistoryController.clearUserViewHistory", "自身数据：浏览历史");
    }

    @Test
    @DisplayName("每个端点要么有能力校验，要么在「无能力校验登记表」里登记过")
    void everyEndpointIsEitherProtectedOrRegistered() {
        List<Class<?>> controllers = scanControllers();
        assertFalse(controllers.isEmpty(), "没有扫描到任何 Controller，扫描逻辑坏了");

        List<String> unregistered = new ArrayList<>();
        List<String> stale = new ArrayList<>();

        Set<String> seen = new java.util.HashSet<>();
        // 扫描到的控制器类名 —— 用来区分「端点被删了」和「整个控制器没被注册」
        Set<String> scannedControllers = new java.util.HashSet<>();

        for (Class<?> controller : controllers) {
            scannedControllers.add(controller.getSimpleName());
            for (Method method : controller.getDeclaredMethods()) {
                if (!isMapped(method)) {
                    continue;
                }
                String key = controller.getSimpleName() + "." + method.getName();
                seen.add(key);

                if (hasCapabilityCheck(controller, method)) {
                    // 已经加了能力校验 → 不该再留在登记表里
                    if (CAPABILITY_FREE.containsKey(key)) {
                        stale.add(key + "（已有能力校验，应从登记表删除）");
                    }
                    continue;
                }
                if (!CAPABILITY_FREE.containsKey(key)) {
                    unregistered.add(key);
                }
            }
        }

        // 登记表里有、但控制器里已经没有的条目 —— 说明端点被删了/改名了，登记要跟着更新。
        //
        // 注意：只有「控制器本身被扫描到了」才算过期。AI 那几个控制器挂着
        // @ConditionalOnAiEnabled —— 扫描器（ClassPathScanningCandidateComponentProvider）
        // 会在扫描阶段就评估 @Conditional，blog.ai.enabled=false 时它们根本不会进扫描结果。
        // 那种情况不是「登记过期」，只是这次没注册而已。
        for (String key : CAPABILITY_FREE.keySet()) {
            String controllerName = key.substring(0, key.indexOf('.'));
            if (!seen.contains(key) && scannedControllers.contains(controllerName)) {
                stale.add(key + "（控制器里已经没有这个端点，登记应删除）");
            }
        }

        if (!unregistered.isEmpty()) {
            fail("以下端点既没有 @SaCheckPermission，也没有登记在 CapabilityCheckCoverageTest"
                    + " 的登记表里 —— 新增端点时必须二选一：" + unregistered);
        }
        if (!stale.isEmpty()) {
            fail("登记表的条目与实际代码对不上：" + stale);
        }
    }

    @Test
    @DisplayName("登记表里的每一条都写明了原因")
    void everyRegisteredEndpointHasAReason() {
        for (Map.Entry<String, String> entry : CAPABILITY_FREE.entrySet()) {
            String reason = entry.getValue();
            if (reason == null || reason.isBlank()) {
                fail(entry.getKey() + " 没有写明为什么可以没有能力校验");
            }
        }
    }

    @Test
    @DisplayName("护栏本身不是空转：扫到了足够的控制器，登记表也不是空的")
    void guardIsNotVacuous() {
        List<Class<?>> controllers = scanControllers();
        assertTrue(controllers.size() >= 15,
                "只扫到 " + controllers.size() + " 个控制器，扫描范围不对，护栏等于没生效");
        assertTrue(CAPABILITY_FREE.size() >= 100,
                "登记表只有 " + CAPABILITY_FREE.size() + " 条，缺口清单不完整");
    }

    /**
     * 判断方法是不是一个对外暴露的 HTTP 端点
     *
     * <p>注意：{@code @GetMapping} 等是<b>元注解</b>（本身被 {@code @RequestMapping} 标注），
     * 直接用 {@code getAnnotations()} 看不到，得用
     * {@link AnnotatedElementUtils#findMergedAnnotation} 做合并查找。
     */
    private boolean isMapped(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null;
    }

    /**
     * 判断端点是否已有能力校验（方法级或类级）
     */
    private boolean hasCapabilityCheck(Class<?> controller, Method method) {
        if (method.isAnnotationPresent(SaCheckPermission.class)) {
            return true;
        }
        if (controller.isAnnotationPresent(SaCheckPermission.class)) {
            return true;
        }
        // @SaIgnore 是显式声明「这个端点不走鉴权」，同样算已处理
        return method.isAnnotationPresent(SaIgnore.class)
                || controller.isAnnotationPresent(SaIgnore.class);
    }

    private List<Class<?>> scanControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        Set<BeanDefinition> candidates =
                scanner.findCandidateComponents("com.ryu.blog.controller");
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : candidates) {
            try {
                Class<?> clazz = Class.forName(definition.getBeanClassName());
                if (clazz.isAnnotationPresent(RestController.class) || hasRequestMapping(clazz)) {
                    result.add(clazz);
                }
            } catch (ClassNotFoundException ignored) {
                // 忽略无法加载的类
            }
        }
        return result;
    }

    private boolean hasRequestMapping(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(RequestMapping.class)) {
                return true;
            }
        }
        return clazz.getAnnotation(RequestMapping.class) != null;
    }
}
