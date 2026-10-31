-- =============================================================================
-- 09_purge_dead_config.sql — 删除「改了没有任何效果」的系统配置（幂等）
--
-- 背景：
--   t_sys_config 是通用键值表，历史上被塞进了一大批「看起来能配、实际没人读」的键。
--   管理员在系统设置页改它们，界面提示「更新成功」，但系统行为毫无变化 ——
--   这比缺一个配置项更糟：它会让人以为某项功能已经按自己的意愿生效了。
--
--   本脚本只处理「与现有实现直接冲突」的那一批：它们各自都有一个**真正的**配置来源。
--
--   | 被删的键              | 真正生效的地方                                  |
--   |-----------------------|-------------------------------------------------|
--   | system.storage.*      | t_storage_config（StorageConfigManager 读取）    |
--   | system.cache.*        | 代码里的 Caffeine 配置（CacheConfig，不是 Redis） |
--   | security.jwt.*        | application.yml 的 sa-token.*                     |
--   | security.login.*      | 登录锁定功能未实现                                |
--   | security.password.*   | 密码策略未实现                                    |
--
--   判定依据：全仓库 grep 这些键，Java / yml / 前端**零引用**（2026-10-31 核实）。
--
--   刻意**没有**动的东西（等逐个决策，不趁这次一起删）：
--     - security.captcha.*：验证码功能存在，但长度/开关写在代码里，这两个键同样不生效；
--   （system.feature.beta_test / system.old_api.endpoint / system.deprecated.old_config
--     三条样例数据已一并清理，见文件末尾。）
--     - notification.* / integration.* / article.comment.* / system.email.* / system.sms.*：
--       功能没做，键先建了 —— 属于"待实现"而不是"实现冲突"，不该由清理脚本替产品做决定。
--
-- 幂等：DELETE 按前缀匹配，重复执行无副作用（第二次影响 0 行）。
-- =============================================================================

-- 存储：真实配置在 t_storage_config
DELETE FROM `t_sys_config` WHERE `config_key` LIKE 'system.storage.%';

-- 缓存：真实配置是 Caffeine（代码内），与 Redis 无关
DELETE FROM `t_sys_config` WHERE `config_key` LIKE 'system.cache.%';

-- JWT：真实配置是 application.yml 的 sa-token.jwt-secret-key / timeout
DELETE FROM `t_sys_config` WHERE `config_key` LIKE 'security.jwt.%';

-- 登录锁定 / 密码策略：功能未实现，读的人不存在
DELETE FROM `t_sys_config` WHERE `config_key` LIKE 'security.login.%';
DELETE FROM `t_sys_config` WHERE `config_key` LIKE 'security.password.%';

-- 样例数据：值是 old-api.example.com / old_value 这种占位，全仓零引用。
-- 它们不是"某个配置项没接上"，而是当年建表时留下的演示行 —— 留着只会让人以为有这个开关。
DELETE FROM `t_sys_config`
 WHERE `config_key` IN (
    'system.feature.beta_test',
    'system.old_api.endpoint',
    'system.deprecated.old_config');
