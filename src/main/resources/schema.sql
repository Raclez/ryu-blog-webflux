-- =============================================================================
-- schema.sql — Ryu Blog 数据库结构
--
-- 本文件是数据库结构的唯一权威来源，与 src/main/java/com/ryu/blog/entity 下的实体一一对应。
--
-- 维护约定：
--   1. 表名以实体上的 @Table 为准（历史上 schema.sql 与实体曾大面积不一致，
--      导致若干 @Query 运行时直接 SQL 报错）；
--   2. 所有继承 BaseEntity 的表都必须带 id / create_time / update_time / is_deleted；
--   3. 索引按 Repository 里真实出现的查询条件来建，不做无依据的冗余索引。
--
-- 与开发库的对齐脚本见 src/main/resources/db/02_align_schema_and_indexes.sql
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 一、用户 / 角色 / 权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_users` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`      varchar(50)  NOT NULL COMMENT '用户名',
    `password`      varchar(100) NOT NULL COMMENT '密码（BCrypt 哈希）',
    `nickname`      varchar(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`        varchar(255) DEFAULT NULL COMMENT '头像地址',
    `email`         varchar(100) DEFAULT NULL COMMENT '邮箱',
    `phone`         varchar(20)  DEFAULT NULL COMMENT '手机号',
    `bio`           text         DEFAULT NULL COMMENT '个人简介',
    `status`        int          DEFAULT 1 COMMENT '状态：0-禁用，1-正常，2-锁定',
    `create_time`   datetime     NOT NULL COMMENT '创建时间',
    `last_login_ip` varchar(255) DEFAULT NULL COMMENT '最后登录IP',
    `update_time`   datetime     NOT NULL COMMENT '更新时间',
    `last_login_time` datetime   DEFAULT NULL COMMENT '最后登录时间',
    `is_deleted`    tinyint(1)   DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

CREATE TABLE IF NOT EXISTS `t_roles` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `name`        varchar(50) NOT NULL COMMENT '角色名称',
    `code`        varchar(50) NOT NULL COMMENT '角色编码',
    `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
    `sort`        int         DEFAULT 0 COMMENT '排序',
    `is_active`   tinyint(1)  DEFAULT 1 COMMENT '是否启用',
    `is_default`  tinyint(1)  DEFAULT 0 COMMENT '是否默认角色',
    `create_time` datetime    NOT NULL COMMENT '创建时间',
    `update_time` datetime    NOT NULL COMMENT '更新时间',
    `is_deleted`  tinyint(1)  DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色表';

CREATE TABLE IF NOT EXISTS `t_user_roles` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID',
    `role_id`     bigint   NOT NULL COMMENT '角色ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_time` datetime NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_ur_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户角色关联表';

CREATE TABLE IF NOT EXISTS `t_permissions` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `name`        varchar(255) DEFAULT NULL COMMENT '权限名称',
    `identity`    varchar(255) DEFAULT NULL COMMENT '权限标识，如 system:user:create',
    `is_active`   tinyint      DEFAULT NULL COMMENT '是否激活',
    `description` text         DEFAULT NULL COMMENT '权限描述',
    `is_deleted`  tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- identity 是 @SaCheckPermission 的比对依据，必须唯一。
    -- 原先只有普通索引，允许重复 —— 重复的 identity 会让鉴权结果不可预期。
    UNIQUE KEY `uk_perm_identity` (`identity`),
    KEY `idx_perm_active` (`is_active`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '权限表';

CREATE TABLE IF NOT EXISTS `t_role_permissions` (
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`       bigint      NOT NULL COMMENT '角色ID',
    `permission_id` bigint      NOT NULL COMMENT '权限ID',
    `create_time`   datetime    NOT NULL COMMENT '创建时间',
    `update_time`   datetime    NOT NULL COMMENT '更新时间',
    `is_deleted`    tinyint(1)  NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    -- 防止同一对 (角色, 权限) 重复插入
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_rp_role` (`role_id`),
    KEY `idx_rp_permission` (`permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色权限关联表';

-- =============================================================================
-- 二、菜单
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_menus` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `name`        varchar(255) DEFAULT NULL COMMENT '菜单名称',
    `route_name`  varchar(64)  DEFAULT NULL COMMENT '前端路由名（vue-router 的 name）',
    `icon`        varchar(255) DEFAULT NULL COMMENT '菜单图标',
    `path`        varchar(255) DEFAULT NULL COMMENT '路由路径',
    `component`   varchar(255) DEFAULT NULL COMMENT '前端组件路径',
    `type`        int          DEFAULT NULL COMMENT '菜单类型',
    `parent_id`   bigint       DEFAULT NULL COMMENT '父菜单ID',
    `sort`        int          DEFAULT NULL COMMENT '排序',
    `hidden`      int          DEFAULT NULL COMMENT '是否隐藏',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `redirect`    varchar(255) DEFAULT NULL COMMENT '重定向路径',
    `is_link`     tinyint      DEFAULT NULL COMMENT '是否外链',
    `is_deleted`  tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_menus_route_name` (`route_name`),
    KEY `idx_menus_parent_sort` (`parent_id`, `sort`),
    KEY `idx_menus_type` (`type`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '菜单表';

-- 【已下线】菜单 → 权限关联表 t_menu_permissions 已从本文件中移除。
-- 菜单可见性改为 role → menu 直连（见 t_sys_role_menus）后，这张表不再承载可见性；
-- 按钮级权限改由「前端按 permission identity 控制显隐」承担。
-- 关联的 Java 类（MenuPermission / MenuPermissionRepository）已一并删除。
-- 已有库里的残留表由 apply-ddl.sh 第二节下线。

-- 角色 → 菜单直连：决定「哪些角色能看到哪些菜单」。
-- 这张表此前定义了却无任何实体/代码引用（纯孤儿表），
-- 菜单可见性模型改为 role → menu 直连后，它才真正开始工作。
CREATE TABLE IF NOT EXISTS `t_sys_role_menus` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`     bigint   NOT NULL COMMENT '角色ID',
    `menu_id`     bigint   NOT NULL COMMENT '菜单ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_srm_menu` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色菜单关联表';

-- =============================================================================
-- 三、文章 / 分类 / 标签
-- =============================================================================

-- 注：本文件此前 **0 条 INSERT** —— 全新部署的库一个角色、一条权限都没有。
-- RBAC 的基础数据（角色 / 权限 / 菜单 / 角色-权限 / 角色-菜单）单独放在
-- db/03_rbac_seed.sql（幂等，可重复执行），因为它既要在「全新建库」时跑，
-- 也要在「已有库补数据」时跑。执行方式见仓库根目录的 apply-ddl.sh。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_categories` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name`        varchar(50) NOT NULL COMMENT '分类名称',
    `description` varchar(255) DEFAULT NULL COMMENT '分类描述',
    `sort`        int         DEFAULT 0 COMMENT '排序',
    `create_time` datetime    NOT NULL COMMENT '创建时间',
    `update_time` datetime    NOT NULL COMMENT '更新时间',
    `is_deleted`  tinyint(1)  DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '分类表';

CREATE TABLE IF NOT EXISTS `t_tags` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name`        varchar(50) NOT NULL COMMENT '标签名称',
    `slug`        varchar(50) DEFAULT NULL COMMENT '标签别名',
    `description` varchar(255) DEFAULT NULL COMMENT '标签描述',
    `create_time` datetime    NOT NULL COMMENT '创建时间',
    `update_time` datetime    NOT NULL COMMENT '更新时间',
    `is_deleted`  tinyint(1)  DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签表';

CREATE TABLE IF NOT EXISTS `t_posts` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    `title`          varchar(100) NOT NULL COMMENT '标题',
    `slug`           varchar(255) DEFAULT NULL COMMENT '文章别名（URL slug）',
    `excerpt`        varchar(255) DEFAULT NULL COMMENT '摘要',
    `content`        longtext     DEFAULT NULL COMMENT '正文',
    `user_id`        bigint       NOT NULL COMMENT '作者ID',
    `status`         int          DEFAULT 2 COMMENT '状态：0-待审核，1-已发布，2-草稿，3-已归档',
    `views`          int          DEFAULT 0 COMMENT '浏览量',
    `seo_meta`       text         DEFAULT NULL COMMENT 'SEO 元数据（JSON）',
    `cover_image_id` bigint       DEFAULT NULL COMMENT '封面图片ID',
    `is_original`    tinyint(1)   DEFAULT 1 COMMENT '是否原创',
    `source_url`     varchar(255) DEFAULT NULL COMMENT '来源地址',
    `sort`           int          DEFAULT 0 COMMENT '排序',
    `allow_comment`  tinyint(1)   DEFAULT 1 COMMENT '是否允许评论',
    `is_sticky`      tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    `visibility`     varchar(20)  DEFAULT 'public' COMMENT '可见性：public/private',
    `password`       varchar(100) DEFAULT NULL COMMENT '访问密码',
    `license`        varchar(100) DEFAULT NULL COMMENT '许可协议',
    `schedule_time`  datetime     DEFAULT NULL COMMENT '定时发布时间',
    `create_time`    datetime     NOT NULL COMMENT '创建时间',
    `update_time`    datetime     NOT NULL COMMENT '更新时间',
    `publish_time`   datetime     DEFAULT NULL COMMENT '发布时间',
    `is_deleted`     tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_posts_user` (`user_id`, `is_deleted`, `create_time`),
    KEY `idx_posts_status_views` (`status`, `is_deleted`, `views`),
    KEY `idx_posts_status_time` (`status`, `is_deleted`, `create_time`),
    UNIQUE KEY `uk_posts_slug` (`slug`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章表';

CREATE TABLE IF NOT EXISTS `t_post_categories` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `post_id`     bigint   NOT NULL COMMENT '文章ID',
    `category_id` bigint   NOT NULL COMMENT '分类ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_category` (`post_id`, `category_id`),
    KEY `idx_pc_category` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章分类关联表';

CREATE TABLE IF NOT EXISTS `t_post_tags` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `post_id`     bigint   NOT NULL COMMENT '文章ID',
    `tag_id`      bigint   NOT NULL COMMENT '标签ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`),
    KEY `idx_pt_tag` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章标签关联表';

CREATE TABLE IF NOT EXISTS `t_post_versions` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `post_id`      bigint       NOT NULL COMMENT '文章ID',
    `title`        varchar(100) DEFAULT NULL COMMENT '标题快照',
    `content`      longtext     DEFAULT NULL COMMENT '正文快照',
    `change_log`   varchar(255) DEFAULT NULL COMMENT '变更说明',
    `version`      int          NOT NULL COMMENT '版本号',
    `create_time`  datetime     NOT NULL COMMENT '创建时间',
    `editor`       bigint       NOT NULL COMMENT '编辑者ID',
    `description`  varchar(255) DEFAULT NULL COMMENT '描述',
    `is_latest`    tinyint(1)   DEFAULT 0 COMMENT '是否最新版本',
    `word_count`   int          DEFAULT NULL COMMENT '字数',
    `tags`         varchar(255) DEFAULT NULL COMMENT '标签',
    `duration`     int          DEFAULT NULL COMMENT '阅读时长（分钟）',
    `modify_count` int          DEFAULT NULL COMMENT '修改次数',
    `update_time`  datetime     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`   tinyint(1)   DEFAULT NULL COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章版本表';

CREATE TABLE IF NOT EXISTS `t_comments` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `content`           text         NOT NULL COMMENT '评论内容',
    `post_id`           bigint       NOT NULL COMMENT '文章ID',
    `user_id`           bigint       NOT NULL COMMENT '评论用户ID',
    `parent_comment_id` bigint       DEFAULT NULL COMMENT '父评论ID',
    `status`            int          DEFAULT 1 COMMENT '状态：0-待审核，1-已通过，2-已拒绝',
    `like_count`        int          DEFAULT 0 COMMENT '点赞数',
    `is_edited`         tinyint(1)   DEFAULT 0 COMMENT '是否编辑过',
    `edit_time`         datetime     DEFAULT NULL COMMENT '编辑时间',
    `create_time`       datetime     NOT NULL COMMENT '创建时间',
    `update_time`       datetime     NOT NULL COMMENT '更新时间',
    `is_deleted`        tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_comment_post` (`post_id`, `parent_comment_id`, `status`, `is_deleted`, `create_time`),
    KEY `idx_comment_user` (`user_id`, `status`, `is_deleted`, `create_time`),
    KEY `idx_comment_parent` (`parent_comment_id`, `status`, `is_deleted`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '评论表';

CREATE TABLE IF NOT EXISTS `t_favorites` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID',
    `post_id`     bigint   NOT NULL COMMENT '文章ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_time` datetime NOT NULL COMMENT '更新时间',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '收藏表';

-- =============================================================================
-- 四、浏览历史
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_view_history` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `visitor_id`    varchar(255)  NOT NULL COMMENT '匿名访客标识',
    `user_id`       bigint        DEFAULT NULL COMMENT '登录用户ID，匿名访客为NULL',
    `post_id`       bigint        NOT NULL COMMENT '文章ID',
    `view_time`     datetime      NOT NULL COMMENT '浏览时间',
    `ip_address`    varchar(255)  NOT NULL COMMENT '访客IP地址',
    `agent`         text          DEFAULT NULL COMMENT '访客设备',
    `location`      varchar(255)  DEFAULT NULL COMMENT '地理位置',
    `view_duration` int           DEFAULT NULL COMMENT '浏览时长（秒）',
    `referer`       text          DEFAULT NULL COMMENT '来源页面',
    `create_time`   datetime      NOT NULL COMMENT '创建时间',
    `update_time`   datetime      NOT NULL COMMENT '更新时间',
    `is_deleted`    tinyint(1)    NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_vh_visitor_time` (`visitor_id`, `create_time`),
    KEY `idx_vh_visitor_post` (`visitor_id`, `post_id`),
    KEY `idx_vh_user_time` (`user_id`, `create_time`),
    KEY `idx_vh_post` (`post_id`),
    KEY `idx_vh_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '浏览历史表';

-- =============================================================================
-- 五、文件与存储
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_file` (
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `file_name`         varchar(255) DEFAULT NULL COMMENT '文件名',
    `file_path`         varchar(255) DEFAULT NULL COMMENT '文件路径',
    `file_size`         bigint       DEFAULT NULL COMMENT '文件大小（字节）',
    `file_type`         varchar(50)  DEFAULT NULL COMMENT '文件类型',
    `mime_type`         varchar(100) DEFAULT NULL COMMENT 'MIME 类型',
    `upload_time`       datetime     DEFAULT NULL COMMENT '上传时间',
    `creator_id`        bigint       DEFAULT NULL COMMENT '上传用户ID',
    `status`            int          DEFAULT NULL COMMENT '状态：0-未处理，1-正常，2-已删除',
    `storage_type`      varchar(50)  DEFAULT NULL COMMENT '存储类型',
    `checksum`          varchar(255) DEFAULT NULL COMMENT '校验和',
    `description`       text         DEFAULT NULL COMMENT '描述',
    `create_time`       datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`       datetime     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`        tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_file_deleted_time` (`is_deleted`, `create_time`),
    KEY `idx_file_creator` (`creator_id`, `is_deleted`),
    KEY `idx_file_type` (`file_type`, `is_deleted`, `create_time`),
    KEY `idx_file_path` (`file_path`),
    KEY `idx_file_checksum` (`checksum`(64), `is_deleted`),
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文件表';

CREATE TABLE IF NOT EXISTS `t_storage_config` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `strategy_name` varchar(255) DEFAULT NULL COMMENT '策略名称',
    `strategy_key`  varchar(255) DEFAULT NULL COMMENT '策略标识',
    `config`        text         DEFAULT NULL COMMENT '策略配置（JSON）',
    `is_enable`     tinyint(1)   DEFAULT NULL COMMENT '是否启用',
    `max_file_size` bigint       DEFAULT NULL COMMENT '单文件大小上限',
    `default_expiry` bigint      DEFAULT NULL COMMENT '默认过期时间',
    `description`   varchar(255) DEFAULT NULL COMMENT '描述',
    `creator_id`    bigint       NOT NULL COMMENT '创建者ID',
    `access_url`    varchar(255) DEFAULT NULL COMMENT '访问地址前缀',
    `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`    tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_sc_strategy` (`strategy_name`),
    KEY `idx_sc_enable` (`is_enable`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '存储策略配置表';

-- =============================================================================
-- 六、系统配置与字典
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_sys_config` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key`   varchar(200) NOT NULL COMMENT '配置键',
    `config_value` varchar(2000) DEFAULT NULL COMMENT '配置值',
    `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
    `extra`        varchar(1000) DEFAULT NULL COMMENT '扩展信息（JSON）',
    `user_id`      bigint       DEFAULT NULL COMMENT '用户ID，0 表示全局配置',
    `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`   tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`, `user_id`),
    KEY `idx_config_key` (`config_key`),
    KEY `idx_sys_config_user_key_deleted` (`user_id`, `config_key`, `is_deleted`),
    KEY `idx_sys_config_key_deleted` (`config_key`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统配置表';

CREATE TABLE IF NOT EXISTS `t_sys_dict_types` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
    `type_name`   varchar(50) NOT NULL COMMENT '类型名称',
    `dict_type`   varchar(50) NOT NULL COMMENT '类型编码',
    `remark`      varchar(255) DEFAULT NULL COMMENT '备注',
    `status`      int         DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_time` datetime    NOT NULL COMMENT '创建时间',
    `update_time` datetime    NOT NULL COMMENT '更新时间',
    `is_deleted`  tinyint(1)  DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`),
    KEY `idx_dt_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '字典类型表';

CREATE TABLE IF NOT EXISTS `t_sys_dict_items` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
    `dict_type_id`   bigint       NOT NULL COMMENT '字典类型ID',
    `dict_item_key`  varchar(50)  NOT NULL COMMENT '字典项键',
    `dict_item_value` varchar(50) NOT NULL COMMENT '字典项值',
    `sort`           int          DEFAULT 0 COMMENT '排序',
    `status`         int          DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `remark`         varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`    datetime     NOT NULL COMMENT '创建时间',
    `update_time`    datetime     NOT NULL COMMENT '更新时间',
    `is_deleted`     tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_key` (`dict_type_id`, `dict_item_key`),
    KEY `idx_di_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '字典项表';

-- =============================================================================
-- 七、异步任务
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_async_tasks` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `user_id`       bigint       NOT NULL COMMENT '提交用户ID',
    `task_type`     varchar(50)  NOT NULL COMMENT '任务类型',
    `status`        varchar(20)  NOT NULL COMMENT '任务状态',
    `priority`      varchar(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    `request_json`  text         DEFAULT NULL COMMENT '请求参数（JSON）',
    `result_json`   text         DEFAULT NULL COMMENT '结果（JSON）',
    `error_message` text         DEFAULT NULL COMMENT '错误信息',
    `progress`      int          DEFAULT 0 COMMENT '进度百分比',
    `submit_time`   datetime     NOT NULL COMMENT '提交时间',
    `execute_at`    datetime     DEFAULT NULL COMMENT '计划执行时间（NULL=立即执行，延迟任务用）',
    `start_time`    datetime     DEFAULT NULL COMMENT '开始时间',
    `complete_time` datetime     DEFAULT NULL COMMENT '完成时间',
    `retry_count`   int          DEFAULT 0 COMMENT '重试次数',
    `max_retries`   int          DEFAULT 3 COMMENT '最大重试次数',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_async_tasks_user` (`user_id`),
    KEY `idx_async_tasks_type` (`task_type`),
    KEY `idx_async_tasks_status` (`status`),
    KEY `idx_async_tasks_submit_time` (`submit_time`),
    KEY `idx_async_tasks_status_priority_submit` (`status`, `priority`, `submit_time`),
    KEY `idx_async_tasks_user_type_status` (`user_id`, `task_type`, `status`),
    -- 延迟任务到期扫描：status='PENDING' AND execute_at <= now
    KEY `idx_async_tasks_status_execute` (`status`, `execute_at`),
    KEY `idx_async_tasks_is_deleted` (`is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '异步任务表';

-- =============================================================================
-- 八、AI 模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_ai_content_template` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `name`            varchar(255) DEFAULT NULL COMMENT '模板名称',
    `description`     varchar(500) DEFAULT NULL COMMENT '模板描述',
    `type`            varchar(50)  DEFAULT NULL COMMENT '模板类型',
    `prompt_template` text         DEFAULT NULL COMMENT '提示词模板',
    `structure`       text         DEFAULT NULL COMMENT '内容结构（JSON）',
    `is_system`       tinyint(1)   DEFAULT 0 COMMENT '是否系统模板',
    `user_id`         bigint       DEFAULT NULL COMMENT '创建用户ID',
    `usage_count`     int          DEFAULT 0 COMMENT '使用次数',
    `create_time`     datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`      tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_template_type` (`type`),
    KEY `idx_ai_template_user` (`user_id`),
    KEY `idx_ai_template_is_system` (`is_system`),
    KEY `idx_ai_template_is_deleted` (`is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI内容模板表';

CREATE TABLE IF NOT EXISTS `t_ai_generation_history` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`         bigint       NOT NULL COMMENT '用户ID',
    `prompt`          text         NOT NULL COMMENT '原始提示词',
    `enhanced_prompt` text         DEFAULT NULL COMMENT '增强后的提示词',
    `result`          text         DEFAULT NULL COMMENT '生成结果',
    `provider_name`   varchar(50)  NOT NULL COMMENT '提供商名称',
    `model_name`      varchar(100) NOT NULL COMMENT '模型名称',
    `token_count`     int          DEFAULT NULL COMMENT 'Token 数量',
    `cost`            decimal(10, 4) DEFAULT NULL COMMENT '费用',
    `generation_time` bigint       DEFAULT NULL COMMENT '生成耗时（毫秒）',
    `create_time`     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      tinyint(1)   DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_history_user_created` (`user_id`, `create_time`),
    KEY `idx_ai_history_provider` (`provider_name`),
    KEY `idx_ai_history_is_deleted` (`is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI生成历史表';

CREATE TABLE IF NOT EXISTS `t_ai_usage_quota` (
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '配额ID',
    `user_id`          bigint      NOT NULL COMMENT '用户ID',
    `role_id`          bigint      DEFAULT NULL COMMENT '角色ID',
    `hourly_limit`     int         DEFAULT NULL COMMENT '每小时上限',
    `daily_limit`      int         DEFAULT NULL COMMENT '每日上限',
    `monthly_limit`    int         DEFAULT NULL COMMENT '每月上限',
    `hourly_used`      int         DEFAULT NULL COMMENT '每小时已用',
    `daily_used`       int         DEFAULT NULL COMMENT '每日已用',
    `monthly_used`     int         DEFAULT NULL COMMENT '每月已用',
    `last_reset_hour`  datetime    DEFAULT NULL COMMENT '上次小时重置时间',
    `last_reset_day`   datetime    DEFAULT NULL COMMENT '上次日重置时间',
    `last_reset_month` datetime    DEFAULT NULL COMMENT '上次月重置时间',
    `create_time`      datetime    DEFAULT NULL COMMENT '创建时间',
    `update_time`      datetime    DEFAULT NULL COMMENT '更新时间',
    `is_deleted`       tinyint(1)  DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_quota_user` (`user_id`),
    KEY `idx_ai_quota_role` (`role_id`),
    KEY `idx_ai_quota_is_deleted` (`is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI使用配额表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 附注
-- -----------------------------------------------------------------------------
-- 以下表在开发库中存在，但当前代码没有任何读写方（无实体、无 @Query）：
--   t_crawled_articles / t_crawled_images / t_crawler_platform_configs
-- 它们属于爬虫功能的遗留产物，本脚本不负责创建与迁移；
-- 如需启用爬虫功能，请在此补充对应 DDL 并同步补上实体与 Repository。
--
-- 历史上存在、现已随死代码删除的表（见 db/02_align_schema_and_indexes.sql）：
--   t_likes / t_file_logs / t_file_metadata / t_file_permissions /
--   t_file_shares / t_file_thumbnails / t_file_versions / t_network_disk
-- =============================================================================
