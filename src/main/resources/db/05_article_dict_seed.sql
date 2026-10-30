-- =============================================================================
-- 05_article_dict_seed.sql — 文章模块的字典种子数据（幂等，可重复执行）
--
-- 背景：
--   前端文章编辑器用字典渲染「访问权限」与「许可协议」两个下拉/单选：
--     GET /sysDictItem/type/article_visibility
--     GET /sysDictItem/type/article_license
--   但**整个字典表从来没有种子数据** —— schema.sql 只建表，03_rbac_seed.sql 只管 RBAC。
--   结果：开发库里查不到 article_visibility → 接口返回空数组 →
--   编辑器上的「访问权限」单选组**一个选项都没有**，用户根本没法改可见性
--   （而后端 PostCreateDTO 又在校验 `^(public|private|password)$`，
--     契约是存在的，只是前端拿不到选项）。
--
-- 这里的字典项就是后端契约本身：
--   article_visibility 的三个 key 必须与
--     - Posts.Visibility（public / private / password）
--     - PostCreateDTO/PostUpdateDTO 的 @Pattern("^(public|private|password)$")
--   完全一致 —— 少一个、多一个，编辑器就会出现「选了却存不进去」的选项。
--
-- 幂等性依赖：
--   - t_sys_dict_types.uk_dict_type (dict_type)
--   - t_sys_dict_items.uk_dict_type_key (dict_type_id, dict_item_key)
--   两条唯一键都由 apply-ddl.sh 第四节保证存在。
--
-- ⚠️ 唯一键不在时 ON DUPLICATE KEY UPDATE 不会触发 —— 重跑会**静默插出重复项**
--    （重复的字典项会让前端的「访问权限」单选框出现两个「公开访问」）。
--    所以统一跑 `bash apply-ddl.sh`（它会先建唯一键，再调本文件）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 字典类型
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_dict_types`
    (`type_name`, `dict_type`, `remark`, `status`, `create_time`, `update_time`, `is_deleted`)
VALUES
    ('文章访问权限', 'article_visibility',
     '与 t_posts.visibility 一致：public-公开，private-私密，password-密码访问', 1, NOW(), NOW(), 0),
    ('文章许可协议', 'article_license',
     't_posts.license 的可选值', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    `type_name`   = VALUES(`type_name`),
    `remark`      = VALUES(`remark`),
    `status`      = 1,
    `update_time` = NOW();

-- -----------------------------------------------------------------------------
-- 2. 访问权限字典项
--
--    用 INSERT ... SELECT + 关联字典类型来取 dict_type_id：
--    类型 id 是自增的，写死会跟开发库里已有的行撞号。
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, s.`remark`, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT 'public'   AS `k`, '公开访问' AS `v`, 1 AS `sort`, '所有人可见' AS `remark`
    UNION ALL SELECT 'private',  '私密文章', 2, '仅自己与管理员可见'
    UNION ALL SELECT 'password', '密码访问', 3, '凭访问密码可见'
) s
WHERE dt.`dict_type` = 'article_visibility'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `remark`          = VALUES(`remark`),
    `status`          = 1,
    `update_time`     = NOW();

-- -----------------------------------------------------------------------------
-- 3. 许可协议字典项
--
--    key 直接用协议标识（前端存的就是它），value 是给人看的名字。
-- -----------------------------------------------------------------------------
INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, NULL, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT 'cc-by-4.0'        AS `k`, 'CC BY 4.0（署名）'            AS `v`, 1 AS `sort`
    UNION ALL SELECT 'cc-by-sa-4.0',   'CC BY-SA 4.0（署名-相同方式共享）', 2
    UNION ALL SELECT 'cc-by-nc-sa-4.0', 'CC BY-NC-SA 4.0（署名-非商业性使用-相同方式共享）', 3
    UNION ALL SELECT 'cc-by-nd-4.0',   'CC BY-ND 4.0（署名-禁止演绎）', 4
    UNION ALL SELECT 'mit',            'MIT',                     5
    UNION ALL SELECT 'apache-2.0',     'Apache License 2.0',      6
    UNION ALL SELECT 'gpl-3.0',        'GNU GPL v3',               7
    UNION ALL SELECT 'all-rights-reserved', '版权所有，保留所有权利',     8
) s
WHERE dt.`dict_type` = 'article_license'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `status`          = 1,
    `update_time`     = NOW();
