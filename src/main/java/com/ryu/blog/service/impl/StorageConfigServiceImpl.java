package com.ryu.blog.service.impl;

import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigQueryDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.StorageConfigMapper;
import com.ryu.blog.repository.StorageConfigRepository;
import com.ryu.blog.service.StorageConfigService;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.vo.StorageConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 存储策略服务实现类
 *
 * @author ryu 475118582@qq.com
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StorageConfigServiceImpl implements StorageConfigService {

    private final StorageConfigRepository storageConfigRepository;
    private final StorageConfigMapper storageConfigMapper;
    private final StorageStrategyRegistry strategyRegistry;
    private final StorageConfigManager configManager;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Mono<StorageConfigVO> getEnabledStrategy() {
        log.debug("查询已启用存储策略");
        return storageConfigRepository.findOneByIsEnableAndIsDeleted(true, false)
                // ⚠️ 这里绝不能打印 config.getConfig() —— 那是 AK/SK 的明文 JSON。
                // 日志文件通常比接口的访问控制更松（会被采集、被备份、被贴到工单里），
                // 凭据一旦进了日志，轮转策略救不回来。
                .doOnNext(config -> log.info("获取到已启用的存储策略: {}", config.getStrategyName()))
                .map(StorageConfigVO::from)
                .doOnError(e -> log.error("查询已启用存储策略出错", e))
                .doOnSuccess(config -> {
                    if (config == null) {
                        log.warn("未找到已启用的存储策略");
                    } else {
                        log.info("最终获取到已启用的存储策略: {}", config.getStrategyKey());
                    }
                });
    }

    @Override
    public Mono<StorageConfigVO> getStrategyByKey(String strategyKey) {
        log.debug("查询存储策略: {}", strategyKey);
        return configManager.getStrategyConfig(strategyKey)
                .doOnNext(config -> log.debug("获取到存储策略: {}", config.getStrategyName()))
                .map(StorageConfigVO::from);
    }

    @Override
    @Transactional
    public Mono<Void> createStorageConfig(StorageConfigCreateDTO dto) {
        log.info("创建存储策略: {}", dto.getStrategyName());

        // 如果设置为启用，则先禁用其他策略
        Mono<Void> disableOthers = Mono.just(true)
                .filter(__ -> Boolean.TRUE.equals(dto.getIsEnable()))
                .flatMap(__ -> disableAllStrategies())
                .then();

        // ⚠️ 先校验再禁用手上的其它策略：
        // 反过来的话，一次被拒绝的创建请求会先把现役策略停掉，
        // 结果是「没创建成功、旧的也被关了」—— 存储直接瘫掉。
        return requireImplementedStrategy(dto.getStrategyKey())
                .then(disableOthers)
                // toEntity() 的 config 是 @Mapping(ignore) 的 —— 用它落库会把 config 列写成 NULL，
                // 刚建好的策略连 AK/SK 都没有。必须走 toEntityWithConfig。
                .then(Mono.fromCallable(() -> storageConfigMapper.toEntityWithConfig(dto)))
                .flatMap(storageConfigRepository::save)
                .doOnNext(savedConfig -> {
                    log.info("存储策略创建成功: {}", savedConfig.getStrategyName());
                    // 发布配置变更事件
                    publishConfigChangeEvent(savedConfig.getStrategyKey());
                })
                .then(configManager.reloadConfig());
    }

    @Override
    @Transactional
    public Mono<Void> updateStorageConfig(StorageConfigUpdateDTO dto) {
        log.info("更新存储策略: {}", dto.getStrategyName());

        // 如果设置为启用，则先禁用其他策略
        Mono<Void> disableOthers = Mono.just(true)
                .filter(__ -> Boolean.TRUE.equals(dto.getIsEnable()))
                .flatMap(__ -> disableAllStrategies())
                .then();

        // 先把老配置捞出来 —— 更新可能是「局部更新」（dto.strategyKey 为空），
        // 生效的策略标识要以库里的那条为准，不能只看 dto。
        return storageConfigRepository.findById(dto.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("存储策略不存在: " + dto.getId())))
                .flatMap(existingConfig -> {
                    String effectiveKey = (dto.getStrategyKey() != null && !dto.getStrategyKey().isEmpty())
                            ? dto.getStrategyKey()
                            : existingConfig.getStrategyKey();

                    // 只在「这次更新会把它启用」时拦；
                    // 停用一条未实现策略的历史配置必须永远放行（不然它就再也关不掉了）。
                    Mono<Void> guard = Boolean.TRUE.equals(dto.getIsEnable())
                            ? requireImplementedStrategy(effectiveKey)
                            : Mono.empty();

                    return guard.then(disableOthers).then(Mono.defer(() -> {
                        // 前端拿到的是脱敏后的 config（AK/SK 是 ******），整表单回传时
                        // 不能拿这串星号覆盖掉库里的真凭据 —— 否则下一次上传就是 InvalidAccessKeyId。
                        if (dto.getConfig() != null) {
                            dto.setConfig(StorageConfigVO.mergeMasked(dto.getConfig(), existingConfig.getConfigMap()));
                        }

                        // 就地更新已有实体，而不是 toEntity(dto) 造个新的：
                        // 新实体的 config 是 ignore 的、maxFileSize / defaultExpiry / creatorId
                        // 被写成 0 —— 编辑一次存储配置，AK/SK 被抹成 NULL、单文件上限被重置成 0。
                        storageConfigMapper.updateEntityWithConfig(dto, existingConfig);
                        return storageConfigRepository.save(existingConfig);
                    }));
                })
                .doOnNext(savedConfig -> {
                    log.info("存储策略更新成功: {}", savedConfig.getStrategyName());
                    // 发布配置变更事件
                    publishConfigChangeEvent(savedConfig.getStrategyKey());
                })
                .then(configManager.reloadConfig());
    }

    @Override
    @Transactional
    public Mono<Void> enableOrDisableStorageConfig(String strategyKey, boolean isEnabled) {
        log.info("{} 存储策略: {}", isEnabled ? "启用" : "停用", strategyKey);

        // 如果要启用策略，先禁用所有策略
        Mono<Void> disableOthers = Mono.just(true)
                .filter(__ -> isEnabled)
                .flatMap(__ -> disableAllStrategies())
                .then();

        // 只在「启用」时拦。停用必须永远放行：
        // 历史上可能已经有一条 COS 配置是启用状态，
        // 如果连停用都拒绝，管理员就再也回不到 local 了。
        Mono<Void> guard = isEnabled
                ? requireImplementedStrategy(strategyKey)
                : Mono.empty();

        return guard
                .then(disableOthers)
                .then(storageConfigRepository.findByStrategyKeyAndIsDeleted(strategyKey, false))
                .switchIfEmpty(Mono.error(new RuntimeException("存储策略不存在: " + strategyKey)))
                .flatMap(config -> {
                    config.setIsEnable(isEnabled);
                    config.setUpdateTime(LocalDateTime.now());
                    return storageConfigRepository.save(config);
                })
                .doOnNext(config -> {
                    log.info("存储策略状态已更新: {}, isEnabled={}", config.getStrategyName(), isEnabled);
                    // 发布配置变更事件
                    publishConfigChangeEvent(strategyKey);
                })
                // 停用时不能走严格版 reloadConfig：停掉最后一个策略之后库里一条启用配置都不剩，
                // reloadConfig() 会明确报「未找到启用的存储配置」，而本方法在 @Transactional 里 ——
                // 那个错误会把「停用」一起回滚，管理员就再也停不掉最后一个策略（Task 2 特意
                // 让停用方向永远放行，正是为了这种情况）。停用只清缓存，活跃策略留空并回落到默认。
                .then(Mono.defer(() -> isEnabled
                        ? configManager.reloadConfig()
                        : Mono.fromRunnable(configManager::clearConfigCache)));
    }

    @Override
    public Flux<StorageConfigVO> getStrategiesByPage(StorageConfigQueryDTO queryDTO) {
        int pageSize = queryDTO.getPageSize() > 0 ? queryDTO.getPageSize() : 10;
        int currentPage = queryDTO.getCurrentPage() > 0 ? queryDTO.getCurrentPage() : 1;
        long offset = (long) (currentPage - 1) * pageSize;

        log.debug("分页查询存储策略, 页码: {}, 每页数量: {}", currentPage, pageSize);

        // 分页交给数据库：早先是全表拉进内存再 skip/take
        Flux<StorageConfig> page = (queryDTO.getStrategyName() != null && !queryDTO.getStrategyName().isEmpty())
                ? storageConfigRepository.findPageByNameAndIsDeleted(false, queryDTO.getStrategyName(), pageSize, offset)
                : storageConfigRepository.findPageByIsDeleted(false, pageSize, offset);

        // 脱敏后再返回 —— 见 StorageConfigVO 的类注释
        return page.map(StorageConfigVO::from);
    }

    @Override
    @Transactional
    public Mono<Void> removeById(Long id) {
        log.info("删除存储策略: {}", id);
        return storageConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("存储策略不存在: " + id)))
                .flatMap(config -> {
                    // 逻辑删除
                    config.setIsDeleted(true);
                    config.setUpdateTime(LocalDateTime.now());
                    return storageConfigRepository.save(config)
                            .doOnNext(savedConfig -> {
                                log.info("存储策略已删除: {}", savedConfig.getStrategyName());
                                // 发布配置变更事件
                                publishConfigChangeEvent(savedConfig.getStrategyKey());
                            });
                })
                .then(configManager.reloadConfig());
    }
    
    /**
     * 校验目标策略<b>确实实现了</b>。
     *
     * <p>占位实现（腾讯云 COS）曾经能一路走通「创建配置 → 启用 → 上传」，
     * 上传还「成功」返回了一个路径 —— 但对象根本不存在。
     * 这里在配置层面就把它挡住，比在上传时才发现问题早得多。
     *
     * <p>未注册的策略标识不在这里报错 —— 那是另一类问题（拼错 key），
     * 交给后续流程按「找不到策略」处理。
     *
     * @param strategyKey 策略标识
     * @return 校验通过返回空 Mono；不通过则 error
     */
    private Mono<Void> requireImplementedStrategy(String strategyKey) {
        if (strategyKey == null || strategyKey.isEmpty()) {
            return Mono.empty();
        }
        return strategyRegistry.getStrategy(strategyKey)
                .onErrorResume(e -> Mono.empty())
                .flatMap(strategy -> strategy.isImplemented()
                        ? Mono.<Void>empty()
                        : Mono.error(new BusinessException(
                                "存储策略「" + strategyKey + "」尚未实现，不能启用；请改用 local 或 minio")))
                .then();
    }

    /**
     * 禁用所有存储策略
     *
     * <p>⚠️ 必须用 {@code concatMap}（串行），不能用 {@code flatMap}（并发）：
     * 调用方（create / update / enable）都带 {@code @Transactional}，
     * 整条链绑定在同一条 R2DBC 连接上；
     * r2dbc-mysql 1.4.0 每条连接只有一个 SPSC 的 RequestQueue 且 submit() 无锁，
     * 并发提交会误判队满并抛 {@code Request queue is full}，
     * 结果是「只禁用了部分策略，库里同时留着两条 is_enable = 1」。
     *
     * @return 操作结果
     */
    private Mono<Void> disableAllStrategies() {
        return storageConfigRepository.findAllByIsDeleted(false)
                .filter(config -> Boolean.TRUE.equals(config.getIsEnable()))
                .concatMap(config -> {
                    config.setIsEnable(false);
                    config.setUpdateTime(LocalDateTime.now());
                    return storageConfigRepository.save(config);
                })
                .then();
    }
    
    /**
     * 发布配置变更事件
     * @param strategyKey 策略键
     */
    private void publishConfigChangeEvent(String strategyKey) {
        configManager.publishConfigChangeEvent(strategyKey);
        log.debug("已发布存储配置变更事件: strategyKey={}", strategyKey);
    }
} 