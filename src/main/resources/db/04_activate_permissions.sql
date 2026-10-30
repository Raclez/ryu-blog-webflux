-- =============================================================================
-- 启用 RBAC 模型要求「可用」的权限（幂等，可重复执行）
--
-- 背景：
--   2025-06-18 那批初始种子数据把一部分权限写成了 is_active = 0：
--     comment:* / monitor:* / content:category:view|add / content:tag:view|edit
--   而 db/03_rbac_seed.sql 的 ON DUPLICATE KEY UPDATE **刻意不覆盖 is_active**
--   （理由是「管理员手工停用的权限不该被种子脚本重新打开」），
--   于是这些行至今仍是停用状态。
--
--   停用 = 解析器直接过滤掉（PermissionsRepository: is_active = 1）
--        = @SaCheckPermission 永远不通过
--        = **整个端点对所有人 403，包括管理员**。
--   已经踩中的：
--     MonitorController       类级 @SaCheckPermission(monitor:server:view) → 整类锁死
--     SysConfigController     类级 @SaCheckPermission(system:config:view)  → 整类锁死
--     StorageConfigController 类级 @SaCheckPermission(system:storage:view) → 整类锁死
--
--   这次 Stage 5 给评论/分类/标签/版本补了 @SaCheckPermission，
--   用的正是 comment:* / content:category:* / content:tag:* —— 不启用就是死接口。
--
-- 执行顺序：必须排在 db/03_rbac_seed.sql 之后（种子负责插入缺失的 identity，
--          本脚本负责把已有但停用的 identity 启用）。
--
-- 本脚本只动「模型要求可用」的固定清单，不碰其它权限的 is_active ——
-- 管理员手工停用的其它权限保持停用。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 补齐缺失的权限标识
--
--    system:config:* / system:storage:* 在开发库里**根本没有这两行** ——
--    SysConfigController 与 StorageConfigController 整类锁死就是它们造成的。
--    名称/描述以 db/03_rbac_seed.sql 为准，这里只在不存在时插入，
--    已存在则不覆盖文字（避免与种子脚本两处维护同一份文案）。
-- -----------------------------------------------------------------------------
INSERT INTO `t_permissions`
    (`name`, `identity`, `is_active`, `description`, `is_deleted`, `create_time`, `update_time`)
VALUES
    ('查看系统配置',   'system:config:view',   1, '允许查看系统配置（仅管理员）',       0, NOW(), NOW()),
    ('编辑系统配置',   'system:config:edit',   1, '允许新增/修改/删除系统配置',         0, NOW(), NOW()),
    ('查看存储策略',   'system:storage:view',  1, '允许查看存储策略',                   0, NOW(), NOW()),
    ('编辑存储策略',   'system:storage:edit',  1, '允许新增/修改/启停存储策略',         0, NOW(), NOW()),
    -- 全站访问统计（GET /view/stats）—— 是全站口径的监控数据，不是「自身数据」
    ('查看访问统计',   'monitor:stats:view',   1, '允许查看全站访问统计（PV/UV/热门文章）', 0, NOW(), NOW()),
    -- 文件模块（Stage 6）：FileController 的 18 个端点全部挂能力位，
    -- 缺任何一条都会让对应端点对所有人 403（含管理员）
    ('上传文件',       'system:file:upload',   1, '允许上传文件（含批量与分片上传）',   0, NOW(), NOW()),
    ('查看文件',       'system:file:view',     1, '允许查看文件（列表/详情/下载/预览）', 0, NOW(), NOW()),
    ('编辑文件',       'system:file:edit',     1, '允许编辑文件信息',                   0, NOW(), NOW()),
    ('删除文件',       'system:file:remove',   1, '允许删除文件',                       0, NOW(), NOW())
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 2. 启用被停用的权限
--
--    清单 = RBAC 模型要求可用、但初始种子里写成 is_active = 0 的那批。
--    is_deleted = 0 的行才启用 —— 已软删的不复活。
-- -----------------------------------------------------------------------------
UPDATE `t_permissions`
SET `is_active` = 1, `update_time` = NOW()
WHERE `is_deleted` = 0
  AND `identity` IN (
        -- 评论模块
        'comment:view',
        'comment:reply',
        'comment:approve',
        'comment:remove',
        -- 分类模块（view/add 此前停用，edit/remove 一直是启用的）
        'content:category:view',
        'content:category:add',
        -- 标签模块（view/edit 此前停用，add/remove 一直是启用的）
        'content:tag:view',
        'content:tag:edit',
        -- 监控模块（MonitorController 整类依赖 monitor:server:view）
        'monitor:log:view',
        'monitor:log:clean',
        'monitor:online:view',
        'monitor:online:logout',
        'monitor:server:view',
        -- 全站访问统计（Stage 5 新增的能力位）
        'monitor:stats:view',
        -- 文件模块（Stage 6 新增的能力位）
        'system:file:upload', 'system:file:view',
        'system:file:edit', 'system:file:remove'
      );

-- -----------------------------------------------------------------------------
-- 3. 确保 ADMIN 角色持有这些权限
--
--    03_rbac_seed.sql 用 CROSS JOIN 给 ADMIN 发全部权限，
--    所以「已存在的权限」ADMIN 早就有了；本步是为了让
--    「本次新增的 identity（system:config/storage:*、monitor:stats:view、system:file:*）」
--    和「任何手工删过绑定的库」也能对上。
--    用 INSERT ... SELECT 而不是写死 permission_id —— 权限 id 会随库变化。
-- -----------------------------------------------------------------------------
INSERT INTO `t_role_permissions`
    (`role_id`, `permission_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, p.`id`, NOW(), NOW(), 0
FROM `t_roles` r
JOIN `t_permissions` p
  ON p.`identity` IN (
        'system:config:view', 'system:config:edit',
        'system:storage:view', 'system:storage:edit',
        'comment:view', 'comment:reply', 'comment:approve', 'comment:remove',
        'content:category:view', 'content:category:add',
        'content:tag:view', 'content:tag:edit',
        'monitor:log:view', 'monitor:log:clean',
        'monitor:online:view', 'monitor:online:logout', 'monitor:server:view',
        'monitor:stats:view',
        'system:file:upload', 'system:file:view',
        'system:file:edit', 'system:file:remove'
     )
WHERE r.`code` = 'ADMIN'
  AND p.`is_active` = 1
  AND p.`is_deleted` = 0
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 4. USER 角色持有文件上传 / 查看
--
--    作者写文章要插图，必须能上传、能预览自己刚传上去的图。
--    file:edit / file:remove 仍然只给 ADMIN。
-- -----------------------------------------------------------------------------
INSERT INTO `t_role_permissions`
    (`role_id`, `permission_id`, `create_time`, `update_time`, `is_deleted`)
SELECT r.`id`, p.`id`, NOW(), NOW(), 0
FROM `t_roles` r
JOIN `t_permissions` p
  ON p.`identity` IN ('system:file:upload', 'system:file:view')
WHERE r.`code` = 'USER'
  AND p.`is_active` = 1
  AND p.`is_deleted` = 0
ON DUPLICATE KEY UPDATE `update_time` = NOW();
