-- =============================================================================
-- 12_menu_route_name.sql — t_menus 增加 route_name（前端路由名，幂等）
--
-- 背景：
--   vben 的「后端驱动菜单」模式把后端菜单直接转成 vue-router 路由，
--   而 vue-router 需要**稳定的 ASCII name**（keep-alive、按 name 去重、
--   以及 generateRoutesByBackend 的组件映射都依赖它）。
--   t_menus 原本只有 name（中文，可随时改），没有可用的路由标识。
--
-- 本脚本只做 DDL；菜单数据由 13_menu_routes_seed.sql 重建。
-- 唯一键允许 NULL：存量行在补数据之前保持 NULL 是合法的（MySQL 唯一索引不约束多个 NULL）。
-- =============================================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_menus' AND COLUMN_NAME = 'route_name'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `t_menus` ADD COLUMN `route_name` varchar(64) DEFAULT NULL COMMENT ''前端路由名（vue-router 的 name）'' AFTER `name`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_menus' AND INDEX_NAME = 'uk_menus_route_name'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE `t_menus` ADD UNIQUE KEY `uk_menus_route_name` (`route_name`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
