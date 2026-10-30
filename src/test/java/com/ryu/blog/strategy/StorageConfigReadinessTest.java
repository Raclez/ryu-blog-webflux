package com.ryu.blog.strategy;

import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.StorageConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Mono;

/**
 * 存储配置的「就绪」与「错误传播」。
 *
 * <p><b>它守的是什么</b>：
 * <ol>
 *   <li>启动时数据库里<b>没有</b>启用中的配置 —— 早先是静默退回 local，
 *       管理员以为用的是 OSS，实际上文件全写在本地磁盘；</li>
 *   <li>{@code reloadConfig()} 的重入保护失败时返回 {@code Mono.empty()} ——
 *       管理员连点两次「刷新配置」，第二次静默什么都不做，界面上却显示成功。</li>
 * </ol>
 *
 * @author ryu
 */
class StorageConfigReadinessTest {

    private StorageConfigRepository repository;
    private StorageConfigManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(StorageConfigRepository.class);
        manager = new StorageConfigManager(repository, event -> { }, mock(org.springframework.cache.CacheManager.class));
    }

    @Test
    @DisplayName("没有启用中的配置：明确报错并标记未就绪，而不是静默用 local")
    void noEnabledConfig_failsLoudly() {
        when(repository.findOneByIsEnableAndIsDeleted(anyBoolean(), anyBoolean()))
                .thenReturn(Mono.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> manager.reloadConfig().block());

        assertTrue(ex.getMessage().contains("存储配置"),
                "错误信息要指向存储配置本身，实际为: " + ex.getMessage());
        assertFalse(manager.isReady(),
                "没有从数据库读到启用配置时，管理器不能自称已就绪 —— "
                        + "否则「悄悄用了 local」会被当成「配置加载成功」");
    }

    @Test
    @DisplayName("并发第二次 reloadConfig：返回错误，而不是静默空操作")
    void concurrentReload_isRejected() {
        StorageConfig config = new StorageConfig();
        config.setStrategyKey("minio");
        config.setStrategyName("MinIO");
        config.setIsEnable(true);

        // 第一次加载故意慢一点，保证第二次调用时它仍在途
        when(repository.findOneByIsEnableAndIsDeleted(anyBoolean(), anyBoolean()))
                .thenReturn(Mono.delay(Duration.ofMillis(300)).thenReturn(config));

        manager.reloadConfig().subscribe(v -> { }, e -> { });

        assertThrows(BusinessException.class,
                () -> manager.reloadConfig().block(),
                "管理员连点两次「刷新配置」时，第二次必须得到明确反馈，"
                        + "而不是一个看起来成功、实际什么都没做的空信号");
    }

    @Test
    @DisplayName("读到启用配置：标记已就绪，并采用该策略")
    void enabledConfig_marksReady() {
        StorageConfig config = new StorageConfig();
        config.setStrategyKey("minio");
        config.setStrategyName("MinIO");
        config.setIsEnable(true);

        when(repository.findOneByIsEnableAndIsDeleted(anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(config));

        manager.reloadConfig().block();

        assertTrue(manager.isReady(), "读到启用配置后应当变为就绪");
        assertTrue("minio".equals(manager.getActiveStrategyKey()));
    }
}
