package com.ryu.blog.service.impl;

import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.mapper.SysConfigMapper;
import com.ryu.blog.repository.SysConfigRepository;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.vo.SysConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统配置服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigRepository sysConfigRepository;
    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;
    private final SysConfigMapper sysConfigMapper;

    // Redis缓存前缀
    private static final String CACHE_KEY_PREFIX = "sys:config:";
    // 缓存时间（秒）
    private static final long CACHE_SECONDS = 3600;

    @Override
    public Mono<String> getConfigValue(String key, String defaultValue) {
        // 先从缓存获取
        return reactiveStringRedisTemplate.opsForValue().get(CACHE_KEY_PREFIX + key)
                .switchIfEmpty(
                        // 缓存不存在，从数据库获取
                        sysConfigRepository.findByConfigKeyAndIsDeleted(key, 0)
                                .flatMap(config -> {
                                    String value = config.getConfigValue();
                                    // 存入缓存
                                    return reactiveStringRedisTemplate.opsForValue()
                                            .set(CACHE_KEY_PREFIX + key, value, Duration.ofSeconds(CACHE_SECONDS))
                                            .thenReturn(value);
                                })
                                .defaultIfEmpty(defaultValue)
                );
    }

    @Override
    public Mono<SysConfigVO> getConfig(String key) {
        return sysConfigRepository.findByConfigKeyAndIsDeleted(key, 0)
                .map(sysConfigMapper::toVO);
    }
    
    @Override
    public Mono<SysConfigVO> getConfigById(Long id) {
        return sysConfigRepository.findById(id)
                .filter(config -> config.getIsDeleted() != null && config.getIsDeleted() == 0)
                .map(sysConfigMapper::toVO);
    }
    
    @Override
    public Mono<SysConfig> getConfigEntity(String key) {
        return sysConfigRepository.findByConfigKeyAndIsDeleted(key, 0);
    }
    
    @Override
    public Mono<SysConfig> getConfigEntityById(Long id) {
        return sysConfigRepository.findById(id)
                .filter(config -> config.getIsDeleted() != null && config.getIsDeleted() == 0);
    }

    @Override
    public Flux<SysConfigVO> getConfigList(String group) {
        Flux<SysConfig> configFlux;
        if (StringUtils.hasText(group)) {
            configFlux = sysConfigRepository.findByConfigGroupAndIsDeletedOrderByIdAsc(group, 0);
        } else {
            configFlux = sysConfigRepository.findAll()
                    .filter(config -> config.getIsDeleted() != null && config.getIsDeleted() == 0)
                    .sort((c1, c2) -> {
                        if (c1.getConfigGroup() == null || c2.getConfigGroup() == null) {
                            return 0;
                        }
                        int groupCompare = c1.getConfigGroup().compareTo(c2.getConfigGroup());
                        if (groupCompare != 0) {
                            return groupCompare;
                        }
                        return c1.getId().compareTo(c2.getId());
                    });
        }
        return configFlux.map(sysConfigMapper::toVO);
    }

    @Override
    public Mono<Map<String, Object>> getConfigListPaged(int page, int size, String group) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        int finalPage = page;
        int finalSize = size;

        Mono<Long> countMono;
        if (StringUtils.hasText(group)) {
            countMono = sysConfigRepository.countByConfigGroupAndIsDeleted(group, 0);
        } else {
            countMono = sysConfigRepository.countByIsDeleted(0);
        }

        return countMono.flatMap(total -> {
            long offset = (finalPage - 1) * finalSize;

            Flux<SysConfig> configFlux;
            if (StringUtils.hasText(group)) {
                configFlux = sysConfigRepository.findByConfigGroupAndIsDeletedOrderByIdAsc(group, 0)
                        .skip(offset)
                        .take(finalSize);
            } else {
                configFlux = sysConfigRepository.findByIsDeletedOrderByConfigGroupAndIdAsc(0, finalSize, offset);
            }

            return configFlux
                    .map(sysConfigMapper::toVO)
                    .collectList()
                    .map(configs -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("records", configs);
                        result.put("total", total);
                        result.put("pages", (total + finalSize - 1) / finalSize);
                        result.put("current", finalPage);
                        return result;
                    });
        });
    }

    @Override
    public Mono<Map<String, Object>> getSysConfigPage(String configKey, String configGroup, int page, int size) {
        return null;
    }

    @Override
    public Flux<SysConfigVO> searchConfig(String key) {
        if (!StringUtils.hasText(key)) {
            return Flux.empty();
        }
        return sysConfigRepository.findByConfigKeyLikeAndIsDeleted(key, 0)
                .map(sysConfigMapper::toVO);
    }

    @Override
    @Transactional
    public Mono<SysConfigVO> addConfig(SysConfigDTO configDTO) {
        SysConfig config = sysConfigMapper.toEntity(configDTO);
        
        // 检查配置键是否已存在
        return sysConfigRepository.findByConfigKeyAndIsDeleted(config.getConfigKey(), 0)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new RuntimeException("配置键已存在"));
                    }
                    
                    // 设置默认值
                    if (config.getIsSystem() == null) {
                        config.setIsSystem(0);
                    }
                    config.setCreateTime(LocalDateTime.now());
                    config.setUpdateTime(LocalDateTime.now());
                    config.setIsDeleted(0);
                    return sysConfigRepository.save(config)
                            .map(sysConfigMapper::toVO);
                });
    }

    @Override
    @Transactional
    public Mono<SysConfigVO> updateConfig( SysConfigUpdateDTO configDTO) {
        return sysConfigRepository.findById(configDTO.getId())
                .flatMap(existingConfig -> {
                    SysConfig config = sysConfigMapper.toEntity(configDTO);
                    config.setId(configDTO.getId());
                    
                    // 检查是否系统内置配置
                    if (existingConfig.getIsSystem() != null && existingConfig.getIsSystem() == 1) {
                        // 系统内置配置不允许修改配置键和分组
                        config.setConfigKey(existingConfig.getConfigKey());
                        config.setConfigGroup(existingConfig.getConfigGroup());
                        config.setIsSystem(1);
                    }
                    
                    // 更新时间
                    config.setUpdateTime(LocalDateTime.now());
                    config.setCreateTime(existingConfig.getCreateTime());
                    config.setIsDeleted(0);
                    
                    // 删除缓存
                    return reactiveStringRedisTemplate.delete(CACHE_KEY_PREFIX + config.getConfigKey())
                            .then(sysConfigRepository.save(config))
                            .map(sysConfigMapper::toVO);
                })
                .switchIfEmpty(Mono.error(new RuntimeException("配置不存在")));
    }

    @Override
    @Transactional
    public Mono<Boolean> deleteConfig(Long id) {
        return sysConfigRepository.findById(id)
                .flatMap(config -> {
                    // 检查是否系统内置配置
                    if (config.getIsSystem() != null && config.getIsSystem() == 1) {
                        return Mono.just(false);
                    }
                    
                    // 逻辑删除
                    config.setIsDeleted(1);
                    config.setUpdateTime(LocalDateTime.now());
                    
                    // 删除缓存
                    return reactiveStringRedisTemplate.delete(CACHE_KEY_PREFIX + config.getConfigKey())
                            .then(sysConfigRepository.save(config))
                            .map(savedConfig -> true);
                })
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Map<String, String>> batchGetConfigValues(Iterable<String> keys) {
        return Flux.fromIterable(keys)
                .flatMap(key -> getConfigValue(key, null)
                        .map(value -> Map.entry(key, value))
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    @Transactional
    public Mono<Boolean> batchUpdateConfig(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return Mono.just(false);
        }
        
        return Flux.fromIterable(configs.entrySet())
                .flatMap(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    return sysConfigRepository.findByConfigKeyAndIsDeleted(key, 0)
                            .flatMap(config -> {
                                config.setConfigValue(value);
                                config.setUpdateTime(LocalDateTime.now());
                                return sysConfigRepository.save(config)
                                        .then(reactiveStringRedisTemplate.opsForValue()
                                                .set(CACHE_KEY_PREFIX + key, value, Duration.ofSeconds(CACHE_SECONDS))
                                        );
                            })
                            .defaultIfEmpty(false);
                })
                .then(Mono.just(true))
                .onErrorReturn(false);
    }
} 