package com.ryu.blog.service.impl;

import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.mapper.SysConfigMapper;
import com.ryu.blog.repository.SysConfigRepository;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysConfigVO;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "sysConfig")
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigRepository sysConfigRepository;
    private final SysConfigMapper sysConfigMapper;
    
    // 配置键分隔符
    private static final String CONFIG_KEY_SEPARATOR = ".";

    @Override
    @Cacheable(key = "'value:' + #key", unless = "#result == null")
    public Mono<String> getConfigValue(String key, String defaultValue) {
        log.debug("获取配置值, key: {}, defaultValue: {}", key, defaultValue);
        
        return getConfigEntity(key)
                .map(config -> {
                    String value = config.getConfigValue();
                    log.debug("从数据库获取配置值, key: {}, value: {}", key, value);
                    return value;
                })
                .defaultIfEmpty(defaultValue)
                .doOnError(e -> log.error("获取配置值出错, key: {}", key, e));
    }

    @Override
    @Cacheable(key = "'userValue:' + #userId + ':' + #key", unless = "#result == null")
    public Mono<String> getUserConfigValue(Long userId, String key, String defaultValue) {
        log.debug("获取用户配置值, userId: {}, key: {}, defaultValue: {}", userId, key, defaultValue);
        
        return sysConfigRepository.findByUserIdAndConfigKey(userId, key)
                .map(config -> {
                    String value = config.getConfigValue();
                    log.debug("从数据库获取用户配置值, userId: {}, key: {}, value: {}", userId, key, value);
                    return value;
                })
                .switchIfEmpty(getConfigValue(key, defaultValue))
                .doOnError(e -> log.error("获取用户配置值出错, userId: {}, key: {}", userId, key, e));
    }

    @Override
    @Cacheable(key = "'config:' + #key", unless = "#result == null")
    public Mono<SysConfigVO> getConfig(String key) {
        log.debug("获取配置信息, key: {}", key);
        return getConfigEntity(key)
                .map(sysConfigMapper::toVO)
                .switchIfEmpty(Mono.empty())
                .doOnError(e -> log.error("获取配置信息出错, key: {}", key, e));
    }

    @Override
    @Cacheable(key = "'configById:' + #id", unless = "#result == null")
    public Mono<SysConfigVO> getConfigById(Long id) {
        log.debug("通过ID获取配置信息, id: {}", id);
        return getConfigEntityById(id)
                .map(sysConfigMapper::toVO)
                .switchIfEmpty(Mono.empty())
                .doOnError(e -> log.error("通过ID获取配置信息出错, id: {}", id, e));
    }

    @Override
    @Cacheable(key = "'entity:' + #key", unless = "#result == null")
    public Mono<SysConfig> getConfigEntity(String key) {
        log.debug("获取配置实体, key: {}", key);
        
        return sysConfigRepository.findByConfigKey(key)
                .doOnNext(config -> log.debug("从数据库获取配置实体, key: {}", key))
                .doOnError(e -> log.error("获取配置实体出错, key: {}", key, e));
    }

    @Override
    @Cacheable(key = "'entityById:' + #id", unless = "#result == null")
    public Mono<SysConfig> getConfigEntityById(Long id) {
        log.debug("通过ID获取配置实体, id: {}", id);
        
        return sysConfigRepository.findById(id)
                .doOnNext(config -> log.debug("从数据库获取配置实体, id: {}", id))
                .doOnError(e -> log.error("通过ID获取配置实体出错, id: {}", id, e));
    }

    @Override
    public Flux<SysConfigVO> getConfigList(String groupPrefix) {
        log.debug("获取配置列表, groupPrefix: {}", groupPrefix);
        
        Flux<SysConfig> configFlux;
        if (StringUtils.hasText(groupPrefix)) {
            // 使用前缀匹配查找指定分组的配置
            configFlux = sysConfigRepository.findByConfigKeyStartingWith(groupPrefix + CONFIG_KEY_SEPARATOR);
        } else {
            configFlux = sysConfigRepository.findAll();
        }
        
        return configFlux
                .map(sysConfigMapper::toVO)
                .doOnComplete(() -> log.debug("获取配置列表完成, groupPrefix: {}", groupPrefix))
                .doOnError(e -> log.error("获取配置列表出错, groupPrefix: {}", groupPrefix, e));
    }

    @Override
    public Flux<SysConfigVO> getUserConfigList(Long userId) {
        log.debug("获取用户配置列表, userId: {}", userId);
        
        return sysConfigRepository.findByUserId(userId)
                .map(sysConfigMapper::toVO)
                .doOnComplete(() -> log.debug("获取用户配置列表完成, userId: {}", userId))
                .doOnError(e -> log.error("获取用户配置列表出错, userId: {}", userId, e));
    }

    @Override
    public Mono<Map<String, Object>> getConfigListPaged(int page, int size, String groupPrefix) {
        log.debug("分页获取配置列表, page: {}, size: {}, groupPrefix: {}", page, size, groupPrefix);
        
        // 创建分页请求对象
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        
        Mono<Long> countMono;
        Flux<SysConfig> configFlux;
        
        if (StringUtils.hasText(groupPrefix)) {
            String prefix = groupPrefix + CONFIG_KEY_SEPARATOR;
            countMono = sysConfigRepository.countByConfigKeyStartingWith(prefix);
            configFlux = sysConfigRepository.findByConfigKeyStartingWith(prefix, pageRequest);
        } else {
            countMono = sysConfigRepository.count();
            configFlux = sysConfigRepository.findAllBy(pageRequest);
        }
        
        return Mono.zip(
                countMono,
                configFlux.map(sysConfigMapper::toVO).collectList()
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("total", tuple.getT1());
            result.put("list", tuple.getT2());
            log.debug("分页获取配置列表完成, total: {}, listSize: {}", tuple.getT1(), tuple.getT2().size());
            return result;
        }).doOnError(e -> log.error("分页获取配置列表出错, page: {}, size: {}", page, size, e));
    }

    @Override
    public Mono<PageResult<SysConfigVO>> getSysConfigPage(String configKey, int page, int size) {
        log.debug("分页获取系统配置, configKey: {}, page: {}, size: {}", configKey, page, size);
        
        // 创建分页请求对象
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
        
        return Mono.zip(
                countMono,
                configFlux.map(sysConfigMapper::toVO).collectList()
        ).map(tuple -> {
            List<SysConfigVO> records = tuple.getT2();
            long total = tuple.getT1();
            
            PageResult<SysConfigVO> pageResult = new PageResult<>(records, total, size, page);
            log.debug("分页获取系统配置完成, total: {}, listSize: {}", total, records.size());
            
            return pageResult;
        }).doOnError(e -> log.error("分页获取系统配置出错, configKey: {}, page: {}, size: {}", configKey, page, size, e));
    }

    @Override
    public Flux<SysConfigVO> searchConfig(String key) {
        log.debug("搜索配置, key: {}", key);
        
        if (!StringUtils.hasText(key)) {
            log.warn("搜索关键字为空");
            return Flux.empty();
        }
        
        return sysConfigRepository.findByConfigKeyContainingOrRemarkContaining(key, key)
                .map(sysConfigMapper::toVO)
                .doOnComplete(() -> log.debug("搜索配置完成, key: {}", key))
                .doOnError(e -> log.error("搜索配置出错, key: {}", key, e));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(key = "'value:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'config:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'entity:' + #result.configKey", condition = "#result != null")
    })
    public Mono<SysConfigVO> addConfig(SysConfigDTO configDTO) {
        log.debug("添加配置, configDTO: {}", configDTO);
        
        // 检查配置键是否已存在
        return sysConfigRepository.existsByConfigKey(configDTO.getConfigKey())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("配置键已存在, key: {}", configDTO.getConfigKey());
                        return Mono.error(new IllegalArgumentException("配置键已存在: " + configDTO.getConfigKey()));
                    }
                    
                    // 验证配置键格式
                    if (!isValidConfigKey(configDTO.getConfigKey())) {
                        log.warn("配置键格式无效, key: {}", configDTO.getConfigKey());
                        return Mono.error(new IllegalArgumentException("配置键格式无效，应为'分组.子分组.配置名'格式: " + configDTO.getConfigKey()));
                    }
                    
                    // 转换DTO为实体并保存
                    SysConfig sysConfig = sysConfigMapper.toEntity(configDTO);
                    // 设置默认值
                    if (sysConfig.getCreateTime() == null) {
                        sysConfig.setCreateTime(LocalDateTime.now());
                    }
                    if (sysConfig.getUpdateTime() == null) {
                        sysConfig.setUpdateTime(LocalDateTime.now());
                    }
                    if (sysConfig.getIsDeleted() == null) {
                        sysConfig.setIsDeleted(0);
                    }
                    if (sysConfig.getStatus() == null) {
                        sysConfig.setStatus(true); // 默认启用
                    }
                    
                    return sysConfigRepository.save(sysConfig)
                            .doOnNext(savedConfig -> {
                                log.debug("添加配置成功, id: {}, key: {}", savedConfig.getId(), savedConfig.getConfigKey());
                            })
                            .map(sysConfigMapper::toVO);
                })
                .doOnError(e -> log.error("添加配置出错, configDTO: {}", configDTO, e));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(key = "'value:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'config:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'entity:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'configById:' + #configDTO.id", condition = "#result != null"),
            @CacheEvict(key = "'entityById:' + #configDTO.id", condition = "#result != null"),
            @CacheEvict(key = "'userValue:' + #result.userId + ':' + #result.configKey", condition = "#result != null && #result.userId != null")
    })
    public Mono<SysConfigVO> updateConfig(SysConfigUpdateDTO configDTO) {
        log.debug("更新配置, configDTO: {}", configDTO);
        
        return getConfigEntityById(configDTO.getId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, id: " + configDTO.getId())))
                .flatMap(existingConfig -> {
                    if (StringUtils.hasText(configDTO.getConfigValue())) {
                        existingConfig.setConfigValue(configDTO.getConfigValue());
                    }
                    if (StringUtils.hasText(configDTO.getRemark())) {
                        existingConfig.setRemark(configDTO.getRemark());
                    }
                    if (configDTO.getStatus() != null) {
                        existingConfig.setStatus(configDTO.getStatus());
                    }
                    if (configDTO.getUserId() != null) {
                        existingConfig.setUserId(configDTO.getUserId());
                    }
                    if (StringUtils.hasText(configDTO.getExtra())) {
                        existingConfig.setExtra(configDTO.getExtra());
                    }
                    
                    existingConfig.setUpdateTime(LocalDateTime.now());
                    
                    return sysConfigRepository.save(existingConfig)
                            .doOnNext(savedConfig -> {
                                log.debug("更新配置成功, id: {}, key: {}", savedConfig.getId(), savedConfig.getConfigKey());
                            })
                            .map(sysConfigMapper::toVO);
                })
                .doOnError(e -> log.error("更新配置出错, configDTO: {}", configDTO, e));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(key = "'value:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'config:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'entity:' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'configById:' + #id", condition = "#result != null"),
            @CacheEvict(key = "'entityById:' + #id", condition = "#result != null"),
            @CacheEvict(key = "'userValue:' + #result.userId + ':' + #result.configKey", condition = "#result != null && #result.userId != null")
    })
    public Mono<Boolean> deleteConfig(Long id) {
        log.debug("删除配置, id: {}", id);
        
        return getConfigEntityById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, id: " + id)))
                .flatMap(config -> {
                    return sysConfigRepository.deleteById(id)
                            .then(Mono.fromCallable(() -> {
                                log.debug("删除配置成功, id: {}, key: {}", id, config.getConfigKey());
                                return true;
                            }))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnError(e -> log.error("删除配置出错, id: {}", id, e));
    }

    @Override
    public Mono<Map<String, String>> batchGetConfigValues(Iterable<String> keys) {
        log.debug("批量获取配置值");
        
        return Flux.fromIterable(keys)
                .flatMap(key -> getConfigValue(key, null)
                        .map(value -> Map.entry(key, value))
                        .onErrorResume(e -> {
                            log.warn("获取配置值出错, key: {}", key, e);
                            return Mono.empty();
                        })
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnSuccess(result -> log.debug("批量获取配置值完成, count: {}", result.size()))
                .doOnError(e -> log.error("批量获取配置值出错", e));
    }

    @Override
    public Mono<Boolean> batchUpdateConfig(Map<String, String> configs) {
        log.debug("批量更新配置, count: {}", configs.size());
        
        return Flux.fromIterable(configs.entrySet())
                .flatMap(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    return getConfigEntity(key)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, key: " + key)))
                            .flatMap(config -> {
                                config.setConfigValue(value);
                                config.setUpdateTime(LocalDateTime.now());
                                return sysConfigRepository.save(config)
                                        .doOnNext(savedConfig -> {
                                            log.debug("更新配置成功, key: {}", key);
                                        });
                            })
                            .onErrorResume(e -> {
                                log.error("更新配置出错, key: {}", key, e);
                                return Mono.empty();
                            });
                })
                .then(Mono.just(true))
                .doOnSuccess(result -> log.debug("批量更新配置完成"))
                .doOnError(e -> log.error("批量更新配置出错", e));
    }
    
    @Override
    @CacheEvict(allEntries = true)
    public Mono<Boolean> clearAllCache() {
        return Mono.fromCallable(() -> {
            log.info("清除所有系统配置缓存");
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    @Cacheable(key = "'configGroups'", unless = "#result == null")
    public Mono<Map<String, Object>> getConfigGroups() {
        log.debug("获取配置分组列表");
        
        // 获取所有配置
        return sysConfigRepository.findAll()
                .map(SysConfig::getConfigKey)
                .collectList()
                .map(this::extractConfigGroups)
                .map(groups -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("groups", groups);
                    log.debug("获取配置分组列表完成, count: {}", groups.size());
                    return result;
                })
                .doOnError(e -> log.error("获取配置分组列表出错", e));
    }
    
    /**
     * 从配置键列表中提取配置分组
     *
     * @param configKeys 配置键列表
     * @return 配置分组列表
     */
    private List<Map<String, Object>> extractConfigGroups(List<String> configKeys) {
        // 用于存储分组信息的Map，键为分组名，值为子分组或配置项
        Map<String, Set<String>> groupMap = new HashMap<>();
        
        // 遍历所有配置键，提取分组信息
        for (String configKey : configKeys) {
            String[] parts = configKey.split("\\" + CONFIG_KEY_SEPARATOR);
            if (parts.length > 0) {
                String group = parts[0];
                // 如果有子分组，则添加到对应分组的集合中
                if (parts.length > 1) {
                    groupMap.computeIfAbsent(group, k -> new HashSet<>()).add(parts[1]);
                } else {
                    // 没有子分组的情况下，确保分组存在
                    groupMap.putIfAbsent(group, new HashSet<>());
                }
            }
        }
        
        // 将分组信息转换为前端需要的格式
        return groupMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("label", entry.getKey());
                    groupInfo.put("value", entry.getKey());
                    
                    // 如果有子分组，则添加子分组信息
                    if (!entry.getValue().isEmpty()) {
                        List<Map<String, Object>> children = entry.getValue().stream()
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
                .collect(Collectors.toList());
    }
    
    /**
     * 检查配置键格式是否有效
     * 格式应为: 分组.子分组.配置名
     *
     * @param configKey 配置键
     * @return 是否有效
     */
    private boolean isValidConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return false;
        }
        
        String[] parts = configKey.split("\\" + CONFIG_KEY_SEPARATOR);
        // 至少应该有分组和配置名两部分
        return parts.length >= 2;
    }
} 