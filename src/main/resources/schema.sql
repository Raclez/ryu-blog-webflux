-- 用户表
CREATE TABLE IF NOT EXISTS `t_users` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `password` varchar(100) NOT NULL COMMENT '密码',
    `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
    `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
    `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
    `gender` tinyint(1) DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
    `birthday` datetime DEFAULT NULL COMMENT '生日',
    `bio` varchar(255) DEFAULT NULL COMMENT '个人简介',
    `status` tinyint(1) DEFAULT 1 COMMENT '用户状态：0-禁用，1-正常',
    `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
    `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `t_role` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `name` varchar(50) NOT NULL COMMENT '角色名称',
    `code` varchar(50) NOT NULL COMMENT '角色编码',
    `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `t_user_role` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 分类表
CREATE TABLE IF NOT EXISTS `t_categories` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` varchar(50) NOT NULL COMMENT '分类名称',
    `description` varchar(255) DEFAULT NULL COMMENT '分类描述',
    `parent_id` bigint(20) DEFAULT NULL COMMENT '父分类ID',
    `sort` int(11) DEFAULT 0 COMMENT '排序',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- 标签表
CREATE TABLE IF NOT EXISTS `t_tags` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` varchar(50) NOT NULL COMMENT '标签名称',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- 文章表
CREATE TABLE IF NOT EXISTS `t_posts` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    `title` varchar(100) NOT NULL COMMENT '文章标题',
    `content` longtext COMMENT '文章内容',
    `excerpt` varchar(255) DEFAULT NULL COMMENT '文章摘要',
    `cover_image_id` bigint(20) DEFAULT NULL COMMENT '封面图片ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `views` int(11) DEFAULT 0 COMMENT '浏览量',
    `status` tinyint(1) DEFAULT 1 COMMENT '文章状态：0-草稿，1-已发布',
    `seo_meta` varchar(500) DEFAULT NULL COMMENT 'SEO信息（关键词、描述等）json',
    `is_original` tinyint(1) DEFAULT 1 COMMENT '是否原创：0-否，1-是',
    `source_url` varchar(255) DEFAULT NULL COMMENT '来源URL',
    `sort` int(11) DEFAULT 0 COMMENT '排序权重',
    `allow_comment` tinyint(1) DEFAULT 1 COMMENT '是否允许评论：0-否，1-是',
    `visibility` varchar(20) DEFAULT 'public' COMMENT '访问权限: public, private, password',
    `password` varchar(100) DEFAULT NULL COMMENT '访问密码',
    `license` varchar(50) DEFAULT NULL COMMENT '许可证',
    `schedule_time` datetime DEFAULT NULL COMMENT '定时发布时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 文章标签关联表
CREATE TABLE IF NOT EXISTS `t_post_tags` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `post_id` bigint(20) NOT NULL COMMENT '文章ID',
    `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';

-- 文章分类关联表
CREATE TABLE IF NOT EXISTS `t_post_categories` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `post_id` bigint(20) NOT NULL COMMENT '文章ID',
    `category_id` bigint(20) NOT NULL COMMENT '分类ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_category` (`post_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类关联表';

-- 评论表
CREATE TABLE IF NOT EXISTS `t_comments` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `content` varchar(500) NOT NULL COMMENT '评论内容',
    `post_id` bigint(20) NOT NULL COMMENT '文章ID',
    `user_id` bigint(20) NOT NULL COMMENT '评论用户ID',
    `parent_id` bigint(20) DEFAULT NULL COMMENT '父评论ID',
    `to_user_id` bigint(20) DEFAULT NULL COMMENT '回复用户ID',
    `status` tinyint(1) DEFAULT 0 COMMENT '评论状态：0-待审核，1-已通过，2-已拒绝',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 文件表
CREATE TABLE IF NOT EXISTS `t_files` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `name` varchar(100) NOT NULL COMMENT '文件名',
    `path` varchar(255) NOT NULL COMMENT '文件路径',
    `url` varchar(255) NOT NULL COMMENT '文件URL',
    `type` varchar(50) DEFAULT NULL COMMENT '文件类型',
    `size` bigint(20) DEFAULT NULL COMMENT '文件大小，单位字节',
    `user_id` bigint(20) NOT NULL COMMENT '上传用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- 点赞表
CREATE TABLE IF NOT EXISTS `t_likes` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
    `type` tinyint(1) NOT NULL COMMENT '点赞类型：1-文章，2-评论',
    `target_id` bigint(20) NOT NULL COMMENT '目标ID（文章ID或评论ID）',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞表';

-- 收藏表
CREATE TABLE IF NOT EXISTS `t_favorites` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    `post_id` bigint(20) NOT NULL COMMENT '文章ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- 浏览历史表
CREATE TABLE IF NOT EXISTS `t_view_history` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `post_id` bigint(20) NOT NULL COMMENT '文章ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览历史表';

-- 文章版本表
CREATE TABLE IF NOT EXISTS `t_post_versions` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `post_id` bigint(20) NOT NULL COMMENT '文章ID',
    `version_number` int(11) NOT NULL COMMENT '版本号',
    `title` varchar(100) NOT NULL COMMENT '文章标题',
    `content` longtext COMMENT '文章内容',
    `excerpt` varchar(255) DEFAULT NULL COMMENT '文章摘要',
    `cover_image_id` bigint(20) DEFAULT NULL COMMENT '封面图片ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `description` varchar(255) DEFAULT NULL COMMENT '版本描述',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_version` (`post_id`, `version_number`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章版本表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `t_sys_config` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key` varchar(100) NOT NULL COMMENT '配置键',
    `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
    `description` varchar(255) DEFAULT NULL COMMENT '配置描述',
    `config_group` varchar(50) DEFAULT NULL COMMENT '配置分组',
    `is_system` tinyint(1) DEFAULT 0 COMMENT '是否系统内置：0-否，1-是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 系统菜单表
CREATE TABLE IF NOT EXISTS `t_sys_menu` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `name` varchar(50) NOT NULL COMMENT '菜单名称',
    `code` varchar(50) DEFAULT NULL COMMENT '菜单标识',
    `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
    `path` varchar(200) DEFAULT NULL COMMENT '菜单路径',
    `component` varchar(255) DEFAULT NULL COMMENT '菜单组件',
    `parent_id` bigint(20) DEFAULT 0 COMMENT '父菜单ID',
    `sort` int(11) DEFAULT 0 COMMENT '排序',
    `visible` tinyint(1) DEFAULT 1 COMMENT '是否可见：0-不可见，1-可见',
    `type` tinyint(1) DEFAULT 2 COMMENT '菜单类型：1-目录，2-菜单，3-按钮',
    `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
    `is_external` tinyint(1) DEFAULT 0 COMMENT '是否外链：0-否，1-是',
    `is_cache` tinyint(1) DEFAULT 0 COMMENT '是否缓存：0-否，1-是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS `t_sys_role_menu` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- 初始化角色数据
INSERT INTO `t_role` (`name`, `code`, `description`) VALUES 
('管理员', 'ADMIN', '系统管理员'),
('用户', 'USER', '普通用户'),
('编辑', 'EDITOR', '内容编辑')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `description`=VALUES(`description`);

-- 初始化管理员用户（密码：admin123）
INSERT INTO `t_users` (`username`, `password`, `nickname`, `avatar`, `email`, `status`) VALUES 
('admin', '$2a$10$3KMxI0F0XhWQV/CXR0QSZuM9mFZSfv0FwQKkXrYYRGBCvLIIZbZDy', '管理员', 'https://avatars.githubusercontent.com/u/1?v=4', 'admin@example.com', 1)
ON DUPLICATE KEY UPDATE `password`=VALUES(`password`), `nickname`=VALUES(`nickname`), `avatar`=VALUES(`avatar`), `email`=VALUES(`email`), `status`=VALUES(`status`);

-- 初始化管理员角色关联
INSERT INTO `t_user_role` (`user_id`, `role_id`) 
SELECT u.id, r.id FROM t_users u, t_role r 
WHERE u.username = 'admin' AND r.code = 'ADMIN'
ON DUPLICATE KEY UPDATE `user_id`=VALUES(`user_id`), `role_id`=VALUES(`role_id`);

-- 初始化分类数据
INSERT INTO `t_categories` (`name`, `description`, `sort`) VALUES 
('技术', '技术相关文章', 1),
('生活', '生活相关文章', 2),
('随笔', '随笔记录', 3)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `description`=VALUES(`description`), `sort`=VALUES(`sort`);

-- 初始化标签数据
INSERT INTO `t_tags` (`name`) VALUES 
('Java'),
('Spring Boot'),
('React'),
('Vue'),
('MySQL'),
('Redis'),
('Docker'),
('Kubernetes'),
('微服务'),
('DevOps')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

-- 初始化系统配置数据
INSERT INTO `t_sys_config` (`config_key`, `config_value`, `description`, `config_group`, `is_system`) VALUES 
('site.name', 'Ryu Blog', '站点名称', 'site', 1),
('site.description', '一个基于Spring Boot WebFlux的响应式博客系统', '站点描述', 'site', 1),
('site.keywords', 'blog,spring,webflux,reactive', '站点关键词', 'site', 1),
('site.logo', '/static/images/logo.png', '站点Logo', 'site', 1),
('site.favicon', '/static/images/favicon.ico', '站点图标', 'site', 1),
('site.footer', 'Copyright © 2023 Ryu Blog', '站点页脚', 'site', 1),
('upload.path', '/data/uploads', '上传路径', 'upload', 1),
('upload.max_size', '10485760', '上传最大大小（字节）', 'upload', 1),
('upload.allowed_types', 'jpg,jpeg,png,gif,doc,docx,pdf,zip,rar', '允许上传的文件类型', 'upload', 1)
ON DUPLICATE KEY UPDATE `config_value`=VALUES(`config_value`), `description`=VALUES(`description`), `config_group`=VALUES(`config_group`), `is_system`=VALUES(`is_system`);

-- 初始化系统菜单数据
INSERT INTO `t_sys_menu` (`name`, `code`, `icon`, `path`, `component`, `parent_id`, `sort`, `visible`, `type`, `permission`) VALUES 
-- 仪表盘
('仪表盘', 'dashboard', 'dashboard', '/dashboard', 'Dashboard', 0, 1, 1, 2, 'dashboard'),

-- 内容管理
('内容管理', 'content', 'file-text', '/content', null, 0, 2, 1, 1, null),
('文章管理', 'article', 'file', '/content/article', 'Article/List', 2, 1, 1, 2, 'content:article:list'),
('文章添加', 'article:add', null, null, null, 3, 1, 1, 3, 'content:article:add'),
('文章编辑', 'article:edit', null, null, null, 3, 2, 1, 3, 'content:article:edit'),
('文章删除', 'article:delete', null, null, null, 3, 3, 1, 3, 'content:article:delete'),
('分类管理', 'category', 'folder', '/content/category', 'Category/List', 2, 2, 1, 2, 'content:category:list'),
('分类添加', 'category:add', null, null, null, 7, 1, 1, 3, 'content:category:add'),
('分类编辑', 'category:edit', null, null, null, 7, 2, 1, 3, 'content:category:edit'),
('分类删除', 'category:delete', null, null, null, 7, 3, 1, 3, 'content:category:delete'),
('标签管理', 'tag', 'tag', '/content/tag', 'Tag/List', 2, 3, 1, 2, 'content:tag:list'),
('标签添加', 'tag:add', null, null, null, 11, 1, 1, 3, 'content:tag:add'),
('标签编辑', 'tag:edit', null, null, null, 11, 2, 1, 3, 'content:tag:edit'),
('标签删除', 'tag:delete', null, null, null, 11, 3, 1, 3, 'content:tag:delete'),
('评论管理', 'comment', 'message-square', '/content/comment', 'Comment/List', 2, 4, 1, 2, 'content:comment:list'),
('评论审核', 'comment:review', null, null, null, 15, 1, 1, 3, 'content:comment:review'),
('评论删除', 'comment:delete', null, null, null, 15, 2, 1, 3, 'content:comment:delete'),

-- 用户管理
('用户管理', 'user', 'users', '/user', null, 0, 3, 1, 1, null),
('用户列表', 'user:list', 'user', '/user/list', 'User/List', 18, 1, 1, 2, 'user:list'),
('用户添加', 'user:add', null, null, null, 19, 1, 1, 3, 'user:add'),
('用户编辑', 'user:edit', null, null, null, 19, 2, 1, 3, 'user:edit'),
('用户删除', 'user:delete', null, null, null, 19, 3, 1, 3, 'user:delete'),
('角色管理', 'role', 'shield', '/user/role', 'Role/List', 18, 2, 1, 2, 'role:list'),
('角色添加', 'role:add', null, null, null, 23, 1, 1, 3, 'role:add'),
('角色编辑', 'role:edit', null, null, null, 23, 2, 1, 3, 'role:edit'),
('角色删除', 'role:delete', null, null, null, 23, 3, 1, 3, 'role:delete'),
('角色授权', 'role:assign', null, null, null, 23, 4, 1, 3, 'role:assign'),

-- 系统管理
('系统管理', 'system', 'settings', '/system', null, 0, 4, 1, 1, null),
('菜单管理', 'menu', 'menu', '/system/menu', 'Menu/List', 28, 1, 1, 2, 'menu:list'),
('菜单添加', 'menu:add', null, null, null, 29, 1, 1, 3, 'menu:add'),
('菜单编辑', 'menu:edit', null, null, null, 29, 2, 1, 3, 'menu:edit'),
('菜单删除', 'menu:delete', null, null, null, 29, 3, 1, 3, 'menu:delete'),
('系统配置', 'config', 'tool', '/system/config', 'Config/List', 28, 2, 1, 2, 'config:list'),
('配置添加', 'config:add', null, null, null, 33, 1, 1, 3, 'config:add'),
('配置编辑', 'config:edit', null, null, null, 33, 2, 1, 3, 'config:edit'),
('配置删除', 'config:delete', null, null, null, 33, 3, 1, 3, 'config:delete')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `icon`=VALUES(`icon`), `path`=VALUES(`path`), `component`=VALUES(`component`), `parent_id`=VALUES(`parent_id`), `sort`=VALUES(`sort`), `visible`=VALUES(`visible`), `type`=VALUES(`type`), `permission`=VALUES(`permission`);

-- 初始化管理员角色菜单关联
INSERT INTO `t_sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id FROM t_role r, t_sys_menu m
WHERE r.code = 'ADMIN'
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`), `menu_id`=VALUES(`menu_id`);

-- 初始化编辑角色菜单关联（仅内容管理相关菜单）
INSERT INTO `t_sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id FROM t_role r, t_sys_menu m
WHERE r.code = 'EDITOR' AND (m.id = 1 OR m.id = 2 OR (m.id >= 3 AND m.id <= 17))
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`), `menu_id`=VALUES(`menu_id`);

-- 修复表名不一致的问题
-- 将t_article_tags改为t_post_tags
RENAME TABLE IF EXISTS `t_article_tags` TO `t_post_tags`;

-- 修改表结构，将article_id改为post_id
ALTER TABLE `t_post_tags` 
CHANGE COLUMN `article_id` `post_id` bigint(20) NOT NULL COMMENT '文章ID';

-- 修正评论表中的article_id为post_id
ALTER TABLE `t_comments` 
CHANGE COLUMN `article_id` `post_id` bigint(20) NOT NULL COMMENT '文章ID';

-- 修正收藏表中的article_id为post_id
ALTER TABLE `t_favorites` 
CHANGE COLUMN `article_id` `post_id` bigint(20) NOT NULL COMMENT '文章ID';

-- 修正浏览历史表中的article_id为post_id
ALTER TABLE `t_view_history` 
CHANGE COLUMN `article_id` `post_id` bigint(20) NOT NULL COMMENT '文章ID';

-- 修正文章版本表中的article_id为post_id
ALTER TABLE `t_post_versions` 
CHANGE COLUMN `article_id` `post_id` bigint(20) NOT NULL COMMENT '文章ID',
CHANGE COLUMN `version` `version_number` int(11) NOT NULL COMMENT '版本号',
ADD COLUMN `version_tag` varchar(50) NULL COMMENT '版本标签' AFTER `version_number`;

-- 重命名唯一键
ALTER TABLE `t_post_versions` 
DROP INDEX `uk_article_version`,
ADD UNIQUE INDEX `uk_post_version` (`post_id`, `version_number`);

-- 系统字典类型表
CREATE TABLE IF NOT EXISTS `t_sys_dict_type` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
    `dict_type` varchar(100) NOT NULL COMMENT '字典类型编码，唯一',
    `type_name` varchar(100) NOT NULL COMMENT '字典类型名称',
    `status` tinyint(1) DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典类型表';

-- 系统字典项表
CREATE TABLE IF NOT EXISTS `t_sys_dict_item` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
    `dict_type_id` bigint(20) NOT NULL COMMENT '所属字典类型ID',
    `dict_item_key` varchar(100) NOT NULL COMMENT '字典项键',
    `dict_item_value` varchar(255) NOT NULL COMMENT '字典项值',
    `sort` int(11) DEFAULT 0 COMMENT '排序',
    `status` tinyint(1) DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `lang` varchar(20) DEFAULT NULL COMMENT '语言标识',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item` (`dict_type_id`, `dict_item_key`),
    KEY `idx_dict_type_id` (`dict_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典项表';

-- 文件版本表
CREATE TABLE IF NOT EXISTS `t_file_versions` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `file_id` bigint(20) NOT NULL COMMENT '文件ID，关联files表',
    `version_number` int(11) NOT NULL COMMENT '版本号',
    `version_tag` varchar(50) DEFAULT NULL COMMENT '版本标签',
    `file_path` varchar(255) NOT NULL COMMENT '文件路径',
    `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小(字节)',
    `checksum` varchar(100) DEFAULT NULL COMMENT '文件校验码',
    `storage_type` varchar(20) DEFAULT 'local' COMMENT '存储类型(local, minio, oss等)',
    `creator_id` bigint(20) NOT NULL COMMENT '创建者ID',
    `description` varchar(255) DEFAULT NULL COMMENT '版本描述',
    `is_current` tinyint(1) DEFAULT 0 COMMENT '是否是当前版本(0:否, 1:是)',
    `has_thumbnail` tinyint(1) DEFAULT 0 COMMENT '是否有缩略图',
    `thumbnail_path` varchar(255) DEFAULT NULL COMMENT '缩略图路径',
    `mime_type` varchar(100) DEFAULT NULL COMMENT 'MIME类型',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_version` (`file_id`, `version_number`),
    KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件版本表';

-- 文件日志表
CREATE TABLE IF NOT EXISTS `t_file_logs` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志记录的唯一标识',
    `file_id` bigint(20) NOT NULL COMMENT '对应文件ID',
    `operation` varchar(50) NOT NULL COMMENT '文件操作类型（如：upload、delete、download等）',
    `user_id` bigint(20) NOT NULL COMMENT '执行操作的用户ID',
    `timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作的时间',
    `details` text DEFAULT NULL COMMENT '操作的详细信息（如错误信息、操作参数等）',
    `description` varchar(255) DEFAULT NULL COMMENT '操作的描述',
    PRIMARY KEY (`id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件日志表';

-- 文件权限表
CREATE TABLE IF NOT EXISTS `t_file_permissions` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '权限记录的唯一标识',
    `file_id` bigint(20) NOT NULL COMMENT '文件的ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `permission_mask` int(11) NOT NULL COMMENT '授权权限掩码，使用位掩码存储：1-读, 3-写(含读), 7-删除(含读写), 8-分享',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '权限分配的时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `expire_time` datetime DEFAULT NULL COMMENT '过期时间，null表示永不过期',
    `grantor_id` bigint(20) NOT NULL COMMENT '授权人ID，谁分配的该权限',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_user` (`file_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_grantor_id` (`grantor_id`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件权限表';

-- 文件分享表
CREATE TABLE IF NOT EXISTS `t_file_shares` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分享记录的唯一标识',
    `file_id` bigint(20) NOT NULL COMMENT '分享的文件ID',
    `user_id` bigint(20) NOT NULL COMMENT '分享者用户ID',
    `share_key` varchar(50) NOT NULL COMMENT '分享链接的唯一标识符',
    `password` varchar(50) DEFAULT NULL COMMENT '访问分享的密码（可为空）',
    `expire_time` datetime DEFAULT NULL COMMENT '分享链接的过期时间',
    `max_downloads` int(11) DEFAULT 0 COMMENT '允许的最大下载次数（0表示不限制）',
    `download_count` int(11) DEFAULT 0 COMMENT '已下载次数',
    `allow_anonymous` tinyint(1) DEFAULT 0 COMMENT '是否允许匿名访问',
    `status` tinyint(1) DEFAULT 1 COMMENT '分享的状态（1-有效；0-已取消；2-已过期）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分享的创建时间',
    `last_access_time` datetime DEFAULT NULL COMMENT '最后访问时间',
    `description` varchar(255) DEFAULT NULL COMMENT '分享说明',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_key` (`share_key`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分享表';

-- 文件缩略图表
CREATE TABLE IF NOT EXISTS `t_file_thumbnails` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '缩略图记录的唯一标识',
    `file_id` bigint(20) NOT NULL COMMENT '对应的文件ID',
    `size` varchar(20) DEFAULT NULL COMMENT '缩略图大小 (宽度x高度)',
    `width` int(11) DEFAULT NULL COMMENT '缩略图宽度',
    `height` int(11) DEFAULT NULL COMMENT '缩略图高度',
    `file_path` varchar(255) NOT NULL COMMENT '缩略图文件路径',
    `file_size` bigint(20) DEFAULT NULL COMMENT '缩略图文件大小(字节)',
    `storage_type` varchar(20) DEFAULT 'local' COMMENT '缩略图存储类型(local, minio, oss等)',
    `format` varchar(20) DEFAULT NULL COMMENT '缩略图格式(例如 jpg, png)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件缩略图表';

-- 文件元数据表
CREATE TABLE IF NOT EXISTS `t_file_metadata` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '元数据记录的唯一标识',
    `file_id` bigint(20) NOT NULL COMMENT '对应的文件ID',
    `metadata_type` varchar(50) NOT NULL COMMENT '元数据类型（例如：image, document, audio, video等）',
    `metadata_key` varchar(100) NOT NULL COMMENT '元数据键',
    `metadata_value` text DEFAULT NULL COMMENT '元数据值',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_metadata` (`file_id`, `metadata_type`, `metadata_key`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_metadata_type` (`metadata_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

-- 资源组表
CREATE TABLE IF NOT EXISTS `t_resource_groups` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '资源组唯一标识',
    `name` varchar(100) NOT NULL COMMENT '资源组名称',
    `description` varchar(255) DEFAULT NULL COMMENT '资源组描述',
    `creator_id` bigint(20) NOT NULL COMMENT '创建者用户ID',
    `parent_id` bigint(20) DEFAULT NULL COMMENT '父资源组ID，顶级资源组为NULL',
    `type` varchar(50) DEFAULT NULL COMMENT '资源组类型（如：project, gallery, folder等）',
    `path` varchar(500) DEFAULT NULL COMMENT '资源组路径，类似于文件系统路径',
    `status` tinyint(1) DEFAULT 1 COMMENT '资源组状态（1-正常，0-已删除）',
    `sort` int(11) DEFAULT 0 COMMENT '排序号',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源组表';

-- 资源组文件关联表
CREATE TABLE IF NOT EXISTS `t_resource_group_file_rel` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联记录唯一标识',
    `group_id` bigint(20) NOT NULL COMMENT '资源组ID',
    `file_id` bigint(20) NOT NULL COMMENT '文件ID',
    `user_id` bigint(20) NOT NULL COMMENT '添加文件的用户ID',
    `sort` int(11) DEFAULT 0 COMMENT '文件在资源组中的排序位置',
    `display_name` varchar(255) DEFAULT NULL COMMENT '文件在资源组中的显示名称',
    `is_cover` tinyint(1) DEFAULT 0 COMMENT '是否为封面文件(0-否, 1-是)',
    `status` tinyint(1) DEFAULT 1 COMMENT '关联状态(1-正常, 0-已删除)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_file` (`group_id`, `file_id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源组文件关联表';

-- 存储配置表
CREATE TABLE IF NOT EXISTS `t_storage_config` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置记录唯一标识',
    `name` varchar(100) NOT NULL COMMENT '存储配置名称',
    `type` varchar(50) NOT NULL COMMENT '存储类型(local, minio, oss, cos等)',
    `config_json` text NOT NULL COMMENT '存储配置键值对，JSON格式',
    `is_default` tinyint(1) DEFAULT 0 COMMENT '是否为默认存储配置(0-否, 1-是)',
    `status` tinyint(1) DEFAULT 1 COMMENT '是否启用(0-禁用, 1-启用)',
    `creator_id` bigint(20) NOT NULL COMMENT '创建者ID',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注说明',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储配置表';

-- 网络磁盘表
CREATE TABLE IF NOT EXISTS `t_network_disk` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录唯一标识',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `filename` varchar(255) NOT NULL COMMENT '文件名',
    `file_path` varchar(500) NOT NULL COMMENT '文件路径，相对于用户网盘根目录',
    `file_type` varchar(20) NOT NULL COMMENT '文件类型(文件夹-folder, 文件-file)',
    `file_size` bigint(20) DEFAULT 0 COMMENT '文件大小(字节)，文件夹为0',
    `mime_type` varchar(100) DEFAULT NULL COMMENT '文件MIME类型',
    `parent_id` bigint(20) DEFAULT NULL COMMENT '父目录ID，根目录为NULL',
    `storage_file_id` bigint(20) DEFAULT NULL COMMENT '实际存储文件的ID，文件夹为NULL',
    `is_favorite` tinyint(1) DEFAULT 0 COMMENT '是否为收藏(0-否, 1-是)',
    `status` tinyint(1) DEFAULT 1 COMMENT '文件状态(1-正常, 0-已删除, 2-回收站)',
    `remark` varchar(255) DEFAULT NULL COMMENT '文件备注',
    `delete_time` datetime DEFAULT NULL COMMENT '删除时间，用于回收站清理',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_file_path` (`file_path`(255)),
    KEY `idx_delete_time` (`delete_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网络磁盘表';

-- 初始化系统字典类型数据
INSERT INTO `t_sys_dict_type` (`dict_type`, `type_name`, `status`, `remark`) VALUES 
('file_type', '文件类型', 1, '文件类型分类'),
('storage_type', '存储类型', 1, '文件存储类型'),
('user_status', '用户状态', 1, '用户状态类型'),
('article_status', '文章状态', 1, '文章状态类型'),
('comment_status', '评论状态', 1, '评论审核状态'),
('permission_type', '权限类型', 1, '文件权限类型')
ON DUPLICATE KEY UPDATE `type_name`=VALUES(`type_name`), `status`=VALUES(`status`), `remark`=VALUES(`remark`);

-- 初始化系统字典项数据
INSERT INTO `t_sys_dict_item` (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`) 
SELECT dt.id, 'image', '图片', 1, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'file_type'
UNION ALL
SELECT dt.id, 'document', '文档', 2, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'file_type'
UNION ALL
SELECT dt.id, 'video', '视频', 3, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'file_type'
UNION ALL
SELECT dt.id, 'audio', '音频', 4, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'file_type'
UNION ALL
SELECT dt.id, 'archive', '归档', 5, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'file_type'
UNION ALL
SELECT dt.id, 'other', '其他', 6, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'file_type'
UNION ALL
SELECT dt.id, 'local', '本地存储', 1, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'storage_type'
UNION ALL
SELECT dt.id, 'minio', 'MinIO对象存储', 2, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'storage_type'
UNION ALL
SELECT dt.id, 'oss', '阿里云OSS', 3, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'storage_type'
UNION ALL
SELECT dt.id, 'cos', '腾讯云COS', 4, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'storage_type'
UNION ALL
SELECT dt.id, 's3', 'Amazon S3', 5, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'storage_type'
UNION ALL
SELECT dt.id, 'disabled', '禁用', 1, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'user_status'
UNION ALL
SELECT dt.id, 'normal', '正常', 2, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'user_status'
UNION ALL
SELECT dt.id, 'locked', '锁定', 3, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'user_status'
UNION ALL
SELECT dt.id, 'draft', '草稿', 1, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'article_status'
UNION ALL
SELECT dt.id, 'published', '已发布', 2, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'article_status'
UNION ALL
SELECT dt.id, 'scheduled', '定时发布', 3, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'article_status'
UNION ALL
SELECT dt.id, 'pending', '待审核', 1, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'comment_status'
UNION ALL
SELECT dt.id, 'approved', '已通过', 2, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'comment_status'
UNION ALL
SELECT dt.id, 'rejected', '已拒绝', 3, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'comment_status'
UNION ALL
SELECT dt.id, 'read', '读取', 1, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'permission_type'
UNION ALL
SELECT dt.id, 'write', '修改', 2, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'permission_type'
UNION ALL
SELECT dt.id, 'delete', '删除', 3, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'permission_type'
UNION ALL
SELECT dt.id, 'share', '分享', 4, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'permission_type'
UNION ALL
SELECT dt.id, 'all', '所有权限', 5, 1 FROM `t_sys_dict_type` dt WHERE dt.dict_type = 'permission_type'
ON DUPLICATE KEY UPDATE `dict_item_value`=VALUES(`dict_item_value`), `sort`=VALUES(`sort`), `status`=VALUES(`status`);