-- 下线「资源分组」功能：两张表一起删。
--
-- 背景：上传接口一直没把 group 落库（t_file 也没有 group 列，关系存在关联表里），
-- 且线上只有 1 条分组、1 条关联，属于从未真正投入使用的功能。
-- 代码侧已整体移除，这里把遗留的表清掉。
--
-- 先删关联表再删主表，避免外键/残留数据带来歧义。

DROP TABLE IF EXISTS `t_resource_group_file_rels`;
DROP TABLE IF EXISTS `t_resource_groups`;
