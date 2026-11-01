-- =============================================================================
-- 15_remove_dashboard_menu.sql — 首页移出菜单表（幂等，软删）
--
-- 背景：
--   首页（/dashboard）此前是 t_menus 里一条 hidden=1 的菜单行。
--   它不在侧边栏显示，却会出现在「菜单管理」列表里，看起来像一条无意义的配置。
--
--   首页不是"可配置的菜单"，而是应用的固定入口：改为前端核心路由
--   （apps/web-ele/src/router/routes/core.ts 的 Root.children），两种 accessMode
--   下都始终存在；DEFAULT_HOME_PATH 仍是 /dashboard，登录后照常落在首页。
--
-- 幂等：软删而不是物理删 —— 回滚把 is_deleted 改回 0 即可。
-- =============================================================================

UPDATE `t_menus`
   SET `is_deleted` = 1, `update_time` = NOW()
 WHERE `id` = 8 OR `route_name` = 'Dashboard';

UPDATE `t_sys_role_menus`
   SET `is_deleted` = 1, `update_time` = NOW()
 WHERE `menu_id` = 8;
