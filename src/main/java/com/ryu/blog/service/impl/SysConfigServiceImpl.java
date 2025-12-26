package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
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
 * 
 * <p>提供系统配置的完整管理功能，包括：
 * <ul>
 *   <li>配置的增删改查操作</li>
 *   <li>用户级配置管理（支持用户个性化配置）</li>
 *   <li>配置分组管理（支持层级结构）</li>
 *   <li>批量操作支持</li>
 *   <li>多级缓存策略（提升查询性能）</li>
 * </ul>
 * 
 * <p>缓存策略说明：
 * <ul>
 *   <li>配置值缓存：按配置键缓存，用于快速获取配置值</li>
 *   <li>用户配置缓存：按用户ID+配置键缓存，支持用户个性化配置</li>
 *   <li>配置信息缓存：缓存完整的配置对象</li>
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
@CacheConfig(cacheNames = CacheConstants.SYS_CONFIG_CACHE_NAME)
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigRepository sysConfigRepository;
    private final SysConfigMapper sysConfigMapper;
    
    /**
     * 配置键分隔符
     * 用于分隔配置键的各个层级，例如：system.email.smtp_host
     */
    private static final String CONFIG_KEY_SEPARATOR = ".";

    /**
     * 获取配置值
     * 
     * <p>根据配置键获取对应的配置值，如果配置不存在则返回默认值。
     * 该方法会优先从缓存中获取，缓存未命中时才查询数据库。
     * 
     * @param key 配置键，格式为：分组.子分组.配置名
     * @param defaultValue 默认值，当配置不存在时返回
     * @return 配置值的Mono包装，如果配置不存在则返回默认值
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_VALUE_KEY + "' + #key", unless = "#result == null")
    public Mono<String> getConfigValue(String key, String defaultValue) {
        log.debug("开始获取配置值 - key: {}, defaultValue: {}", key, defaultValue);
        
        return getConfigEntity(key)
                .map(config -> {
                    String value = config.getConfigValue();
                    log.debug("成功获取配置值 - key: {}, value: {}, source: database", key, value);
                    return value;
                })
                .defaultIfEmpty(defaultValue)
                .doOnNext(value -> {
                    if (value != null && value.equals(defaultValue)) {
                        log.debug("配置不存在，返回默认值 - key: {}, defaultValue: {}", key, defaultValue);
                    }
                })
                .doOnError(e -> log.error("获取配置值失败 - key: {}, error: {}", key, e.getMessage(), e));
    }

    /**
     * 获取用户级配置值
     * 
     * <p>获取特定用户的个性化配置值。查询优先级：
     * <ol>
     *   <li>用户个性化配置（如果存在）</li>
     *   <li>系统全局配置</li>
     *   <li>默认值</li>
     * </ol>
     * 
     * <p>这种设计允许用户覆盖系统默认配置，实现个性化定制。
     * 
     * @param userId 用户ID
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值的Mono包装
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_USER_VALUE_KEY + "' + #userId + ':' + #key", unless = "#result == null")
    public Mono<String> getUserConfigValue(Long userId, String key, String defaultValue) {
        log.debug("开始获取用户配置值 - userId: {}, key: {}, defaultValue: {}", userId, key, defaultValue);
        
        return sysConfigRepository.findByUserIdAndConfigKey(userId, key)
                .map(config -> {
                    String value = config.getConfigValue();
                    log.debug("成功获取用户配置值 - userId: {}, key: {}, value: {}, source: user_config", 
                            userId, key, value);
                    return value;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("用户配置不存在，尝试获取系统配置 - userId: {}, key: {}", userId, key);
                    return getConfigValue(key, defaultValue);
                }))
                .doOnError(e -> log.error("获取用户配置值失败 - userId: {}, key: {}, error: {}", 
                        userId, key, e.getMessage(), e));
    }

    /**
     * 获取完整配置信息
     * 
     * <p>根据配置键获取完整的配置对象（包含所有字段），返回VO对象。
     * 
     * @param key 配置键
     * @return 配置信息VO的Mono包装，如果不存在则返回空Mono
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_INFO_KEY + "' + #key", unless = "#result == null")
    public Mono<SysConfigVO> getConfig(String key) {
        log.debug("开始获取配置信息 - key: {}", key);
        
        return getConfigEntity(key)
                .map(config -> {
                    SysConfigVO vo = sysConfigMapper.toVO(config);
                    log.debug("成功获取配置信息 - key: {}, id: {}", key, vo.getId());
                    return vo;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("配置信息不存在 - key: {}", key);
                    return Mono.empty();
                }))
                .doOnError(e -> log.error("获取配置信息失败 - key: {}, error: {}", key, e.getMessage(), e));
    }

    /**
     * 根据ID获取配置信息
     * 
     * @param id 配置ID
     * @return 配置信息VO的Mono包装，如果不存在则返回空Mono
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_INFO_BY_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<SysConfigVO> getConfigById(Long id) {
        log.debug("开始根据ID获取配置信息 - id: {}", id);
        
        return getConfigEntityById(id)
                .map(config -> {
                    SysConfigVO vo = sysConfigMapper.toVO(config);
                    log.debug("成功根据ID获取配置信息 - id: {}, key: {}", id, vo.getConfigKey());
                    return vo;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("配置信息不存在 - id: {}", id);
                    return Mono.empty();
                }))
                .doOnError(e -> log.error("根据ID获取配置信息失败 - id: {}, error: {}", id, e.getMessage(), e));
    }

    /**
     * 获取配置实体对象
     * 
     * <p>内部方法，用于获取配置的实体对象。
     * 该方法会被其他公共方法调用，并且结果会被缓存。
     * 
     * @param key 配置键
     * @return 配置实体的Mono包装
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #key", unless = "#result == null")
    public Mono<SysConfig> getConfigEntity(String key) {
        log.debug("开始获取配置实体 - key: {}", key);
        
        return sysConfigRepository.findByConfigKey(key)
                .doOnNext(config -> log.debug("成功获取配置实体 - key: {}, id: {}", key, config.getId()))
                .doOnError(e -> log.error("获取配置实体失败 - key: {}, error: {}", key, e.getMessage(), e));
    }

    /**
     * 根据ID获取配置实体对象
     * 
     * <p>内部方法，用于根据ID获取配置的实体对象。
     * 
     * @param id 配置ID
     * @return 配置实体的Mono包装
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_ENTITY_BY_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<SysConfig> getConfigEntityById(Long id) {
        log.debug("开始根据ID获取配置实体 - id: {}", id);
        
        return sysConfigRepository.findById(id)
                .doOnNext(config -> log.debug("成功根据ID获取配置实体 - id: {}, key: {}", id, config.getConfigKey()))
                .doOnError(e -> log.error("根据ID获取配置实体失败 - id: {}, error: {}", id, e.getMessage(), e));
    }

    /**
     * 获取配置列表
     * 
     * <p>根据分组前缀获取配置列表。如果不指定分组前缀，则返回所有配置。
     * 
     * @param groupPrefix 分组前缀，例如："system"、"system.email"，为空则返回所有配置
     * @return 配置列表的Flux流
     */
    @Override
    public Flux<SysConfigVO> getConfigList(String groupPrefix) {
        log.debug("开始获取配置列表 - groupPrefix: {}", groupPrefix);
        
        Flux<SysConfig> configFlux;
        if (StringUtils.hasText(groupPrefix)) {
            // 使用前缀匹配查找指定分组的配置
            String prefix = groupPrefix + CONFIG_KEY_SEPARATOR;
            log.debug("使用前缀匹配查询 - prefix: {}", prefix);
            configFlux = sysConfigRepository.findByConfigKeyStartingWith(prefix);
        } else {
            log.debug("查询所有配置");
            configFlux = sysConfigRepository.findAll();
        }
        
        return configFlux
                .map(sysConfigMapper::toVO)
                .doOnComplete(() -> log.debug("成功获取配置列表 - groupPrefix: {}", groupPrefix))
                .doOnError(e -> log.error("获取配置列表失败 - groupPrefix: {}, error: {}", 
                        groupPrefix, e.getMessage(), e));
    }

    /**
     * 获取用户配置列表
     * 
     * <p>获取指定用户的所有个性化配置。
     * 
     * @param userId 用户ID
     * @return 用户配置列表的Flux流
     */
    @Override
    public Flux<SysConfigVO> getUserConfigList(Long userId) {
        log.debug("开始获取用户配置列表 - userId: {}", userId);
        
        return sysConfigRepository.findByUserId(userId)
                .map(sysConfigMapper::toVO)
                .doOnComplete(() -> log.debug("成功获取用户配置列表 - userId: {}", userId))
                .doOnError(e -> log.error("获取用户配置列表失败 - userId: {}, error: {}", 
                        userId, e.getMessage(), e));
    }

    /**
     * 分页获取配置列表（旧版本接口）
     * 
     * <p>该方法返回Map格式的分页数据，主要用于向后兼容。
     * 建议使用 {@link #getSysConfigPage(String, int, int)} 方法。
     * 
     * @param page 页码，从1开始
     * @param size 每页大小
     * @param groupPrefix 分组前缀，可选
     * @return 包含total和list的Map
     */
    @Override
    public Mono<Map<String, Object>> getConfigListPaged(int page, int size, String groupPrefix) {
        log.debug("开始分页获取配置列表 - page: {}, size: {}, groupPrefix: {}", page, size, groupPrefix);
        
        // 创建分页请求对象，按ID降序排列
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        
        Mono<Long> countMono;
        Flux<SysConfig> configFlux;
        
        if (StringUtils.hasText(groupPrefix)) {
            String prefix = groupPrefix + CONFIG_KEY_SEPARATOR;
            log.debug("使用前缀匹配分页查询 - prefix: {}", prefix);
            countMono = sysConfigRepository.countByConfigKeyStartingWith(prefix);
            configFlux = sysConfigRepository.findByConfigKeyStartingWith(prefix, pageRequest);
        } else {
            log.debug("分页查询所有配置");
            countMono = sysConfigRepository.count();
            configFlux = sysConfigRepository.findAllBy(pageRequest);
        }
        
        return Mono.zip(countMono, configFlux.map(sysConfigMapper::toVO).collectList())
                .map(tuple -> {
                    Long total = tuple.getT1();
                    List<SysConfigVO> list = tuple.getT2();
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("total", total);
                    result.put("list", list);
                    
                    log.debug("成功分页获取配置列表 - page: {}, size: {}, total: {}, listSize: {}", 
                            page, size, total, list.size());
                    return result;
                })
                .doOnError(e -> log.error("分页获取配置列表失败 - page: {}, size: {}, groupPrefix: {}, error: {}", 
                        page, size, groupPrefix, e.getMessage(), e));
    }

    /**
     * 分页获取系统配置
     * 
     * <p>支持按配置键模糊搜索的分页查询。
     * 
     * @param configKey 配置键搜索关键字，支持模糊匹配，为空则查询所有
     * @param page 页码，从1开始
     * @param size 每页大小
     * @return 分页结果对象
     */
    @Override
    public Mono<PageResult<SysConfigVO>> getSysConfigPage(String configKey, int page, int size) {
        log.debug("开始分页获取系统配置 - configKey: {}, page: {}, size: {}", configKey, page, size);
        
        // 创建分页请求对象，按ID降序排列
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        
        Mono<Long> countMono;
        Flux<SysConfig> configFlux;
        
        if (StringUtils.hasText(configKey)) {
            log.debug("使用模糊匹配分页查询 - configKey: {}", configKey);
            countMono = sysConfigRepository.countByConfigKeyContaining(configKey);
            configFlux = sysConfigRepository.findByConfigKeyContaining(configKey, pageRequest);
        } else {
            log.debug("分页查询所有系统配置");
            countMono = sysConfigRepository.count();
            configFlux = sysConfigRepository.findAllBy(pageRequest);
        }
        
        return Mono.zip(countMono, configFlux.map(sysConfigMapper::toVO).collectList())
                .map(tuple -> {
                    List<SysConfigVO> records = tuple.getT2();
                    long total = tuple.getT1();
                    
                    PageResult<SysConfigVO> pageResult = new PageResult<>(records, total, size, page);
                    log.debug("成功分页获取系统配置 - page: {}, size: {}, total: {}, recordsSize: {}", 
                            page, size, total, records.size());
                    
                    return pageResult;
                })
                .doOnError(e -> log.error("分页获取系统配置失败 - configKey: {}, page: {}, size: {}, error: {}", 
                        configKey, page, size, e.getMessage(), e));
    }

    /**
     * 搜索配置
     * 
     * <p>根据关键字搜索配置，支持在配置键和备注中进行模糊匹配。
     * 
     * @param key 搜索关键字
     * @return 匹配的配置列表的Flux流
     */
    @Override
    public Flux<SysConfigVO> searchConfig(String key) {
        log.debug("开始搜索配置 - keyword: {}", key);
        
        if (!StringUtils.hasText(key)) {
            log.warn("搜索关键字为空，返回空结果");
            return Flux.empty();
        }
        
        return sysConfigRepository.findByConfigKeyContainingOrRemarkContaining(key, key)
                .map(sysConfigMapper::toVO)
                .doOnComplete(() -> log.debug("成功搜索配置 - keyword: {}", key))
                .doOnError(e -> log.error("搜索配置失败 - keyword: {}, error: {}", key, e.getMessage(), e));
    }

    /**
     * 添加配置
     * 
     * <p>添加新的系统配置。该方法会：
     * <ol>
     *   <li>检查配置键是否已存在</li>
     *   <li>验证配置键格式是否符合规范</li>
     *   <li>设置默认值（创建时间、更新时间、状态等）</li>
     *   <li>保存到数据库</li>
     *   <li>清除相关缓存</li>
     * </ol>
     * 
     * @param configDTO 配置DTO对象
     * @return 新增的配置VO对象
     * @throws IllegalArgumentException 如果配置键已存在或格式无效
     */
    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.CONFIG_VALUE_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_INFO_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #result.configKey", condition = "#result != null")
    })
    public Mono<SysConfigVO> addConfig(SysConfigDTO configDTO) {
        log.info("开始添加配置 - configKey: {}", configDTO.getConfigKey());
        
        // 1. 检查配置键是否已存在
        return sysConfigRepository.existsByConfigKey(configDTO.getConfigKey())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("配置键已存在，无法添加 - configKey: {}", configDTO.getConfigKey());
                        return Mono.error(new IllegalArgumentException("配置键已存在: " + configDTO.getConfigKey()));
                    }
                    
                    // 2. 验证配置键格式
                    if (!isValidConfigKey(configDTO.getConfigKey())) {
                        log.warn("配置键格式无效 - configKey: {}, 期望格式: 分组.子分组.配置名", 
                                configDTO.getConfigKey());
                        return Mono.error(new IllegalArgumentException(
                                "配置键格式无效，应为'分组.子分组.配置名'格式: " + configDTO.getConfigKey()));
                    }
                    
                    // 3. 转换DTO为实体并设置默认值
                    SysConfig sysConfig = sysConfigMapper.toEntity(configDTO);
                    LocalDateTime now = LocalDateTime.now();
                    
                    if (sysConfig.getCreateTime() == null) {
                        sysConfig.setCreateTime(now);
                    }
                    if (sysConfig.getUpdateTime() == null) {
                        sysConfig.setUpdateTime(now);
                    }
                    if (sysConfig.getIsDeleted() == null) {
                        sysConfig.setIsDeleted(0);
                    }
                    if (sysConfig.getStatus() == null) {
                        sysConfig.setStatus(true); // 默认启用
                    }
                    
                    log.debug("配置实体已准备 - configKey: {}, status: {}", 
                            sysConfig.getConfigKey(), sysConfig.getStatus());
                    
                    // 4. 保存到数据库
                    return sysConfigRepository.save(sysConfig)
                            .doOnNext(savedConfig -> {
                                log.info("成功添加配置 - id: {}, configKey: {}, value: {}", 
                                        savedConfig.getId(), savedConfig.getConfigKey(), savedConfig.getConfigValue());
                            })
                            .map(sysConfigMapper::toVO);
                })
                .doOnError(e -> {
                    if (!(e instanceof IllegalArgumentException)) {
                        log.error("添加配置失败 - configKey: {}, error: {}", 
                                configDTO.getConfigKey(), e.getMessage(), e);
                    }
                });
    }

    /**
     * 更新配置
     * 
     * <p>更新现有配置的值和属性。该方法会：
     * <ol>
     *   <li>验证配置是否存在</li>
     *   <li>更新指定的字段（只更新非空字段）</li>
     *   <li>更新修改时间</li>
     *   <li>保存到数据库</li>
     *   <li>清除相关缓存（包括用户配置缓存）</li>
     * </ol>
     * 
     * @param configDTO 配置更新DTO对象
     * @return 更新后的配置VO对象
     * @throws ResourceNotFoundException 如果配置不存在
     */
    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.CONFIG_VALUE_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_INFO_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_INFO_BY_ID_KEY + "' + #configDTO.id", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_BY_ID_KEY + "' + #configDTO.id", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_USER_VALUE_KEY + "' + #result.userId + ':' + #result.configKey", condition = "#result != null && #result.userId != null")
    })
    public Mono<SysConfigVO> updateConfig(SysConfigUpdateDTO configDTO) {
        log.info("开始更新配置 - id: {}", configDTO.getId());
        
        // 1. 获取现有配置
        return getConfigEntityById(configDTO.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("配置不存在，无法更新 - id: {}", configDTO.getId());
                    return Mono.error(new ResourceNotFoundException("配置不存在, id: " + configDTO.getId()));
                }))
                .flatMap(existingConfig -> {
                    log.debug("找到现有配置 - id: {}, configKey: {}", 
                            existingConfig.getId(), existingConfig.getConfigKey());
                    
                    // 2. 更新字段（只更新非空字段）
                    boolean hasChanges = false;
                    
                    if (StringUtils.hasText(configDTO.getConfigValue())) {
                        String oldValue = existingConfig.getConfigValue();
                        existingConfig.setConfigValue(configDTO.getConfigValue());
                        log.debug("更新配置值 - id: {}, oldValue: {}, newValue: {}", 
                                configDTO.getId(), oldValue, configDTO.getConfigValue());
                        hasChanges = true;
                    }
                    
                    if (StringUtils.hasText(configDTO.getRemark())) {
                        existingConfig.setRemark(configDTO.getRemark());
                        log.debug("更新备注 - id: {}", configDTO.getId());
                        hasChanges = true;
                    }
                    
                    if (configDTO.getStatus() != null) {
                        Boolean oldStatus = existingConfig.getStatus();
                        existingConfig.setStatus(configDTO.getStatus());
                        log.debug("更新状态 - id: {}, oldStatus: {}, newStatus: {}", 
                                configDTO.getId(), oldStatus, configDTO.getStatus());
                        hasChanges = true;
                    }
                    
                    if (configDTO.getUserId() != null) {
                        existingConfig.setUserId(configDTO.getUserId());
                        log.debug("更新用户ID - id: {}, userId: {}", configDTO.getId(), configDTO.getUserId());
                        hasChanges = true;
                    }
                    
                    if (StringUtils.hasText(configDTO.getExtra())) {
                        existingConfig.setExtra(configDTO.getExtra());
                        log.debug("更新扩展信息 - id: {}", configDTO.getId());
                        hasChanges = true;
                    }
                    
                    if (!hasChanges) {
                        log.debug("没有字段需要更新 - id: {}", configDTO.getId());
                    }
                    
                    // 3. 更新修改时间
                    existingConfig.setUpdateTime(LocalDateTime.now());
                    
                    // 4. 保存到数据库
                    return sysConfigRepository.save(existingConfig)
                            .doOnNext(savedConfig -> {
                                log.info("成功更新配置 - id: {}, configKey: {}", 
                                        savedConfig.getId(), savedConfig.getConfigKey());
                            })
                            .map(sysConfigMapper::toVO);
                })
                .doOnError(e -> {
                    if (!(e instanceof ResourceNotFoundException)) {
                        log.error("更新配置失败 - id: {}, error: {}", 
                                configDTO.getId(), e.getMessage(), e);
                    }
                });
    }

    /**
     * 删除配置
     * 
     * <p>删除指定的配置。该方法会：
     * <ol>
     *   <li>验证配置是否存在</li>
     *   <li>从数据库中删除配置</li>
     *   <li>返回被删除的配置对象</li>
     *   <li>自动清除所有相关缓存（通过 @CacheEvict 注解）</li>
     * </ol>
     * 
     * <p><b>缓存清除策略：</b>
     * 使用方法返回值 #result 来清除缓存，可以访问被删除配置的所有属性：
     * <ul>
     *   <li>按配置键清除：CONFIG_VALUE_KEY, CONFIG_INFO_KEY, CONFIG_ENTITY_KEY</li>
     *   <li>按ID清除：CONFIG_INFO_BY_ID_KEY, CONFIG_ENTITY_BY_ID_KEY</li>
     *   <li>按用户清除：CONFIG_USER_VALUE_KEY（如果是用户配置）</li>
     * </ul>
     * 
     * @param id 配置ID
     * @return 被删除的配置对象
     * @throws ResourceNotFoundException 如果配置不存在
     */
    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.CONFIG_VALUE_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_INFO_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #result.configKey", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_INFO_BY_ID_KEY + "' + #result.id", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_ENTITY_BY_ID_KEY + "' + #result.id", condition = "#result != null"),
            @CacheEvict(key = "'" + CacheConstants.CONFIG_USER_VALUE_KEY + "' + #result.userId + ':' + #result.configKey", 
                        condition = "#result != null && #result.userId != null")
    })
    public Mono<SysConfigVO> deleteConfig(Long id) {
        log.info("开始删除配置 - id: {}", id);
        
        // 1. 获取配置信息
        return getConfigEntityById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("配置不存在，无法删除 - id: {}", id);
                    return Mono.error(new ResourceNotFoundException("配置不存在, id: " + id));
                }))
                .flatMap(config -> {
                    String configKey = config.getConfigKey();
                    Long userId = config.getUserId();
                    log.debug("找到配置，准备删除 - id: {}, configKey: {}, userId: {}", id, configKey, userId);
                    
                    // 2. 转换为VO（在删除前保存配置信息）
                    SysConfigVO configVO = sysConfigMapper.toVO(config);
                    
                    // 3. 从数据库中删除
                    return sysConfigRepository.deleteById(id)
                            .then(Mono.fromCallable(() -> {
                                log.info("成功删除配置 - id: {}, configKey: {}", id, configKey);
                                return configVO;  // 返回被删除的配置对象
                            }))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnError(e -> {
                    if (!(e instanceof ResourceNotFoundException)) {
                        log.error("删除配置失败 - id: {}, error: {}", id, e.getMessage(), e);
                    }
                });
    }

    /**
     * 批量获取配置值
     * 
     * <p>根据配置键列表批量获取配置值。
     * 该方法会并发查询所有配置，提高查询效率。
     * 如果某个配置不存在或查询失败，会跳过该配置继续处理其他配置。
     * 
     * @param keys 配置键集合
     * @return 配置键值对Map的Mono包装
     */
    @Override
    public Mono<Map<String, String>> batchGetConfigValues(Iterable<String> keys) {
        List<String> keyList = new ArrayList<>();
        keys.forEach(keyList::add);
        log.debug("开始批量获取配置值 - count: {}", keyList.size());
        
        return Flux.fromIterable(keys)
                .flatMap(key -> 
                    getConfigValue(key, null)
                            .map(value -> Map.entry(key, value))
                            .onErrorResume(e -> {
                                log.warn("获取配置值失败，跳过该配置 - key: {}, error: {}", key, e.getMessage());
                                return Mono.empty();
                            })
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnSuccess(result -> log.debug("成功批量获取配置值 - requestCount: {}, successCount: {}", 
                        keyList.size(), result.size()))
                .doOnError(e -> log.error("批量获取配置值失败 - error: {}", e.getMessage(), e));
    }

    /**
     * 批量更新配置
     * 
     * <p>根据配置键值对Map批量更新配置。
     * 该方法会并发更新所有配置，提高更新效率。
     * 如果某个配置不存在或更新失败，会跳过该配置继续处理其他配置。
     * 
     * @param configs 配置键值对Map
     * @return 更新成功返回true
     */
    @Override
    public Mono<Boolean> batchUpdateConfig(Map<String, String> configs) {
        log.info("开始批量更新配置 - count: {}", configs.size());
        
        return Flux.fromIterable(configs.entrySet())
                .flatMap(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    return getConfigEntity(key)
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("配置不存在，跳过更新 - key: {}", key);
                                return Mono.error(new ResourceNotFoundException("配置不存在, key: " + key));
                            }))
                            .flatMap(config -> {
                                String oldValue = config.getConfigValue();
                                config.setConfigValue(value);
                                config.setUpdateTime(LocalDateTime.now());
                                
                                return sysConfigRepository.save(config)
                                        .doOnNext(savedConfig -> {
                                            log.debug("成功更新配置 - key: {}, oldValue: {}, newValue: {}", 
                                                    key, oldValue, value);
                                        });
                            })
                            .onErrorResume(e -> {
                                log.error("更新配置失败，跳过该配置 - key: {}, error: {}", key, e.getMessage());
                                return Mono.empty();
                            });
                })
                .then(Mono.just(true))
                .doOnSuccess(result -> log.info("批量更新配置完成 - count: {}", configs.size()))
                .doOnError(e -> log.error("批量更新配置失败 - error: {}", e.getMessage(), e));
    }
    
    /**
     * 清除所有缓存
     * 
     * <p>清除系统配置相关的所有缓存。
     * 该方法通常在批量更新配置或系统维护时调用。
     * 
     * @return 清除成功返回true
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
     * 
     * <p>获取系统中所有配置的分组结构。
     * 该方法会分析所有配置键，提取出分组和子分组信息，构建层级结构。
     * 
     * <p>返回格式示例：
     * <pre>
     * {
     *   "groups": [
     *     {
     *       "label": "system",
     *       "value": "system",
     *       "children": [
     *         {"label": "email", "value": "system.email"},
     *         {"label": "sms", "value": "system.sms"}
     *       ]
     *     }
     *   ]
     * }
     * </pre>
     * 
     * @return 包含分组信息的Map
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_GROUPS_KEY + "'", unless = "#result == null")
    public Mono<Map<String, Object>> getConfigGroups() {
        log.debug("开始获取配置分组列表");
        
        // 获取所有配置键
        return sysConfigRepository.findAll()
                .map(SysConfig::getConfigKey)
                .collectList()
                .map(configKeys -> {
                    log.debug("获取到配置键列表 - count: {}", configKeys.size());
                    return this.extractConfigGroups(configKeys);
                })
                .map(groups -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("groups", groups);
                    log.debug("成功获取配置分组列表 - groupCount: {}", groups.size());
                    return result;
                })
                .doOnError(e -> log.error("获取配置分组列表失败 - error: {}", e.getMessage(), e));
    }
    
    /**
     * 从配置键列表中提取配置分组
     * 
     * <p>该方法会分析所有配置键，提取出分组和子分组信息，构建层级结构。
     * 
     * <p>处理逻辑：
     * <ol>
     *   <li>遍历所有配置键</li>
     *   <li>按分隔符拆分配置键</li>
     *   <li>提取第一级分组和第二级子分组</li>
     *   <li>构建层级结构</li>
     * </ol>
     * 
     * <p>示例：
     * <pre>
     * 输入：["system.email.smtp_host", "system.email.smtp_port", "system.sms.api_key"]
     * 输出：[
     *   {
     *     "label": "system",
     *     "value": "system",
     *     "children": [
     *       {"label": "email", "value": "system.email"},
     *       {"label": "sms", "value": "system.sms"}
     *     ]
     *   }
     * ]
     * </pre>
     *
     * @param configKeys 配置键列表
     * @return 配置分组列表
     */
    private List<Map<String, Object>> extractConfigGroups(List<String> configKeys) {
        log.debug("开始提取配置分组 - configKeyCount: {}", configKeys.size());
        
        // 用于存储分组信息的Map，键为分组名，值为子分组集合
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
        
        log.debug("提取到分组信息 - groupCount: {}", groupMap.size());
        
        // 将分组信息转换为前端需要的格式
        List<Map<String, Object>> result = groupMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("label", entry.getKey());
                    groupInfo.put("value", entry.getKey());
                    
                    // 如果有子分组，则添加子分组信息
                    if (!entry.getValue().isEmpty()) {
                        List<Map<String, Object>> children = entry.getValue().stream()
                                .sorted() // 子分组按字母顺序排序
                                .map(subGroup -> {
                                    Map<String, Object> subGroupInfo = new HashMap<>();
                                    subGroupInfo.put("label", subGroup);
                                    subGroupInfo.put("value", entry.getKey() + CONFIG_KEY_SEPARATOR + subGroup);
                                    return subGroupInfo;
                                })
                                .collect(Collectors.toList());
                        groupInfo.put("children", children);
                        
                        log.debug("分组包含子分组 - group: {}, subGroupCount: {}", 
                                entry.getKey(), children.size());
                    }
                    
                    return groupInfo;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("label"))) // 分组按字母顺序排序
                .collect(Collectors.toList());
        
        log.debug("配置分组提取完成 - totalGroups: {}", result.size());
        return result;
    }
    
    /**
     * 检查配置键格式是否有效
     * 
     * <p>配置键格式规范：分组.子分组.配置名
     * <p>至少应该包含两个部分（分组和配置名），推荐使用三个部分。
     * 
     * <p>有效示例：
     * <ul>
     *   <li>system.email.smtp_host ✓</li>
     *   <li>system.email ✓</li>
     *   <li>system ✗（只有一个部分）</li>
     * </ul>
     *
     * @param configKey 配置键
     * @return 如果格式有效返回true，否则返回false
     */
    private boolean isValidConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            log.debug("配置键为空，格式无效");
            return false;
        }
        
        String[] parts = configKey.split("\\" + CONFIG_KEY_SEPARATOR);
        boolean isValid = parts.length >= 2;
        
        if (!isValid) {
            log.debug("配置键格式无效 - configKey: {}, parts: {}, 期望至少2个部分", 
                    configKey, parts.length);
        }
        
        return isValid;
    }
} 