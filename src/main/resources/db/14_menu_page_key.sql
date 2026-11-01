-- =============================================================================
-- 14_menu_page_key.sql — 菜单组件路径改写为约定 page_key（幂等）
--
-- 背景：
--   切到「后端驱动菜单」后，component 由管理员在菜单管理里手填完整路径
--   （views/features/user/UserList.vue）。写错一个字母，路由照样能建出来，
--   点进去却是 not-found，且页面上没有任何提示。
--
--   改为约定 key：user/UserList → views/features/user/UserList.vue
--   解析规则在前端 apps/web-ele/src/utils/page-registry.ts：
--     - 含 .vue  → 当作显式路径（相对 src）
--     - 不含 .vue → views/features/{key}.vue，其次 views/features/{key}/index.vue
--
--   本脚本只改 views/features 下的 22 条；views/api-doc/index.vue 与
--   views/dashboard/... 保持显式路径（它们不在 features 目录，key 规则不覆盖）。
--
-- 幂等：改写后的值不再以 views/features/ 开头，重复执行影响 0 行。
-- =============================================================================

UPDATE `t_menus`
   SET `component`   = REPLACE(REPLACE(`component`, 'views/features/', ''), '.vue', ''),
       `update_time` = NOW()
 WHERE `component` LIKE 'views/features/%';
