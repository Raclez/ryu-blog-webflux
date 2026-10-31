-- =============================================================================
-- 10_dict_key_alignment.sql — 把字典 key 改成「代码里实际在用的值」（幂等）
--
-- 背景：
--   这三个字典建了项，但 key 与代码里的值对不上，前端即使拿到也匹配不上 ——
--   等于字典存在但用不了。与 08（article_status 改 0..3）是同一条原则：
--   **字典的 key 必须就是后端契约/前端提交的那个值**，否则它只是好看的文案表。
--
--   | 字典        | 原 key                          | 代码实际用的值                    |
--   |-------------|---------------------------------|-----------------------------------|
--   | file_type   | image/video/audio/document/other | Image/Video/Audio/Document/Other  |
--   |             |                                 | （FileUtils.getFileType 的返回值） |
--   | user_status | normal/disabled/locked           | 1 正常 / 0 禁用 / 2 锁定（Integer）|
--   | menu_type   | directory/menu/button            | 0 目录 / 1 菜单（Integer）         |
--
--   ⚠️ FileUtils.getFileType 实际会返回十几种值（Text/Pdf/Spreadsheet/Presentation/
--   Archive/Json/Xml/Markdown/unknown…），但界面只提供这 5 个筛选项。
--   这里只对齐「界面上能选的那 5 个」，不替产品决定要不要把全部类型做成筛选项 ——
--   也就是说 PDF / 纯文本 / 压缩包 目前在「类型筛选」里筛不出来，只能从「全部」里找。
--   注意「其他」的 key 是**小写 other**，与后端 MIME 识别失败时的兜底值一致。
--
--   menu_type 的 button 项要一并删掉：按钮级权限已改由 permission identity 承载，
--   后端种子脚本也清理过 type=2 的按钮型菜单，字典里留着它只会误导。
--
-- 幂等性依赖 uk_dict_type_key(dict_type_id, dict_item_key)，由 apply-ddl.sh 第四节保证。
-- ⚠️ 这些表通常是 *_ci 排序规则（大小写不敏感），所以 'image' 与 'Image' 会撞同一个
--    唯一键 —— 必须先删旧键再插新键，顺序不能反。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. file_type → 与 FileUtils.getFileType 的返回值一致（首字母大写）
-- -----------------------------------------------------------------------------
DELETE i FROM `t_sys_dict_items` i
JOIN `t_sys_dict_types` t ON t.`id` = i.`dict_type_id`
WHERE t.`dict_type` = 'file_type'
  AND i.`dict_item_key` IN ('image', 'video', 'audio', 'document', 'Other', 'IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT');

INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, s.`remark`, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT 'Image' AS `k`, '图片' AS `v`, 1 AS `sort`, '后端 file_type = Image' AS `remark`
    UNION ALL SELECT 'Video',    '视频', 2, '后端 file_type = Video'
    UNION ALL SELECT 'Audio',    '音频', 3, '后端 file_type = Audio'
    UNION ALL SELECT 'Document', '文档', 4, '后端 file_type = Document'
    -- 小写：FileUtils.getFileType 识别不出来时返回的就是 "other"
    UNION ALL SELECT 'other',    '其他', 5, '后端 file_type = other'
) s
WHERE dt.`dict_type` = 'file_type'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `remark`          = VALUES(`remark`),
    `status`          = 1,
    `is_deleted`      = 0,
    `update_time`     = NOW();

-- -----------------------------------------------------------------------------
-- 2. user_status → 与 t_users.status 的数值一致（1 正常 / 0 禁用 / 2 锁定）
-- -----------------------------------------------------------------------------
DELETE i FROM `t_sys_dict_items` i
JOIN `t_sys_dict_types` t ON t.`id` = i.`dict_type_id`
WHERE t.`dict_type` = 'user_status'
  AND i.`dict_item_key` IN ('normal', 'disabled', 'locked');

INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, s.`remark`, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT '1' AS `k`, '正常' AS `v`, 1 AS `sort`, 'Integer status = 1' AS `remark`
    UNION ALL SELECT '0', '禁用', 2, 'Integer status = 0'
    UNION ALL SELECT '2', '锁定', 3, 'Integer status = 2'
) s
WHERE dt.`dict_type` = 'user_status'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `remark`          = VALUES(`remark`),
    `status`          = 1,
    `is_deleted`      = 0,
    `update_time`     = NOW();

-- -----------------------------------------------------------------------------
-- 3. menu_type → 与 t_menus.type 的数值一致（0 目录 / 1 菜单）
--
--    button（原 type=2）已随「菜单不再承载权限」下线，删掉不再重建。
-- -----------------------------------------------------------------------------
DELETE i FROM `t_sys_dict_items` i
JOIN `t_sys_dict_types` t ON t.`id` = i.`dict_type_id`
WHERE t.`dict_type` = 'menu_type'
  AND i.`dict_item_key` IN ('directory', 'menu', 'button');

INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, s.`remark`, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT '0' AS `k`, '目录' AS `v`, 1 AS `sort`, 'Integer menuType = 0' AS `remark`
    UNION ALL SELECT '1', '菜单', 2, 'Integer menuType = 1'
) s
WHERE dt.`dict_type` = 'menu_type'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `remark`          = VALUES(`remark`),
    `status`          = 1,
    `is_deleted`      = 0,
    `update_time`     = NOW();
