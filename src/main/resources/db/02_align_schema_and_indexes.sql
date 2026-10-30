-- =============================================================================
-- 02_align_schema_and_indexes.sql
--
-- ⚠️ 本文件是**迁移内容的可读参考**，不是可直接执行的脚本。
--
--   原因：MySQL **不支持** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
--   （那是 MariaDB 的语法），也不支持 `ADD INDEX IF NOT EXISTS`。
--   本地开发库是 MySQL 8.0.45，直接跑这个文件会在第一条 ALTER 就语法报错。
--
--   实际执行请用仓库根目录的 apply-ddl.sh —— 它用
--   「先查 information_schema，再决定要不要执行」的方式实现了幂等，
--   可以重复跑而不会报 Duplicate key / Unknown column。
--
--   全新建库请用 src/main/resources/schema.sql（用 CREATE TABLE IF NOT EXISTS）。
--
-- 用途：把开发库对齐到「实体 + Repository」的真实契约，并补齐热点表索引。
-- 目标库：本地开发库 ryu_blog_webflux（localhost:3306）。
--
-- 背景：
--   1) 代码里若干 @Query 用了不存在的表名/列名，运行时直接 SQL 报错；
--   2) BaseEntity 统一的 create_time / update_time / is_deleted 在部分表上缺列；
--   3) t_file / t_view_history 等热点表只有主键，所有列表与统计查询都是全表扫描；
--   4) 死代码（Like / FileLog / FileMetadata / FilePermission / FileShare /
--      FileThumbnail / FileVersion / NetworkDisk）对应的表已成孤儿，一并清理。
--
-- 索引选择依据（对应 Repository 中的实际查询）：
--   t_file          is_deleted + create_time / creator_id / file_type / checksum / file_path
--   t_view_history  visitor_id + create_time / visitor_id + post_id / post_id / create_time
--   t_comments      post_id + parent_comment_id + status + is_deleted + create_time 等
--   t_posts         user_id + is_deleted + create_time / status + is_deleted + views
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、补齐实体声明但表中缺失的列
-- -----------------------------------------------------------------------------

-- Favorite 实体继承 BaseEntity，带 is_deleted；原表只有 5 列。
ALTER TABLE `t_favorites`
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';

-- RolePermission 实体继承 BaseEntity。
ALTER TABLE `t_role_permissions`
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';

-- PostCategory / PostTag 实体继承 BaseEntity。
ALTER TABLE `t_post_categories`
    ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';

ALTER TABLE `t_post_tags`
    ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';

-- ResourceGroupFileRel 实体继承 BaseEntity。
ALTER TABLE `t_resource_group_file_rels`
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';

-- AiGenerationHistory 实体继承 BaseEntity。
ALTER TABLE `t_ai_generation_history`
    ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ViewHistory：visitor_id 是前端生成的匿名访客标识，不是登录用户。
-- 「我的浏览历史 / 清空浏览历史」需要按登录用户过滤，补 user_id（匿名访客为 NULL）。
ALTER TABLE `t_view_history`
    ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '登录用户ID，匿名访客为NULL';

-- Posts：slug（文章别名）。
-- DTO 与 VO 都声明了 slug，但实体与表里都没有 —— 客户端传的 slug 被静默丢弃，
-- 详情接口返回的 slug 恒为空。这里补上真实列。
-- 唯一索引在下面第四节用 uk_posts_slug 建：应用层已有
-- ArticleCrudService.checkSlugAvailable 校验（会抛 BusinessException.postSlugExists()），
-- 唯一键只是数据库兜底。
ALTER TABLE `t_posts`
    ADD COLUMN `slug` varchar(255) DEFAULT NULL COMMENT '文章别名（URL slug）';

-- Posts：is_sticky（是否置顶）。
-- DTO、VO 与前端开关都有「置顶」，但实体与表里都没有 —— 客户端传的置顶标记
-- 被 MapStruct 静默丢弃，开关永远停在「关」。
ALTER TABLE `t_posts`
    ADD COLUMN `is_sticky` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是';

-- t_sys_role_menus：角色 → 菜单直连的落地表，此前只有 id/role_id/menu_id，
-- 缺项目约定的审计字段，RoleMenu 实体继承 BaseEntity 会写不进去。
ALTER TABLE `t_sys_role_menus`
    ADD COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ADD COLUMN `is_deleted`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';

-- -----------------------------------------------------------------------------
-- 二、下线已退场的关联表
-- -----------------------------------------------------------------------------

-- 【已下线】t_menu_permissions（菜单 → 权限关联）：
-- 菜单可见性模型改为 role → menu 直连后，这张表不再承载可见性；
-- 按钮级权限改由「前端按 permission identity 控制显隐」承担。
-- 关联的 MenuPermission 实体 / MenuPermissionRepository 已删除。
-- 注意：apply-ddl.sh 会先检查表里有没有数据，有数据就保留并告警，不擅自丢数据。
DROP TABLE IF EXISTS `t_menu_permissions`;

-- -----------------------------------------------------------------------------
-- 三、删除死代码遗留的空表
--    对应的实体/仓储/服务/控制器已从代码中移除，这些表不再有任何读写方。
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `t_likes`;
DROP TABLE IF EXISTS `t_file_logs`;
DROP TABLE IF EXISTS `t_file_metadata`;
DROP TABLE IF EXISTS `t_file_permissions`;
DROP TABLE IF EXISTS `t_file_shares`;
DROP TABLE IF EXISTS `t_file_thumbnails`;
DROP TABLE IF EXISTS `t_file_versions`;
DROP TABLE IF EXISTS `t_network_disk`;

-- -----------------------------------------------------------------------------
-- 四、补齐索引
-- -----------------------------------------------------------------------------

-- ---- t_file：此前只有主键，所有文件列表都是全表扫描 ----
ALTER TABLE `t_file`
    ADD INDEX `idx_file_deleted_time` (`is_deleted`, `create_time`),
    ADD INDEX `idx_file_creator` (`creator_id`, `is_deleted`),
    ADD INDEX `idx_file_type` (`file_type`, `is_deleted`, `create_time`),
    ADD INDEX `idx_file_path` (`file_path`),
    ADD INDEX `idx_file_checksum` (`checksum`(64), `is_deleted`),

-- ---- t_view_history：此前只有主键 ----
ALTER TABLE `t_view_history`
    ADD INDEX `idx_vh_visitor_time` (`visitor_id`, `create_time`),
    ADD INDEX `idx_vh_visitor_post` (`visitor_id`, `post_id`),
    ADD INDEX `idx_vh_user_time` (`user_id`, `create_time`),
    ADD INDEX `idx_vh_post` (`post_id`),
    ADD INDEX `idx_vh_create_time` (`create_time`);

-- ---- t_comments：原有单列索引无法覆盖 status/is_deleted/create_time 过滤 ----
ALTER TABLE `t_comments`
    ADD INDEX `idx_comment_post` (`post_id`, `parent_comment_id`, `status`, `is_deleted`, `create_time`),
    ADD INDEX `idx_comment_user` (`user_id`, `status`, `is_deleted`, `create_time`),
    ADD INDEX `idx_comment_parent` (`parent_comment_id`, `status`, `is_deleted`, `create_time`),
    DROP INDEX `idx_post_id`,
    DROP INDEX `idx_user_id`;

-- ---- t_posts：idx_user 与 idx_user_id 完全重复；缺 user_id 排序与热门榜排序 ----
ALTER TABLE `t_posts`
    ADD INDEX `idx_posts_user` (`user_id`, `is_deleted`, `create_time`),
    ADD INDEX `idx_posts_status_views` (`status`, `is_deleted`, `views`),
    -- 别名唯一：应用层已有 checkSlugAvailable 校验，这里再加一道数据库兜底。
    -- 列是新增的、历史数据全为 NULL，MySQL 的唯一索引允许多个 NULL，不会冲突。
    ADD UNIQUE INDEX `uk_posts_slug` (`slug`),
    DROP INDEX `idx_user`,
    DROP INDEX `idx_user_id`;

-- ---- 角色/权限/菜单/存储配置：此前均无二级索引 ----
ALTER TABLE `t_role_permissions`
    ADD INDEX `idx_rp_role` (`role_id`),
    ADD INDEX `idx_rp_permission` (`permission_id`);

-- t_permissions.identity 是 @SaCheckPermission 的比对依据，必须唯一 ——
-- 允许重复的话，鉴权结果不可预期（同一 identity 对应多条权限记录）。
-- 先删掉重复的（保留 id 最小的），再建唯一键。
DELETE p1 FROM `t_permissions` p1
JOIN `t_permissions` p2
  ON p1.`identity` = p2.`identity` AND p1.`id` > p2.`id`;

ALTER TABLE `t_permissions`
    ADD UNIQUE KEY `uk_perm_identity` (`identity`),
    ADD INDEX `idx_perm_active` (`is_active`);

-- t_role_permissions 缺 (role_id, permission_id) 唯一键
-- （t_user_roles 有 uk_user_role，t_menu_permissions 有 uk_menu_permission，唯独这张漏了）。
-- 先删重复行再建唯一键。
DELETE rp1 FROM `t_role_permissions` rp1
JOIN `t_role_permissions` rp2
  ON rp1.`role_id` = rp2.`role_id`
 AND rp1.`permission_id` = rp2.`permission_id`
 AND rp1.`id` > rp2.`id`;

ALTER TABLE `t_role_permissions`
    ADD UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`);

-- t_sys_role_menus (role → menu) 缺 (role_id, menu_id) 唯一键。
-- 种子脚本 03_rbac_seed.sql 的 INSERT ... ON DUPLICATE KEY UPDATE 靠它才能幂等 ——
-- 没有唯一键时 ON DUPLICATE 永远不触发，脚本每跑一次就多一份重复绑定。
-- 先删重复行再建唯一键。
DELETE rm1 FROM `t_sys_role_menus` rm1
JOIN `t_sys_role_menus` rm2
  ON rm1.`role_id` = rm2.`role_id`
 AND rm1.`menu_id` = rm2.`menu_id`
 AND rm1.`id` > rm2.`id`;

ALTER TABLE `t_sys_role_menus`
    ADD UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`);

-- t_roles.code 唯一键 —— 种子脚本给角色做 upsert 时靠它。
-- 同样先清重复。
DELETE r1 FROM `t_roles` r1
JOIN `t_roles` r2
  ON r1.`code` = r2.`code` AND r1.`id` > r2.`id`;

ALTER TABLE `t_roles`
    ADD UNIQUE KEY `uk_code` (`code`);

ALTER TABLE `t_menus`
    ADD INDEX `idx_menus_parent_sort` (`parent_id`, `sort`),
    ADD INDEX `idx_menus_type` (`type`, `is_deleted`);

ALTER TABLE `t_storage_config`
    ADD INDEX `idx_sc_strategy` (`strategy_name`),
    ADD INDEX `idx_sc_enable` (`is_enable`);

ALTER TABLE `t_resource_group_file_rels`
    ADD INDEX `idx_rgfr_file` (`file_id`);

ALTER TABLE `t_user_roles`
    ADD INDEX `idx_ur_role` (`role_id`);

ALTER TABLE `t_sys_dict_types`
    ADD INDEX `idx_dt_status` (`status`, `is_deleted`);

-- idx_dict_type_id 是 uk_dict_type_key(dict_type_id, dict_item_key) 的左前缀冗余。
ALTER TABLE `t_sys_dict_items`
    DROP INDEX `idx_dict_type_id`;
