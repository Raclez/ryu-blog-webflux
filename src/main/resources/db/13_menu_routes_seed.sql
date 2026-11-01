-- =============================================================================
-- 13_menu_routes_seed.sql — 按前端路由清单重建菜单树（幂等，不物理删除）
--
-- 背景：
--   切到「后端驱动菜单」后，路由表就是 t_menus —— 菜单缺一行，对应页面在前端
--   就直接不可达（不是 404，是路由根本不存在）。旧种子是另一套结构（个人中心 /
--   数据统计 / 评论管理 / 文档中心，组件路径 'system/user/index' 对不上任何真实文件），
--   必须整树替换。这里按 apps/web-ele/src/router/routes/modules/*.ts 的静态路由重建。
--
-- 幂等与安全：
--   - 新菜单按固定 id UPSERT，重复执行结果一致；
--   - 旧种子行不物理删除，只置 is_deleted = 1（MenusRepository 的查询都带
--     is_deleted = 0）；需要回滚时把这几行改回 0 即可；
--   - 绑定同理：指向已退役菜单的绑定置 is_deleted = 1，新绑定 UPSERT。
--
-- 字段约定：
--   route_name  vue-router 的 name，ASCII 且全局唯一（uk_menus_route_name）
--   component   相对 src 的路径，前端 normalizeViewPath 会补前导斜杠；
--               目录行为 NULL（由前端套 BasicLayout）
--   icon        iconify 名（VbenIcon 直接渲染字符串）
--   hidden      1 = 不进侧边栏但保留路由（编辑器、详情页这类必须可达）
-- =============================================================================

-- 1. 菜单树 UPSERT
INSERT INTO `t_menus`
    (`id`, `name`, `route_name`, `path`, `component`, `type`, `parent_id`, `sort`,
     `hidden`, `icon`, `create_time`, `update_time`, `redirect`, `is_link`, `is_deleted`)
VALUES
    (1,   '系统管理',   'SystemManagement',   '/system-management',      NULL, 0, 0,   500,  0, 'lucide:settings',        NOW(), NOW(), NULL, 0, 0),
    (101, '任务管理',   'TaskManagement',     '/system/task',            'views/features/task/TaskManagementPage.vue', 1, 1, 1, 0, 'lucide:list', NOW(), NOW(), NULL, 0, 0),
    (102, '字典管理',   'Dict',               '/dict',                   NULL, 0, 1,   2,    0, 'lucide:book',            NOW(), NOW(), NULL, 0, 0),
    (111, '字典类型',   'DictType',           '/dict/type',              'views/features/dict/type/DictTypeList.vue', 1, 102, 1, 0, 'lucide:book-open', NOW(), NOW(), NULL, 0, 0),
    (112, '字典数据',   'DictItem',           '/dict/item',              'views/features/dict/item/DictItemList.vue', 1, 102, 2, 0, 'lucide:list-checks', NOW(), NOW(), NULL, 0, 0),
    (103, '系统配置',   'SystemConfig',       '/system-config/form',     'views/features/system-config/SystemSettings.vue', 1, 1, 3, 0, 'lucide:settings-2', NOW(), NOW(), NULL, 0, 0),
    (104, '系统监控',   'SystemMonitor',      '/system/monitor',         'views/features/system/SystemLog.vue', 1, 1, 4, 0, 'lucide:activity', NOW(), NOW(), NULL, 0, 0),
    (105, 'SpringBoot监控', 'SpringBootAdmin', '/system/springboot-admin', 'views/features/system/SpringBootAdmin.vue', 1, 1, 5, 0, 'lucide:server', NOW(), NOW(), NULL, 0, 0),

    (2,   '内容管理',   'Content',            '/content',                NULL, 0, 0,   1000, 0, 'lucide:file-text',       NOW(), NOW(), NULL, 0, 0),
    (201, '文章管理',   'ArticleList',        '/article/list',           'views/features/article/ArticleList.vue', 1, 2, 1, 0, 'lucide:newspaper', NOW(), NOW(), NULL, 0, 0),
    (202, '文章编辑',   'ArticleEditor',      '/article/edit/:id?',      'views/features/article/ArticleEditor.vue', 1, 2, 2, 1, 'lucide:pen-line', NOW(), NOW(), NULL, 0, 0),
    (203, '版本历史',   'ArticleHistory',     '/article/history/:id',    'views/features/article/ArticleHistory.vue', 1, 2, 3, 1, 'lucide:history', NOW(), NOW(), NULL, 0, 0),
    (204, '分类管理',   'CategoryList',       '/category/list',          'views/features/category/CategoryList.vue', 1, 2, 4, 0, 'lucide:folder-tree', NOW(), NOW(), NULL, 0, 0),
    (205, '标签管理',   'TagList',            '/tag/list',               'views/features/tag/TagList.vue', 1, 2, 5, 0, 'lucide:tags', NOW(), NOW(), NULL, 0, 0),

    (3,   '权限管理',   'Authority',          '/authority',              NULL, 0, 0,   1001, 0, 'lucide:shield',          NOW(), NOW(), NULL, 0, 0),
    (301, '角色管理',   'Role',               '/role/list',              'views/features/role/RoleList.vue', 1, 3, 1, 0, 'lucide:users', NOW(), NOW(), NULL, 0, 0),
    (302, '菜单管理',   'Menu',               '/menu/list',              'views/features/menu/MenuList.vue', 1, 3, 2, 0, 'lucide:menu', NOW(), NOW(), NULL, 0, 0),
    (303, '权限管理',   'Permission',         '/permission/list',        'views/features/permission/PermissionList.vue', 1, 3, 3, 0, 'lucide:key-round', NOW(), NOW(), NULL, 0, 0),
    (304, '用户管理',   'UserList',           '/user/list',              'views/features/user/UserList.vue', 1, 3, 4, 0, 'lucide:user', NOW(), NOW(), NULL, 0, 0),
    (305, '浏览历史',   'ViewHistoryList',    '/authority/list',         'views/features/view-history/ViewHistoryList.vue', 1, 3, 5, 0, 'lucide:eye', NOW(), NOW(), NULL, 0, 0),

    (4,   '资源管理',   'ResourceManagement', '/resource-management',    NULL, 0, 0,   1002, 0, 'lucide:hard-drive',      NOW(), NOW(), NULL, 0, 0),
    (401, '资源列表',   'ResourceList',       '/resource/list',          'views/features/resource/ResourceList.vue', 1, 4, 1, 0, 'lucide:image', NOW(), NOW(), NULL, 0, 0),
    (402, '网盘管理',   'DiskManager',        '/disk/files',             'views/features/disk/DiskManager.vue', 1, 4, 2, 0, 'lucide:cloud', NOW(), NOW(), NULL, 0, 0),

    (5,   '爬虫管理',   'CrawlerManagement',  '/crawler',                NULL, 0, 0,   1003, 0, 'lucide:bug',             NOW(), NOW(), NULL, 0, 0),
    (501, '图片采集',   'PictureCrawler',     '/crawler/picture',        'views/features/crawler/PictureCrawler.vue', 1, 5, 1, 0, 'lucide:camera', NOW(), NOW(), NULL, 0, 0),

    (6,   'Arthas工具', 'ArthasTools',        '/arthas',                 NULL, 0, 0,   2000, 0, 'lucide:terminal',        NOW(), NOW(), NULL, 0, 0),
    (601, 'Arthas管理', 'ArthasManager',      '/arthas/index',           'views/features/arthas/index.vue', 1, 6, 1, 0, 'lucide:monitor', NOW(), NOW(), NULL, 0, 0),
    (602, '模块功能',   'ArthasModule',       '/arthas/module',          'views/features/arthas/ArthasModule.vue', 1, 6, 2, 1, 'lucide:boxes', NOW(), NOW(), NULL, 0, 0),
    (603, '命令控制台', 'ArthasConsole',      '/arthas/console',         'views/features/arthas/ArthasConsole.vue', 1, 6, 3, 1, 'lucide:terminal-square', NOW(), NOW(), NULL, 0, 0),

    (7,   '接口管理',   'Api',                '/api',                    NULL, 0, 0,   2001, 0, 'lucide:plug',            NOW(), NOW(), NULL, 0, 0),
    (701, 'API文档',    'ApiDoc',             '/api-doc',                'views/api-doc/index.vue', 1, 7, 1, 0, 'lucide:file-json', NOW(), NOW(), NULL, 0, 0),

    (8,   '首页',       'Dashboard',          '/dashboard',              'views/dashboard/blog-dashboard/index.vue', 1, 0, 200, 1, 'lucide:home', NOW(), NOW(), NULL, 0, 0)
ON DUPLICATE KEY UPDATE
    `name`        = VALUES(`name`),
    `route_name`  = VALUES(`route_name`),
    `path`        = VALUES(`path`),
    `component`   = VALUES(`component`),
    `type`        = VALUES(`type`),
    `parent_id`   = VALUES(`parent_id`),
    `sort`        = VALUES(`sort`),
    `hidden`      = VALUES(`hidden`),
    `icon`        = VALUES(`icon`),
    `is_deleted`  = 0,
    `update_time` = NOW();

-- 2. 退役旧种子行（用户/角色/菜单/权限管理、文章/分类/标签/评论、个人中心、数据统计、文档中心）
--    软删而不是物理删：回滚只需把这些行的 is_deleted 改回 0。
--    ⚠️ 不要包含 5/6/7/8 —— 这 4 个 id 被新树的「爬虫管理 / Arthas / 接口管理 / 首页」复用，
--       上一版把复用的行一起软删了，结果 4 个一级目录连同它们的子页从侧边栏消失。
UPDATE `t_menus` SET `is_deleted` = 1, `update_time` = NOW()
 WHERE `id` IN (9, 10, 11, 12, 13, 14, 15, 16, 24);

-- 3. 绑定：先让指向已退役菜单的绑定失效，再 UPSERT 新的
UPDATE `t_sys_role_menus` SET `is_deleted` = 1, `update_time` = NOW()
 WHERE `menu_id` NOT IN (
    1, 101, 102, 111, 112, 103, 104, 105,
    2, 201, 202, 203, 204, 205,
    3, 301, 302, 303, 304, 305,
    4, 401, 402, 5, 501, 6, 601, 602, 603,
    7, 701, 8);

INSERT INTO `t_sys_role_menus` (`role_id`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, m.`id`, NOW(), NOW(), 0
FROM `t_roles` r
CROSS JOIN `t_menus` m
WHERE r.`code` = 'ADMIN' AND m.`is_deleted` = 0
ON DUPLICATE KEY UPDATE `is_deleted` = 0, `update_time` = NOW();

INSERT INTO `t_sys_role_menus` (`role_id`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, m.`id`, NOW(), NOW(), 0
FROM `t_roles` r
CROSS JOIN `t_menus` m
WHERE r.`code` = 'USER'
  AND m.`is_deleted` = 0
  -- 普通用户：首页 + 资源管理（资源列表 / 网盘）
  AND m.`id` IN (8, 4, 401, 402)
ON DUPLICATE KEY UPDATE `is_deleted` = 0, `update_time` = NOW();

-- 3b. USER 的历史绑定里可能有「刚好 id 被复用」的项（旧 id=3 曾是个人中心，现在是权限管理），
--     它不是上面那条 NOT IN 能清掉的 —— 按 USER 的意图集合再收一次。
UPDATE `t_sys_role_menus` rm
  JOIN `t_roles` r ON r.`id` = rm.`role_id`
   SET rm.`is_deleted` = 1, rm.`update_time` = NOW()
 WHERE r.`code` = 'USER'
   AND rm.`menu_id` NOT IN (8, 4, 401, 402);

-- 4. 自增从固定 id 之后继续，避免管理端新建菜单时撞号
ALTER TABLE `t_menus` AUTO_INCREMENT = 900;
