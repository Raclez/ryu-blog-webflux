-- =============================================================================
-- 11_storage_type_alignment.sql — storage_type 字典与真实实现对齐（幂等）
--
-- 背景：
--   storage_type 字典原本是 local / aliyun / qiniu / tencent ——
--   与后端的实际能力**两头都对不上**：
--
--   | strategyKey | 后端实现                                  | 原字典 |
--   |-------------|-------------------------------------------|--------|
--   | local       | LocalStorageStrategy（可用）               | ✅     |
--   | minio       | MinioStorageStrategy（可用）               | ❌ 缺失 |
--   | aliyun      | AliyunOssStorageStrategy（占位，调用即抛异常）| ✅   |
--   | tencent     | TencentCosStorageStrategy（占位，调用即抛异常）| ✅   |
--   | qiniu       | 没有任何实现                                | ✅     |
--   | s3          | 没有任何实现                                | ❌     |
--
--   前端存储策略面板却把 6 个都列出来，于是「看起来能配，配了上传就失败」——
--   与本次清理系统配置的理由完全相同。
--
--   这里把字典收敛到**真正可用**的两种；占位实现（aliyun/tencent）与
--   无实现（qiniu/s3）不进字典，等它们真接上了再往字典里加一项即可。
--
-- 幂等性依赖 uk_dict_type_key(dict_type_id, dict_item_key)。
-- =============================================================================

DELETE i FROM `t_sys_dict_items` i
JOIN `t_sys_dict_types` t ON t.`id` = i.`dict_type_id`
WHERE t.`dict_type` = 'storage_type'
  AND i.`dict_item_key` NOT IN ('local', 'minio');

INSERT INTO `t_sys_dict_items`
    (`dict_type_id`, `dict_item_key`, `dict_item_value`, `sort`, `status`,
     `remark`, `create_time`, `update_time`, `is_deleted`)
SELECT dt.`id`, s.`k`, s.`v`, s.`sort`, 1, s.`remark`, NOW(), NOW(), 0
FROM `t_sys_dict_types` dt
JOIN (
    SELECT 'local' AS `k`, '本地存储' AS `v`, 1 AS `sort`,
           'LocalStorageStrategy，可用' AS `remark`
    UNION ALL SELECT 'minio', 'MinIO', 2,
           'MinioStorageStrategy，可用。aliyun/tencent 为占位实现、qiniu/s3 无实现，实现后再加入本字典'
) s
WHERE dt.`dict_type` = 'storage_type'
ON DUPLICATE KEY UPDATE
    `dict_item_value` = VALUES(`dict_item_value`),
    `sort`            = VALUES(`sort`),
    `remark`          = VALUES(`remark`),
    `status`          = 1,
    `is_deleted`      = 0,
    `update_time`     = NOW();
