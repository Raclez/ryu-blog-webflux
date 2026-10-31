-- =============================================================================
-- 08_dict_alignment.sql — 字典与后端契约对齐（幂等，可重复执行）
--
-- 背景：
--   1) article_status 的字典项 key 是 draft/published/pending/offline，
--      而 t_posts.status 一直是 Integer 0..3（PostQueryDTO.status 也是 Integer）。
--      前端把字典的 value 原样塞进查询参数，于是「状态筛选」一选就发
--      status=draft 给整数字段 —— 要么绑定失败，要么筛不出任何结果。
--   2) 看板待办（blog-todo-list.vue）读 todo_priority，但库里根本没有这个类型，
--      优先级下拉是空的。
--
-- 幂等性依赖：
--   - t_sys_dict_types.uk_dict_type (dict_type)
--   - t_sys_dict_items.uk_dict_type_key (dict_type_id, dict_item_key)
--   两条唯一键都由 apply-ddl.sh 第四节保证存在。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 字典类型
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_dict_types`
    (`type_name`, `dict_type`, `remark`, `status`, `create_time`, `update_time`, `is_deleted`)
VALUES
    ('文章状态', 'article_status',
     '与 t_posts.status / PostQueryDTO.status 一致：0-待发布 1-已发布 2-草稿 3-已归档',
     1, NOW(), NOW(), 0),
    ('待办优先级', 'todo_priority',
     '看板待办的优先级，key 与前端 TodoItem.priority 一致（high/medium/low），与后端 TaskPriority 枚举无关',
     1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    `type_name`   = VALUES(`type_name`),
    `remark`      = VALUES(`remark`),
    `status`      = 1,
    `is_deleted`  = 0,
    `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 2. 清掉 article_status 的旧字符串键
--
--    这里必须**硬删**，不能用 is_deleted = 1 软删：
--    uk_dict_type_key(dict_type_id, dict_item_key) 不区分 is_deleted，
--    软删的行会永久占住 'draft' 这个 key —— 以后想重新启用都插不进去。
-- -----------------------------------------------------------------------------
DELETE i FROM `t_sys_dict_items` i
JOIN `t_sys_dict_types` t ON t.`id` = i.`dict_type_id`
WHERE t.`dict_type` = 'article_status'
  AND i.`dict_item_key` IN ('draft', 'published', 'pending', 'offline');

-- -----------------------------------------------------------------------------
-- 3. 文章状态：数字键（与 Posts.Status 一一对应）
--
--    key 用数字字符串而不是汉字，因为它同时是前端提交给后端的值：
--    Number('1') === PostsApi.PostStatus.PUBLISHED。
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, s.`remark`, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT '0' AS `k`, '待发布' AS `v`, 1 AS `sort`, '待发布 / 定时发布' AS `remark`
    UNION ALL SELECT '1', '已发布', 2, '已发布，公开可见'
    UNION ALL SELECT '2', '草稿',   3, '尚未发布'
    UNION ALL SELECT '3', '已归档', 4, '不再活跃展示'
) s
WHERE dt.`dict_type` = 'article_status'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `remark`          = VALUES(`remark`),
    `status`          = 1,
    `is_deleted`      = 0,
    `update_time`     = NOW();

-- -----------------------------------------------------------------------------
-- 4. 待办优先级
--
--    key 必须与前端 TodoItem.priority（'high' | 'low' | 'medium'）逐字一致，
--    前端是拿它当值直接用的。不要改成数字 —— 那是后端 TaskPriority 的语义。
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, NULL, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT 'high'   AS `k`, '高' AS `v`, 1 AS `sort`
    UNION ALL SELECT 'medium', '中', 2
    UNION ALL SELECT 'low',    '低', 3
) s
WHERE dt.`dict_type` = 'todo_priority'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `status`          = 1,
    `is_deleted`      = 0,
    `update_time`     = NOW();
