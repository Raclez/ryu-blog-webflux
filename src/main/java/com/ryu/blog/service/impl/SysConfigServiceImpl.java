package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.SysConfigRepository;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现类
 * 
 * <p>提供系统配置的完整管理功能，包括：
 * <ul>
 *   <li>配置的增删改查操作</li>
 *   <li>用户级配置管理（支持用户个性化配置）</li>
 *   <li>配置分组管理（支持层级结构）</li>
 *   <li>批量操作支持</li>
 *   <li>缓存策略（提升查询性能）</li>
 * </ul>
 * 
 * <p>缓存策略说明：
 * <ul>
 *   <li>配置实体缓存：按配置键或ID缓存完整配置对象</li>
 *   <li>用户配置缓存：按用户ID+配置键缓存，支持用户个性化配置</li>
 *   <li>配置分组缓存：缓存配置分组结构</li>
 * </ul>
 * 
 * <p>配置键格式规范：
 * <pre>
 * 格式：分组.子分组.配置名
 * 示例：system.email.smtp_host
 * </pre>
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = CacheConstants.SYS_CONFIG_CACHE)
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigRepository sysConfigRepository;
    
    /**
     * 配置键分隔符
     */
    private static final String CONFIG_KEY_SEPARATOR = ".";

    /**
     * 获取配置值
     */
    @Override
    public Mono<String> getConfigValue(String key, String defaultValue) {
        log.debug("获取配置值 - key: {}, defaultValue: {}", key, defaultValue);
        
        return getConfig(key)
                .map(SysConfig::getConfigValue)
                .defaultIfEmpty(defaultValue)
                .doOnNext(value -> {
                    if (value != null && value.equals(defaultValue)) {
                        log.debug("配置不存在，返回默认值 - key: {}", key);
                    }
                });
    }

    /**
     * 获取用户级配置值
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_USER_VALUE_KEY + "' + #userId + ':' + #key", unless = "#result == null")
    public Mono<String> getUserConfigValue(Long userId, String key, String defaultValue) {
        log.debug("获取用户配置值 - userId: {}, key: {}", userId, key);
        
        return sysConfigRepository.findByUserIdAndConfigKey(userId, key)
                .map(SysConfig::getConfigValue)
                .switchIfEmpty(Mono.defer(() -> getConfigValue(key, defaultValue)));
    }

    /**
     * 获取完整配置信息
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #key", unless = "#result == null")
    public Mono<SysConfig> getConfig(String key) {
        log.debug("获取配置 - key: {}", key);
        
        return sysConfigRepository.findByConfigKey(key)
                .doOnNext(config -> log.debug("找到配置 - key: {}, id: {}", key, config.getId()));
    }

    /**
     * 根据ID获取配置信息
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_ENTITY_BY_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<SysConfig> getConfigById(Long id) {
        log.debug("根据ID获取配置 - id: {}", id);
        
        return sysConfigRepository.findById(id)
                .doOnNext(config -> log.debug("找到配置 - id: {}, key: {}", id, config.getConfigKey()));
    }

    /**
     * 获取配置列表
     */
    @Override
    public Flux<SysConfig> getConfigList(String groupPrefix) {
        log.debug("获取配置列表 - groupPrefix: {}", groupPrefix);
        
        if (StringUtils.hasText(groupPrefix)) {
            String prefix = groupPrefix + CONFIG_KEY_SEPARATOR;
            return sysConfigRepository.findByConfigKeyStartingWith(prefix);
        }
        return sysConfigRepository.findAll();
    }

    /**
     * 获取用户配置列表
     */
    @Override
    public Flux<SysConfig> getUserConfigList(Long userId) {
        log.debug("获取用户配置列表 - userId: {}", userId);
        return sysConfigRepository.findByUserId(userId);
    }

    /**
     * 分页获取系统配置
     */
    @Override
    public Mono<PageResult<SysConfig>> getSysConfigPage(String configKey, int page, int size) {
        log.debug("分页获取系统配置 - configKey: {}, page: {}, size: {}", configKey, page, size);
        
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        
        Mono<Long> countMono;
        Flux<SysConfig> configFlux;
        
        if (StringUtils.hasText(configKey)) {
            countMono = sysConfigRepository.countByConfigKeyContaining(configKey);
            configFlux = sysConfigRepository.findByConfigKeyContaining(configKey, pageRequest);
        } else {
            countMono = sysConfigRepository.count();
            configFlux = sysConfigRepository.findAllBy(pageRequest);
        }
        
        return Mono.zip(countMono, configFlux.collectList())
                .map(tuple -> new PageResult<>(tuple.getT2(), tuple.getT1(), size, page));
    }

    /**
     * 搜索配置
     */
    @Override
    public Flux<SysConfig> searchConfig(String key) {
        log.debug("搜索配置 - keyword: {}", key);
        
        if (!StringUtils.hasText(key)) {
            return Flux.empty();
        }
        
        return sysConfigRepository.findByConfigKeyContainingOrRemarkContaining(key, key);
    }

    /**
     * 添加配置
     */
    @Override
    @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #result.configKey", condition = "#result != null")
    public Mono<SysConfig> addConfig(SysConfigDTO configDTO) {
        log.info("添加配置 - configKey: {}", configDTO.getConfigKey());
        
        return sysConfigRepository.existsByConfigKey(configDTO.getConfigKey())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new IllegalArgumentException("配置键已存在: " + configDTO.getConfigKey()));
                    }
                    
                    if (!isValidConfigKey(configDTO.getConfigKey())) {
                        return Mono.error(new IllegalArgumentException(
                                "配置键格式无效，应为'分组.子分组.配置名'格式: " + configDTO.getConfigKey()));
                    }
                    
                    SysConfig sysConfig = SysConfig.builder()
                            .configKey(configDTO.getConfigKey())
                            .configValue(configDTO.getConfigValue())
                            .remark(configDTO.getRemark())
                            .userId(configDTO.getUserId())
                            .extra(configDTO.getExtra())
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .isDeleted(false)
                            .build();
                    
                    return sysConfigRepository.save(sysConfig)
                            .doOnNext(saved -> log.info("成功添加配置 - id: {}, key: {}", 
                                    saved.getId(), saved.getConfigKey()));
                });
    }

    /**
     * 更新配置
     */
    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_BY_ID_KEY + "' + #configDTO.id", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_USER_VALUE_KEY + "' + #result.userId + ':' + #result.configKey", 
                        condition = "#result != null && #result.userId != null")
    })
    public Mono<SysConfig> updateConfig(SysConfigUpdateDTO configDTO) {
        log.info("更新配置 - id: {}", configDTO.getId());
        
        return getConfigById(configDTO.getId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, id: " + configDTO.getId())))
                .flatMap(existingConfig -> {
                    if (StringUtils.hasText(configDTO.getConfigValue())) {
                        existingConfig.setConfigValue(configDTO.getConfigValue());
                    }
                    if (StringUtils.hasText(configDTO.getRemark())) {
                        existingConfig.setRemark(configDTO.getRemark());
                    }
                    if (configDTO.getUserId() != null) {
                        existingConfig.setUserId(configDTO.getUserId());
                    }
                    if (StringUtils.hasText(configDTO.getExtra())) {
                        existingConfig.setExtra(configDTO.getExtra());
                    }
                    
                    existingConfig.setUpdateTime(LocalDateTime.now());
                    
                    return sysConfigRepository.save(existingConfig)
                            .doOnNext(saved -> log.info("成功更新配置 - id: {}, key: {}", 
                                    saved.getId(), saved.getConfigKey()));
                });
    }

    /**
     * 删除配置
     */
    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_BY_ID_KEY + "' + #result.id", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_USER_VALUE_KEY + "' + #result.userId + ':' + #result.configKey", 
                        condition = "#result != null && #result.userId != null")
    })
    public Mono<SysConfig> deleteConfig(Long id) {
        log.info("删除配置 - id: {}", id);
        
        return getConfigById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, id: " + id)))
                .flatMap(config -> 
                    sysConfigRepository.deleteById(id)
                            .then(Mono.fromCallable(() -> {
                                log.info("成功删除配置 - id: {}, key: {}", id, config.getConfigKey());
                                return config;
                            }))
                            .subscribeOn(Schedulers.boundedElastic())
                );
    }

    /**
     * 批量获取配置值
     */
    @Override
    public Mono<Map<String, String>> batchGetConfigValues(Iterable<String> keys) {
        List<String> keyList = new ArrayList<>();
        keys.forEach(keyList::add);
        log.debug("批量获取配置值 - count: {}", keyList.size());
        
        return Flux.fromIterable(keys)
                .flatMap(key -> 
                    getConfigValue(key, null)
                            .map(value -> Map.entry(key, value))
                            .onErrorResume(e -> {
                                log.warn("获取配置值失败，跳过 - key: {}", key);
                                return Mono.empty();
                            })
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * 批量更新配置
     */
    @Override
    public Mono<Boolean> batchUpdateConfig(Map<String, String> configs) {
        log.info("批量更新配置 - count: {}", configs.size());
        
        return Flux.fromIterable(configs.entrySet())
                .flatMap(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    return getConfig(key)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, key: " + key)))
                            .flatMap(config -> {
                                config.setConfigValue(value);
                                config.setUpdateTime(LocalDateTime.now());
                                return sysConfigRepository.save(config);
                            })
                            .onErrorResume(e -> {
                                log.error("更新配置失败，跳过 - key: {}", key);
                                return Mono.empty();
                            });
                })
                .then(Mono.just(true));
    }
    
    /**
     * 清除所有缓存
     */
    @Override
    @CacheEvict(allEntries = true)
    public Mono<Boolean> clearAllCache() {
        return Mono.fromCallable(() -> {
            log.info("清除所有系统配置缓存");
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取配置分组列表
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_GROUPS_KEY + "'", unless = "#result == null")
    public Mono<Map<String, Object>> getConfigGroups() {
        log.debug("获取配置分组列表");
        
        return sysConfigRepository.findAll()
                .map(SysConfig::getConfigKey)
                .collectList()
                .map(this::extractConfigGroups)
                .map(groups -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("groups", groups);
                    return result;
                });
    }
    
    /**
     * 从配置键列表中提取配置分组
     */
    private List<Map<String, Object>> extractConfigGroups(List<String> configKeys) {
        Map<String, Set<String>> groupMap = new HashMap<>();
        
        for (String configKey : configKeys) {
            String[] parts = configKey.split("\\" + CONFIG_KEY_SEPARATOR);
            if (parts.length > 0) {
                String group = parts[0];
                if (parts.length > 1) {
                    groupMap.computeIfAbsent(group, k -> new HashSet<>()).add(parts[1]);
                } else {
                    groupMap.putIfAbsent(group, new HashSet<>());
                }
            }
        }
        
        return groupMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("label", entry.getKey());
                    groupInfo.put("value", entry.getKey());
                    
                    if (!entry.getValue().isEmpty()) {
                        List<Map<String, Object>> children = entry.getValue().stream()
                                .sorted()
                                .map(subGroup -> {
                                    Map<String, Object> subGroupInfo = new HashMap<>();
                                    subGroupInfo.put("label", subGroup);
                                    subGroupInfo.put("value", entry.getKey() + CONFIG_KEY_SEPARATOR + subGroup);
                                    return subGroupInfo;
                                })
                                .collect(Collectors.toList());
                        groupInfo.put("children", children);
                    }
                    
                    return groupInfo;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("label")))
                .collect(Collectors.toList());
    }
    
    /**
     * 检查配置键格式是否有效
     */
    private boolean isValidConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return false;
        }
        String[] parts = configKey.split("\\" + CONFIG_KEY_SEPARATOR);
        return parts.length >= 2;
    }
}
