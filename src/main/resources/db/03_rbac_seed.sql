-- =============================================================================
-- 03_rbac_seed.sql — RBAC 基础数据（幂等，可重复执行）
--
-- 背景：RBAC 这套表、实体、Service 一直都在，但**从来没有种子数据**：
--   - schema.sql 里 0 条 INSERT → 全新部署的库一个角色、一条权限都没有；
--   - 开发库 3 个角色的 is_default 全是 NULL → getDefaultRole() 恒空
--     → 注册出来的用户永远没有任何角色；
--   - t_sys_role_menus 是孤儿表（无实体、无代码）→ 菜单可见性模型改为
--     role → menu 直连后，它才真正开始工作。
--
-- 【已下线】t_menu_permissions（菜单 → 权限关联）连同 MenuPermission 实体 /
-- MenuPermissionRepository 一并删除。可见性由 t_sys_role_menus 承担，
-- 按钮级权限由前端按 permission identity 控制显隐。
-- 与之配套：type=2 的「按钮菜单」也不再有意义（见 4a）—— 菜单不再承载权限。
--
-- 幂等性依赖（这四条唯一键都由 apply-ddl.sh 保证存在 —— 缺任何一条，
-- 下面的 ON DUPLICATE KEY UPDATE 都不会触发，脚本每跑一次就多一份重复行）：
--   - t_roles.uk_code
--   - t_permissions.uk_perm_identity
--   - t_role_permissions.uk_role_permission
--   - t_sys_role_menus.uk_role_menu
--
-- ⚠️ 顺序：先跑 `bash apply-ddl.sh`（它自己会调本文件），
--    **不要单独跑本文件** —— 唯一键还没建时会报 Duplicate entry。
--
-- MySQL 8.0 不支持 `INSERT ... ON DUPLICATE KEY UPDATE` 之外的 upsert 变体，
-- 但 ON DUPLICATE KEY UPDATE 是标准语法，这里直接用。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 清理垃圾数据：system:role:tttt 是压测时手写的测试权限
-- -----------------------------------------------------------------------------
DELETE FROM `t_permissions` WHERE `identity` = 'system:role:tttt';

-- -----------------------------------------------------------------------------
-- 2. 角色
--
--    is_default 的语义是「新注册用户自动分配的角色」——
--    所以只有普通用户 USER 是默认角色，ADMIN 不是。
--    否则新注册的用户一上来就是管理员。
-- -----------------------------------------------------------------------------
INSERT INTO `t_roles`
    (`id`, `name`, `code`, `description`, `sort`, `is_active`, `is_default`,
     `create_time`, `update_time`, `is_deleted`)
VALUES
    (1, '管理员',   'ADMIN', '系统管理员', 1, 1, 0, NOW(), NOW(), 0),
    (2, '普通用户', 'USER',  '普通用户',   2, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    `name`        = VALUES(`name`),
    `description` = VALUES(`description`),
    `sort`        = VALUES(`sort`),
    `is_active`   = VALUES(`is_active`),
    `is_default`  = VALUES(`is_default`),
    `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 3. 权限
--
--    identity 是 @SaCheckPermission 的比对依据，**必须唯一**。
--    注意：is_active / is_deleted 在冲突时**不覆盖** ——
--    管理员手工停用的权限，不该被种子脚本重新打开。
-- -----------------------------------------------------------------------------
INSERT INTO `t_permissions`
    (`name`, `identity`, `is_active`, `description`, `is_deleted`, `create_time`, `update_time`)
VALUES
    -- 用户模块
    ('查看用户',       'system:user:view',      1, '允许查看用户列表和详情',       0, NOW(), NOW()),
    ('添加用户',       'system:user:add',       1, '允许创建新用户',               0, NOW(), NOW()),
    ('编辑用户',       'system:user:edit',      1, '允许修改自己的用户资料',       0, NOW(), NOW()),
    ('删除用户',       'system:user:remove',    1, '允许删除用户',                 0, NOW(), NOW()),
    ('重置密码',       'system:user:resetPwd',  1, '允许重置他人密码',             0, NOW(), NOW()),
    ('修改用户状态',   'system:user:status',    1, '允许启用或禁用用户',           0, NOW(), NOW()),
    ('分配用户角色',   'system:user:assign',    1, '允许给用户分配角色',           0, NOW(), NOW()),
    ('管理他人账户',   'system:user:manage',    1, '允许管理他人账户（改资料/状态/角色/删除）', 0, NOW(), NOW()),
    -- 角色模块
    ('查看角色',       'system:role:view',      1, '允许查看角色列表和详情',       0, NOW(), NOW()),
    ('添加角色',       'system:role:add',       1, '允许创建新角色',               0, NOW(), NOW()),
    ('编辑角色',       'system:role:edit',      1, '允许修改角色信息',             0, NOW(), NOW()),
    ('删除角色',       'system:role:remove',    1, '允许删除角色',                 0, NOW(), NOW()),
    ('分配权限',       'system:role:assign',    1, '允许为角色分配权限',           0, NOW(), NOW()),
    ('启用禁用角色',   'system:role:status',    1, '允许启用或禁用角色',           0, NOW(), NOW()),
    -- 菜单模块
    ('查看菜单',       'system:menu:view',      1, '允许查看菜单列表和菜单树',     0, NOW(), NOW()),
    ('添加菜单',       'system:menu:add',       1, '允许创建新菜单',               0, NOW(), NOW()),
    ('编辑菜单',       'system:menu:edit',      1, '允许修改菜单信息',             0, NOW(), NOW()),
    ('删除菜单',       'system:menu:remove',    1, '允许删除菜单',                 0, NOW(), NOW()),
    ('分配菜单角色',   'system:menu:assign',    1, '允许为菜单分配可见角色',       0, NOW(), NOW()),
    -- 权限模块
    ('查看权限',       'system:permission:view',   1, '允许查看权限列表与他人权限',  0, NOW(), NOW()),
    ('添加权限',       'system:permission:add',    1, '允许创建新权限',              0, NOW(), NOW()),
    ('编辑权限',       'system:permission:edit',   1, '允许修改权限与启停权限',      0, NOW(), NOW()),
    ('删除权限',       'system:permission:remove', 1, '允许删除权限',                0, NOW(), NOW()),
    -- 内容模块
    ('查看文章',       'content:post:view',     1, '允许查看文章',                 0, NOW(), NOW()),
    ('添加文章',       'content:post:add',      1, '允许创建新文章',               0, NOW(), NOW()),
    ('编辑文章',       'content:post:edit',     1, '允许修改文章',                 0, NOW(), NOW()),
    ('删除文章',       'content:post:remove',   1, '允许删除文章',                 0, NOW(), NOW()),
    ('发布文章',       'content:post:publish',  1, '允许发布文章',                 0, NOW(), NOW()),
    ('取消发布',       'content:post:unpublish',1, '允许取消发布文章',             0, NOW(), NOW()),
    ('查看分类',       'content:category:view', 1, '允许查看分类',                 0, NOW(), NOW()),
    ('添加分类',       'content:category:add',  1, '允许创建新分类',               0, NOW(), NOW()),
    ('编辑分类',       'content:category:edit', 1, '允许修改分类',                 0, NOW(), NOW()),
    ('删除分类',       'content:category:remove',1,'允许删除分类',                 0, NOW(), NOW()),
    ('查看标签',       'content:tag:view',      1, '允许查看标签',                 0, NOW(), NOW()),
    ('添加标签',       'content:tag:add',       1, '允许创建新标签',               0, NOW(), NOW()),
    ('编辑标签',       'content:tag:edit',      1, '允许修改标签',                 0, NOW(), NOW()),
    ('删除标签',       'content:tag:remove',    1, '允许删除标签',                 0, NOW(), NOW()),
    -- 评论模块
    ('查看评论',       'comment:view',          1, '允许查看评论',                 0, NOW(), NOW()),
    ('回复评论',       'comment:reply',         1, '允许回复评论',                 0, NOW(), NOW()),
    ('审核评论',       'comment:approve',       1, '允许审核评论',                 0, NOW(), NOW()),
    ('删除评论',       'comment:remove',        1, '允许删除评论',                 0, NOW(), NOW()),
    -- 监控模块
    ('查看日志',       'monitor:log:view',      1, '允许查看日志',                 0, NOW(), NOW()),
    ('清空日志',       'monitor:log:clean',     1, '允许清空日志',                 0, NOW(), NOW()),
    ('查看在线用户',   'monitor:online:view',   1, '允许查看在线用户',             0, NOW(), NOW()),
    ('强制下线',       'monitor:online:logout', 1, '允许强制用户下线',             0, NOW(), NOW()),
    ('查看服务器信息', 'monitor:server:view',   1, '允许查看服务器信息',           0, NOW(), NOW()),
    ('查看访问统计',   'monitor:stats:view',    1, '允许查看全站访问统计（PV/UV/热门文章）', 0, NOW(), NOW()),
    -- 系统配置模块（AI Provider 的 API Key 等敏感配置的入口）
    ('查看系统配置',   'system:config:view',    1, '允许查看系统配置（仅管理员）', 0, NOW(), NOW()),
    ('编辑系统配置',   'system:config:edit',    1, '允许新增/修改/删除系统配置',   0, NOW(), NOW()),
    -- 存储策略模块（配置里含对象存储 AK/SK）
    ('查看存储策略',   'system:storage:view',   1, '允许查看存储策略',             0, NOW(), NOW()),
    ('编辑存储策略',   'system:storage:edit',   1, '允许新增/修改/启停存储策略',   0, NOW(), NOW()),
    -- 文件模块（Stage 6：FileController 18 个端点全部挂能力位）
    --   upload 与 storage:edit 分开：改存储策略（换 AK/SK、切策略）是管理员的事，
    --   而上传是每个作者的日常操作，锁给管理员等于把编辑器砸了。
    ('上传文件',       'system:file:upload',    1, '允许上传文件（含批量与分片上传）', 0, NOW(), NOW()),
    ('查看文件',       'system:file:view',      1, '允许查看文件（列表/详情/下载/预览）', 0, NOW(), NOW()),
    ('编辑文件',       'system:file:edit',      1, '允许编辑文件信息',             0, NOW(), NOW()),
    ('删除文件',       'system:file:remove',    1, '允许删除文件',                 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `name`        = VALUES(`name`),
    `description` = VALUES(`description`),
    `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 4. 菜单
--
--    id 显式指定，保证下面「角色 → 菜单」的绑定用固定 id 引用。
--    type：0=目录，1=菜单
--
--    【不再有 type=2 按钮菜单】
--    旧模型里「按钮」是一条菜单记录，再把权限挂上去（菜单 ↔ 权限映射），
--    前端按「这个菜单挂了哪些权限」决定按钮显隐。新模型下这条映射已下线，
--    按钮级权限改由 **permission identity** 决定（前端 v-access:code），
--    菜单不再承载权限 → type=2 的行没有存在意义，见下方 4a 的清理。
-- -----------------------------------------------------------------------------
INSERT INTO `t_menus`
    (`id`, `name`, `icon`, `path`, `component`, `type`, `parent_id`, `sort`, `hidden`,
     `create_time`, `update_time`, `redirect`, `is_link`, `is_deleted`)
VALUES
    (1,  '系统管理', 'setting',  '/system',     'Layout',                 0, 0, 1, 0, NOW(), NOW(), '/system/user',    0, 0),
    (2,  '内容管理', 'document', '/content',    'Layout',                 0, 0, 2, 0, NOW(), NOW(), '/content/article',0, 0),
    (3,  '个人中心', 'user',     '/profile',    'Layout',                 0, 0, 3, 0, NOW(), NOW(), '/profile/info',   0, 0),
    (4,  '数据统计', 'chart',    '/statistics', 'Layout',                 0, 0, 4, 0, NOW(), NOW(), '/statistics/dashboard', 0, 0),
    (5,  '用户管理', 'user',     'user',        'system/user/index',      1, 1, 1, 0, NOW(), NOW(), NULL, 0, 0),
    (6,  '角色管理', 'role',     'role',        'system/role/index',      1, 1, 2, 0, NOW(), NOW(), NULL, 0, 0),
    (7,  '菜单管理', 'menu',     'menu',        'system/menu/index',      1, 1, 3, 0, NOW(), NOW(), NULL, 0, 0),
    (8,  '权限管理', 'lock',     'permission',  'system/permission/index',1, 1, 4, 0, NOW(), NOW(), NULL, 0, 0),
    (9,  '文章管理', 'article',  'article',     'content/article/index',  1, 2, 1, 0, NOW(), NOW(), NULL, 0, 0),
    (10, '分类管理', 'category', 'category',    'content/category/index', 1, 2, 2, 0, NOW(), NOW(), NULL, 0, 0),
    (11, '标签管理', 'tag',      'tag',         'content/tag/index',      1, 2, 3, 0, NOW(), NOW(), NULL, 0, 0),
    (12, '评论管理', 'comment',  'comment',     'content/comment/index',  1, 2, 4, 0, NOW(), NOW(), NULL, 0, 0),
    (13, '个人信息', 'profile',  'info',        'profile/info/index',     1, 3, 1, 0, NOW(), NOW(), NULL, 0, 0),
    (14, '修改密码', 'password', 'password',    'profile/password/index',1, 3, 2, 0, NOW(), NOW(), NULL, 0, 0),
    (15, '数据看板', 'dashboard','dashboard',   'statistics/dashboard/index', 1, 4, 1, 0, NOW(), NOW(), NULL, 0, 0),
    (16, '访问统计', 'visits',   'visits',      'statistics/visits/index',   1, 4, 2, 0, NOW(), NOW(), NULL, 0, 0),
    (24, '文档中心', 'link',     'https://docs.example.com', NULL,         1, 0, 5, 0, NOW(), NOW(), NULL, 1, 0)
ON DUPLICATE KEY UPDATE
    `name`        = VALUES(`name`),
    `icon`        = VALUES(`icon`),
    `path`        = VALUES(`path`),
    `component`   = VALUES(`component`),
    `type`        = VALUES(`type`),
    `parent_id`   = VALUES(`parent_id`),
    `sort`        = VALUES(`sort`),
    `hidden`      = VALUES(`hidden`),
    `redirect`    = VALUES(`redirect`),
    `is_link`     = VALUES(`is_link`),
    `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 4a. 清理「按钮型菜单」（type = 2）
--
--     为什么要删：type=2 的行是旧「菜单 ↔ 权限」模型的载体。
--     映射表 t_menu_permissions 已下线，按钮级权限改由 identity 决定，
--     这些行不再有任何语义 —— 留着只会让人以为「菜单还能挂权限」。
--
--     执行顺序：先删角色绑定，再删菜单本体。
--     （t_sys_role_menus 上没有外键约束，删菜单本体不会自动带走绑定行，
--       顺序反了就会留下指向已删菜单的悬空行。）
--
--     注意：这里按 type=2 全量清理，不只是种子自己建的 id 17~23 ——
--     管理端此前允许手工建按钮菜单，那些同样是旧模型的遗留。
--     前端已同步移除「按钮」这个菜单类型，新数据不会再产生 type=2。
-- -----------------------------------------------------------------------------
DELETE rm FROM `t_sys_role_menus` rm
JOIN `t_menus` m ON m.`id` = rm.`menu_id`
WHERE m.`type` = 2;

DELETE FROM `t_menus` WHERE `type` = 2;

-- -----------------------------------------------------------------------------
-- 5. 角色 → 权限
--
--    ADMIN 拿到全部权限；USER 拿到 system:user:edit（改自己的资料）
--    以及 system:file:upload / system:file:view（上传与查看自己的文件）。
--
--    为什么 USER 要 file:upload / file:view：
--    文章写作要插图，作者必须能上传、能预览/下载自己刚传上去的图。
--    file:edit / file:remove 仍然只给 ADMIN ——
--    改与删由「归属校验 + 管理员」两条线把守，普通作者不需要这两个能力位。
--
--    用 INSERT ... SELECT 而不是写死 permission_id —— 权限 id 会随库变化。
-- -----------------------------------------------------------------------------
INSERT INTO `t_role_permissions`
    (`role_id`, `permission_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, p.`id`, NOW(), NOW(), 0
FROM `t_roles` r
CROSS JOIN `t_permissions` p
WHERE r.`code` = 'ADMIN'
ON DUPLICATE KEY UPDATE `update_time` = NOW();

INSERT INTO `t_role_permissions`
    (`role_id`, `permission_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, p.`id`, NOW(), NOW(), 0
FROM `t_roles` r
JOIN `t_permissions` p
  ON p.`identity` IN ('system:user:edit', 'system:file:upload', 'system:file:view')
WHERE r.`code` = 'USER'
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 6. 角色 → 菜单（role → menu 直连）
--
--    ADMIN 看到全部菜单；USER 只看到「个人中心」子树（3 目录 + 13/14 两个页面）。
--    子树里的祖先目录也会由应用侧自动补齐，这里显式写全更直观。
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_role_menus`
    (`role_id`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, m.`id`, NOW(), NOW(), 0
FROM `t_roles` r
CROSS JOIN `t_menus` m
WHERE r.`code` = 'ADMIN'
ON DUPLICATE KEY UPDATE `update_time` = NOW();

INSERT INTO `t_sys_role_menus`
    (`role_id`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, m.`id`, NOW(), NOW(), 0
FROM `t_roles` r
JOIN `t_menus` m ON m.`id` IN (3, 13, 14)
WHERE r.`code` = 'USER'
ON DUPLICATE KEY UPDATE `update_time` = NOW();
