-- 为t_storage_config表添加索引
-- 1. 为strategy_key字段添加索引，提高查询性能
CREATE INDEX idx_storage_config_strategy_key ON t_storage_config (strategy_key);

-- 2. 为is_enable字段添加索引，提高查询启用配置的性能
CREATE INDEX idx_storage_config_is_enable ON t_storage_config (is_enable);

-- 3. 为is_deleted字段添加索引，提高过滤已删除配置的性能
CREATE INDEX idx_storage_config_is_deleted ON t_storage_config (is_deleted);

-- 4. 组合索引，提高同时按strategy_key和is_deleted查询的性能
CREATE INDEX idx_storage_config_key_deleted ON t_storage_config (strategy_key, is_deleted);

-- 5. 组合索引，提高同时按is_enable和is_deleted查询的性能
CREATE INDEX idx_storage_config_enable_deleted ON t_storage_config (is_enable, is_deleted); 