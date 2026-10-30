package com.ryu.blog.strategy;

import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.StorageConfigMapper;
import com.ryu.blog.repository.StorageConfigRepository;
import com.ryu.blog.service.impl.StorageConfigServiceImpl;
import com.ryu.blog.strategy.impl.AliyunOssStorageStrategy;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import com.ryu.blog.strategy.impl.MinioStorageStrategy;
import com.ryu.blog.strategy.impl.TencentCosStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储策略「可用性」契约测试。
 *
 * <p><b>它守的是什么</b>：腾讯云 COS 曾经是一整套<b>假装成功</b>的实现 ——
 * {@code getCosClient()} 返回 {@code new Object()}、上传只返回一个人造的路径、
 * {@code fileExists} 恒为 {@code true}、删除恒成功。它却是正常的 Spring Bean，
 * 于是注册表里有它、后台能选中它 —— 管理员把存储策略切成 COS 之后，
 * 上传「成功」、文件却根本不存在。这是最危险的一种静默失败：
 * <b>用户要等到下载时才发现问题</b>。
 *
 * <p>本测试把三条规则钉死：
 * <ol>
 *   <li>未实现的策略<b>不出现在可选列表</b>里；</li>
 *   <li>未实现的策略<b>不能被启用</b>（连同「创建配置」一起拦掉）；</li>
 *   <li>未实现的能力<b>不许伪造成功</b>（阿里云的校验和曾返回随机 UUID，
 *       让基于校验和的去重静默失效）。</li>
 * </ol>
 *
 * @author ryu
 */
class StorageStrategyAvailabilityTest {

    private StorageConfigManager configManager;
    private StorageStrategyRegistry registry;
    private StorageConfigRepository storageConfigRepository;
    private StorageConfigServiceImpl storageConfigService;

    @BeforeEach
    void setUp() {
        configManager = mock(StorageConfigManager.class);
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());
        when(configManager.getStrategyConfig(anyString())).thenReturn(Mono.empty());
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(2)));
        when(configManager.reloadConfig()).thenReturn(Mono.empty());

        registry = new StorageStrategyRegistry(List.of(
                new LocalStorageStrategy(configManager),
                new MinioStorageStrategy(configManager, mock(CacheManager.class)),
                new AliyunOssStorageStrategy(configManager),
                new TencentCosStorageStrategy(configManager)), configManager);
        registry.init();

        storageConfigRepository = mock(StorageConfigRepository.class);
        when(storageConfigRepository.findAllByIsDeleted(anyBoolean())).thenReturn(Flux.empty());
        when(storageConfigRepository.save(any(StorageConfig.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StorageConfigMapper mapper = mock(StorageConfigMapper.class);
        when(mapper.toEntity(any(StorageConfigCreateDTO.class))).thenAnswer(inv -> {
            StorageConfigCreateDTO dto = inv.getArgument(0);
            StorageConfig entity = new StorageConfig();
            entity.setStrategyKey(dto.getStrategyKey());
            entity.setStrategyName(dto.getStrategyName());
            entity.setIsEnable(Boolean.TRUE.equals(dto.getIsEnable()));
            return entity;
        });

        storageConfigService = new StorageConfigServiceImpl(storageConfigRepository, mapper, registry,
                configManager, mock(ApplicationEventPublisher.class));
    }

    // ==================================================================
    // 1. 未实现的策略不出现在可选列表里
    // ==================================================================

    @Test
    @DisplayName("未实现的策略不出现在可选列表里")
    void availableStrategies_excludeUnimplemented() {
        List<String> available = registry.getAvailableStrategies();

        assertTrue(available.contains("local"), "本地存储必须可选");
        assertTrue(available.contains("minio"), "MinIO 必须可选");
        assertFalse(available.contains("aliyun"), "阿里云 OSS 尚未实现，不能出现在可选列表里");
        assertFalse(available.contains("tencent"), "腾讯云 COS 尚未实现，不能出现在可选列表里");
    }

    @Test
    @DisplayName("未实现的策略自报 isImplemented=false")
    void isImplemented_isReportedHonestly() {
        assertTrue(registry.getStrategy("local").block().isImplemented());
        assertTrue(registry.getStrategy("minio").block().isImplemented());
        assertFalse(registry.getStrategy("aliyun").block().isImplemented());
        assertFalse(registry.getStrategy("tencent").block().isImplemented());
    }

    // ==================================================================
    // 2. 未实现的策略不能被启用
    // ==================================================================

    @Test
    @DisplayName("启用未实现的策略 → 拒绝，且不得写库")
    void enablingUnimplementedStrategy_isRejected() {
        when(storageConfigRepository.findByStrategyKeyAndIsDeleted(anyString(), anyBoolean()))
                .thenReturn(Mono.just(config("tencent")));

        assertThrows(BusinessException.class,
                () -> storageConfigService.enableOrDisableStorageConfig("tencent", true).block());

        verify(storageConfigRepository, never()).save(any(StorageConfig.class));
    }

    @Test
    @DisplayName("创建未实现策略的配置 → 拒绝，且不得写库")
    void creatingConfigForUnimplementedStrategy_isRejected() {
        StorageConfigCreateDTO dto = new StorageConfigCreateDTO();
        dto.setStrategyKey("tencent");
        dto.setStrategyName("腾讯云COS");
        dto.setIsEnable(false);

        assertThrows(BusinessException.class,
                () -> storageConfigService.createStorageConfig(dto).block());

        verify(storageConfigRepository, never()).save(any(StorageConfig.class));
    }

    @Test
    @DisplayName("启用已实现的策略 → 放行")
    void enablingImplementedStrategy_isAllowed() {
        when(storageConfigRepository.findByStrategyKeyAndIsDeleted(anyString(), anyBoolean()))
                .thenReturn(Mono.just(config("local")));

        storageConfigService.enableOrDisableStorageConfig("local", true).block();

        verify(storageConfigRepository).save(any(StorageConfig.class));
    }

    // ==================================================================
    // 3. 未实现的能力不许伪造成功
    // ==================================================================

    @Test
    @DisplayName("阿里云 OSS 的校验和返回空串，而不是随机 UUID")
    void aliyunChecksum_isNotARandomUuid() {
        AliyunOssStorageStrategy aliyun = new AliyunOssStorageStrategy(configManager);

        String checksum = aliyun.calculateFileChecksum("images/2026/10/30/a.png").block();

        assertEquals("", checksum,
                "返回一个随机 UUID 会让「按校验和去重」静默失效 —— "
                        + "同一个文件每次的校验和都不同，去重形同虚设");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private StorageConfig config(String strategyKey) {
        StorageConfig config = new StorageConfig();
        config.setId(1L);
        config.setStrategyKey(strategyKey);
        config.setStrategyName(strategyKey);
        config.setIsEnable(Boolean.FALSE);
        return config;
    }

    @Test
    @DisplayName("停用最后一个启用策略：必须成功，不能因为「没有启用配置」被回滚")
    void disablingTheLastStrategy_isAllowed() {
        StorageConfig local = new StorageConfig();
        local.setStrategyKey("local");
        local.setStrategyName("本地存储");
        local.setIsEnable(true);

        when(storageConfigRepository.findByStrategyKeyAndIsDeleted("local", false))
                .thenReturn(Mono.just(local));
        when(storageConfigRepository.save(any(StorageConfig.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // 停用之后一条启用配置都不剩 —— reloadConfig() 此时会明确报「未找到启用的存储配置」
        when(configManager.reloadConfig())
                .thenReturn(Mono.error(new BusinessException("未找到启用的存储配置，请先在系统配置中启用一个存储策略")));

        storageConfigService.enableOrDisableStorageConfig("local", false).block();

        assertFalse(local.getIsEnable(),
                "停用被回滚了 —— 管理员再也停不掉最后一个策略，也就回不到 local");
    }
}
