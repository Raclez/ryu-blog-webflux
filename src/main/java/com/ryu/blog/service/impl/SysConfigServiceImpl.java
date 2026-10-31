package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.constant.PublicConfigKeys;
import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.SysConfigRepository;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 自身代理 —— 让类内部的调用也能命中 {@code @Cacheable}。
     *
     * <p>Spring AOP 的 CGLIB 代理中，目标对象内部的 {@code this} 指向的是目标实例
     * 而不是代理，拦截器不会触发。所以<b>读路径</b>必须显式走 {@code self}，
     * 否则 {@code getConfigValue} 每次都要打一次配置表。
     *
     * <p><b>写路径相反</b>：{@code updateConfig} / {@code batchUpdateConfig} 必须在
     * 缓存之外读 —— 读到旧值再覆盖回去会把更新悄悄吃掉。这条路径<b>不</b>走
     * {@code self}，而是直接调用 {@link #loadConfigByKey} / {@link #loadConfigById}
     * 这两个不带 {@code @Cacheable} 的直读方法。
     *
     * <p>刻意不写成「调 {@code getConfig} 但绕过代理」：那种写法依赖「代理只拦
     * {@code @Cacheable} 方法」这个隐含前提，一旦有人给目标方法换个注解，
     * 或者把调用改成 {@code self.getConfig(...)}，更新就会被旧值悄悄覆盖掉。
     * 用名字不带缓存语义的直读方法表达意图，才不会踩坑。
     *
     * <p>{@code @Lazy} 用来打破「代理依赖自己」的循环依赖。
     */
    private SysConfigService self;

    @Autowired
    public void setSelf(@Lazy SysConfigService self) {
        this.self = self;
    }
    
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
        
        return self.getConfig(key)
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
                .switchIfEmpty(Mono.defer(() -> self.getConfigValue(key, defaultValue)));
    }

    /**
     * 获取完整配置信息
     */
    @Override
    @Cacheable(key = "'" + CacheConstants.CONFIG_ENTITY_KEY + "' + #key", unless = "#result == null")
    public Mono<SysConfig> getConfig(String key) {
        return loadConfigByKey(key);
    }

    /**
     * 直接读库取配置（<b>不经过缓存</b>）。
     *
     * <p>供写路径使用 —— 更新前必须拿到数据库里的最新值，
     * 命中缓存拿到旧配置再存回去就等于把这次更新悄悄吃掉了。
     */
    private Mono<SysConfig> loadConfigByKey(String key) {
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
        return loadConfigById(id);
    }

    /**
     * 直接读库取配置（<b>不经过缓存</b>），按 ID 查。
     *
     * <p>供写路径使用，理由同 {@link #loadConfigByKey(String)}。
     */
    private Mono<SysConfig> loadConfigById(Long id) {
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
     *
     * <p>缓存驱逐改成显式调用 {@link #evictConfigCaches(SysConfig)}：
     * 原先的 {@code @CacheEvict(key = "... + #result.configKey")} 在响应式场景下
     * {@code #result} 拿到的是返回的 {@code Mono} 而不是解析后的实体，
     * SpEL 取不到 configKey → 驱逐的 key 是错的，缓存根本清不掉。
     */
    @Override
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
                })
                .doOnNext(saved -> {
                    evictConfigCaches(saved);
                    // 新增键会改变分组树（/sysConfig/groups 的级联选项），
                    // 不驱逐的话管理页要等 12 小时才能看到新分组。
                    evictGroupsCache();
                    publishConfigChange(saved.getConfigKey());
                });
    }

    /**
     * 更新配置
     */
    @Override
    public Mono<SysConfig> updateConfig(SysConfigUpdateDTO configDTO) {
        log.info("更新配置 - id: {}", configDTO.getId());

        // 刻意绕开缓存：更新路径必须读到**最新**的值。
        // 如果命中缓存拿到旧配置，改完再存回去就等于把更新悄悄吃掉。
        return loadConfigById(configDTO.getId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, id: " + configDTO.getId())))
                .flatMap(existingConfig -> {
                    // 记下旧的 userId：userId 变了的话，旧的用户级缓存键也要驱逐
                    final Long previousUserId = existingConfig.getUserId();

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
                            .doOnNext(saved -> {
                                log.info("成功更新配置 - id: {}, key: {}",
                                        saved.getId(), saved.getConfigKey());
                                evictConfigCaches(saved);
                                if (previousUserId != null && !previousUserId.equals(saved.getUserId())) {
                                    evictUserConfigCache(previousUserId, saved.getConfigKey());
                                }
                                publishConfigChange(saved.getConfigKey());
                            });
                });
    }

    /**
     * 删除配置
     */
    @Override
    public Mono<SysConfig> deleteConfig(Long id) {
        log.info("删除配置 - id: {}", id);

        // 同 updateConfig：删除前要拿到最新值，不能用缓存里的旧对象。
        return loadConfigById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, id: " + id)))
                .flatMap(config ->
                    sysConfigRepository.deleteById(id)
                            .then(Mono.fromCallable(() -> {
                                log.info("成功删除配置 - id: {}, key: {}", id, config.getConfigKey());
                                return config;
                            }))
                            .subscribeOn(Schedulers.boundedElastic())
                )
                .doOnNext(config -> {
                    evictConfigCaches(config);
                    // 删掉最后一个同前缀的键会让分组消失，同样要清分组缓存
                    evictGroupsCache();
                    publishConfigChange(config.getConfigKey());
                });
    }

    /**
     * 驱逐某条配置相关的缓存
     *
     * <p>与三个 @Cacheable 的 key 一一对应：
     * <ul>
     *   <li>{@code getConfig(key)} → entity:{configKey}</li>
     *   <li>{@code getConfigById(id)} → entityById:{id}</li>
     *   <li>{@code getUserConfigValue(userId, key, default)} → userValue:{userId}:{configKey}</li>
     * </ul>
     *
     * @param config 配置实体
     */
    private void evictConfigCaches(SysConfig config) {
        if (config == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CacheConstants.SYS_CONFIG_CACHE);
        if (cache == null) {
            log.warn("缓存 {} 不存在，跳过驱逐", CacheConstants.SYS_CONFIG_CACHE);
            return;
        }
        cache.evict(CacheConstants.CONFIG_ENTITY_KEY_PREFIX + config.getConfigKey());
        if (config.getId() != null) {
            cache.evict(CacheConstants.CONFIG_ENTITY_BY_ID_KEY_PREFIX + config.getId());
        }
        if (config.getUserId() != null) {
            evictUserConfigCache(config.getUserId(), config.getConfigKey());
        }
        evictPublicConfigCache(config.getConfigKey());
    }

    /**
     * 驱逐公开配置缓存
     *
     * <p>只有白名单内的键会影响 {@code /sysConfig/public} 的响应。
     * 这也意味着「删掉一个敏感键」不会触发公开缓存清空 —— 它本来就不在里面。
     *
     * @param configKey 变更的配置键
     */
    private void evictPublicConfigCache(String configKey) {
        if (!PublicConfigKeys.isPublic(configKey)) {
            return;
        }
        Cache publicCache = cacheManager.getCache(CacheConstants.PUBLIC_CONFIG_CACHE);
        if (publicCache == null) {
            log.warn("缓存 {} 不存在，跳过驱逐", CacheConstants.PUBLIC_CONFIG_CACHE);
            return;
        }
        publicCache.clear();
    }

    /**
     * 驱逐配置分组缓存
     *
     * <p>{@code getConfigGroups} 同样挂在 {@link CacheConstants#SYS_CONFIG_CACHE} 上，
     * 但它的键是固定值，与具体配置键无关 —— 增删键时必须单独清。
     */
    private void evictGroupsCache() {
        Cache cache = cacheManager.getCache(CacheConstants.SYS_CONFIG_CACHE);
        if (cache == null) {
            return;
        }
        cache.evict(CacheConstants.CONFIG_GROUPS_KEY);
    }

    /**
     * 驱逐用户级配置缓存
     *
     * @param userId 用户ID
     * @param configKey 配置键
     */
    private void evictUserConfigCache(Long userId, String configKey) {
        Cache cache = cacheManager.getCache(CacheConstants.SYS_CONFIG_CACHE);
        if (cache == null) {
            return;
        }
        cache.evict(CacheConstants.CONFIG_USER_VALUE_KEY_PREFIX + userId + ":" + configKey);
    }

    /**
     * 发布配置变更事件
     *
     * <p>AI Provider 的实例是缓存在 {@code AiProviderFactory} 里的，
     * 改了 {@code ai.provider.*} 的配置（API Key / 模型 / 端点）之后必须让它失效，
     * 否则改了配置也不生效，要重启才行。
     *
     * @param configKey 配置键
     */
    private void publishConfigChange(String configKey) {
        if (configKey == null) {
            return;
        }
        eventPublisher.publishEvent(new ConfigChangeEvent(this, "sysConfig", configKey));
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
                    
                    // 同 updateConfig：批量更新同样必须读到最新值，
                    // 命中缓存拿旧配置再存回去会把这次更新吃掉。
                    return loadConfigByKey(key)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("配置不存在, key: " + key)))
                            .flatMap(config -> {
                                config.setConfigValue(value);
                                config.setUpdateTime(LocalDateTime.now());
                                return sysConfigRepository.save(config);
                            })
                            // 批量更新以前只写库：缓存不驱逐、事件也不发，
                            // 于是走 PUT /sysConfig/batch/update 改 AI Provider 配置
                            // 要等 12 小时缓存过期或重启才生效。
                            .doOnNext(saved -> {
                                evictConfigCaches(saved);
                                publishConfigChange(saved.getConfigKey());
                            })
                            .onErrorResume(e -> {
                                log.error("更新配置失败，跳过 - key: {}", key, e);
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
     * 获取公开配置
     *
     * <p>缓存用独立的 {@code publicConfig}（5 分钟），不复用 12 小时的系统配置缓存：
     * 它要面向未登录调用方，改动必须尽快收敛。
     *
     * <p>只查全局配置（{@code user_id = 0}）—— 用户级配置永远不进公开响应。
     *
     * @return 公开配置键值对
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.PUBLIC_CONFIG_CACHE,
            key = "'" + CacheConstants.CONFIG_PUBLIC_KEY + "'")
    public Mono<Map<String, String>> getPublicConfigValues() {
        return sysConfigRepository.findAllGlobal()
                .filter(config -> PublicConfigKeys.isPublic(config.getConfigKey()))
                .collectMap(SysConfig::getConfigKey,
                        config -> config.getConfigValue() == null ? "" : config.getConfigValue());
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
