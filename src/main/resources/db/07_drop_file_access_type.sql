-- 下线「文件可见性」：t_file.access_type 不再有语义。
--
-- 背景：对象存储的桶是匿名可读的（实测 GET https://minio.example.com/ryu-blog/
-- 直接返回完整对象列表），access_type 挡不住直链，只制造「私有文件是安全的」
-- 这种错觉。代码侧已整体移除（FileAccessPolicy 只保留写权限判定）。
--
-- 真要保护敏感文件，只能靠私有桶 / 私有前缀，一个 boolean 字段做不到。

ALTER TABLE `t_file` DROP COLUMN `access_type`;
