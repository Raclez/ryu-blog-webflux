package com.ryu.blog.service;

import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.vo.SysConfigVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 系统配置服务接口
 */
public interface SysConfigService {

    /**
     * 获取配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    Mono<String> getConfigValue(String key, String defaultValue);

    /**
     * 获取配置信息
     *
     * @param key 配置键
     * @return 配置视图对象
     */
    Mono<SysConfigVO> getConfig(String key);
    
    /**
     * 通过ID获取配置信息
     *
     * @param id 配置ID
     * @return 配置视图对象
     */
    Mono<SysConfigVO> getConfigById(Long id);
    
    /**
     * 获取配置实体
     *
     * @param key 配置键
     * @return 配置实体
     */
    Mono<SysConfig> getConfigEntity(String key);
    
    /**
     * 通过ID获取配置实体
     *
     * @param id 配置ID
     * @return 配置实体
     */
    Mono<SysConfig> getConfigEntityById(Long id);

    /**
     * 获取配置列表
     *
     * @param group 配置分组，可为null
     * @return 配置视图对象列表
     */
    Flux<SysConfigVO> getConfigList(String group);

    /**
     * 分页获取配置列表
     *
     * @param page  页码
     * @param size  每页大小
     * @param group 配置分组，可为null
     * @return 配置列表和分页信息
     */
    Mono<Map<String, Object>> getConfigListPaged(int page, int size, String group);
    
    /**
     * 分页获取系统配置
     *
     * @param configKey 配置键（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 系统配置分页结果
     */
    Mono<Map<String, Object>> getSysConfigPage(String configKey, int page, int size);

    /**
     * 搜索配置
     *
     * @param key 配置键关键字
     * @return 配置视图对象列表
     */
    Flux<SysConfigVO> searchConfig(String key);

    /**
     * 添加配置
     *
     * @param configDTO 配置DTO
     * @return 添加后的配置视图对象
     */
    Mono<SysConfigVO> addConfig(SysConfigDTO configDTO);

    /**
     * 更新配置
     *
     * @param configDTO 配置DTO
     * @return 更新后的配置视图对象
     */
    Mono<SysConfigVO> updateConfig( SysConfigUpdateDTO configDTO);

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteConfig(Long id);

    /**
     * 批量获取配置
     *
     * @param keys 配置键列表
     * @return 配置键值对
     */
    Mono<Map<String, String>> batchGetConfigValues(Iterable<String> keys);

    /**
     * 批量更新配置
     *
     * @param configs 配置键值对
     * @return 是否更新成功
     */
    Mono<Boolean> batchUpdateConfig(Map<String, String> configs);
} 