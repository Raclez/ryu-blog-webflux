package com.ryu.blog.service.impl;

import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.mapper.StorageConfigMapperImpl;
import com.ryu.blog.repository.StorageConfigRepository;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.vo.StorageConfigVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「在后台编辑一次存储配置」不能把凭据弄丢。
 *
 * <p><b>它守的是两个真实缺陷</b>：
 * <ol>
 *   <li>{@code toEntity(dto)} 上的 {@code config} 是 {@code @Mapping(ignore = true)} 的，
 *       生成的 Mapper 实现根本<b>不写 config</b> → {@code save()} 把 config 列写成 NULL。
 *       编辑一次 MinIO 配置，AK/SK/endpoint/bucket 全没了。</li>
 *   <li>读接口把 AK/SK 脱敏成 {@code ******}，前端整表单回传时如果直接落库，
 *       库里存的就是一串星号 → 下一次上传报 {@code InvalidAccessKeyId}。</li>
 * </ol>
 *
 * @author ryu
 */
class StorageConfigUpdatePreservesConfigTest {

    private static final String AK = "AKIAIOSFODNN7EXAMPLE";
    private static final String SK = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    @Test
    @DisplayName("编辑配置：AK/SK 保留原值，单文件上限不被重置成 0")
    void updateKeepsStoredSecretsAndLimits() {
        StorageConfigRepository repository = mock(StorageConfigRepository.class);
        StorageStrategyRegistry registry = mock(StorageStrategyRegistry.class);
        StorageConfigManager configManager = mock(StorageConfigManager.class);

        StorageConfig existing = new StorageConfig();
        existing.setId(1L);
        existing.setStrategyName("MinIO");
        existing.setStrategyKey("minio");
        existing.setIsEnable(true);
        existing.setMaxFileSize(10L * 1024 * 1024);
        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("endpoint", "https://minio.example.com");
        stored.put("bucket", "ryu-blog");
        stored.put("accessKey", AK);
        stored.put("secretKey", SK);
        existing.setConfigMap(stored);

        when(repository.findById(1L)).thenReturn(Mono.just(existing));
        when(repository.findAllByIsDeleted(false)).thenReturn(Flux.empty());
        when(repository.save(any(StorageConfig.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(registry.getStrategy("minio")).thenReturn(Mono.empty());
        when(configManager.reloadConfig()).thenReturn(Mono.empty());

        StorageConfigServiceImpl service = new StorageConfigServiceImpl(
                repository,
                new StorageConfigMapperImpl(),
                registry,
                configManager,
                mock(ApplicationEventPublisher.class));

        // 前端回传的是「GET /storageConfigs/minio」拿到的脱敏视图
        Map<String, String> echoed = new LinkedHashMap<>();
        echoed.put("endpoint", "https://minio.example.com");
        echoed.put("bucket", "ryu-blog-2");
        echoed.put("accessKey", StorageConfigVO.MASK);
        echoed.put("secretKey", StorageConfigVO.MASK);

        StorageConfigUpdateDTO dto = new StorageConfigUpdateDTO();
        dto.setId(1L);
        dto.setStrategyName("MinIO");
        dto.setStrategyKey("minio");
        dto.setIsEnable(true);
        dto.setConfig(echoed);

        StepVerifier.create(service.updateStorageConfig(dto)).verifyComplete();

        ArgumentCaptor<StorageConfig> captor = ArgumentCaptor.forClass(StorageConfig.class);
        verify(repository).save(captor.capture());
        StorageConfig saved = captor.getValue();

        assertEquals(AK, saved.getConfigMap().get("accessKey"));
        assertEquals(SK, saved.getConfigMap().get("secretKey"));
        assertEquals("ryu-blog-2", saved.getConfigMap().get("bucket"));
        assertEquals(10L * 1024 * 1024, saved.getMaxFileSize().longValue());
        assertEquals(1L, saved.getId().longValue());
    }
}
