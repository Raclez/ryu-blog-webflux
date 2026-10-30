package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.MultipartCapable;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

/**
 * 分片会话必须<b>只存在 Redis 里</b>，而不是进程内的一份 static Map。
 *
 * <p><b>它守的是什么</b>：进程内 Map 在多实例部署下是分裂的 ——
 * 用户在 A 节点建了会话，下一个分片被负载均衡打到 B 节点，B 查不到，
 * 整个分片上传就挂了；进程重启同理。而且那份 Map 从不清理，
 * 中途放弃的上传会永久占着堆。
 *
 * <p>验证方式很直接：两个<b>互不相识</b>的服务实例共用同一个 Redis，
 * 第一个建会话、第二个续传。只要还依赖进程内状态，这条就会失败。
 *
 * @author ryu
 */
class MultipartSessionRedisTest {

    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    @DisplayName("另一个服务实例能续传同一个分片会话（说明状态不在进程内）")
    void sessionSurvivesAcrossServiceInstances() {
        // 两个节点连同一个 Redis
        FakeMultipartRedis store = FakeMultipartRedis.shared();
        FileUploadService nodeA = newService(store.template());
        FileUploadService nodeB = newService(store.template());

        Map<String, String> init = nodeA
                .initiateMultipartUpload("big.png", 1024L, new UploadOptionsDTO(), 1L)
                .block();
        assertNotNull(init);
        String uploadId = init.get("uploadId");

        // A 节点传第 1 片
        assertEquals("etag-1", nodeA.uploadPart(uploadId, 1, new byte[100], 1L).block());

        // B 节点接着传第 2 片：它从没见过这个会话，只能从 Redis 读
        assertEquals("etag-2", nodeB.uploadPart(uploadId, 2, new byte[100], 1L).block(),
                "另一个实例续传失败 —— 说明会话还依赖进程内状态，多实例部署下分片上传必挂");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private FileUploadService newService(ReactiveRedisTemplate<String, Object> redis) {
        FileStorageStrategy strategy = mock(FileStorageStrategy.class,
                withSettings().extraInterfaces(MultipartCapable.class));
        MultipartCapable multipart = (MultipartCapable) strategy;
        when(strategy.getStrategyKey()).thenReturn("local");
        when(multipart.initiateMultipartUpload(anyString(), anyLong(), anyMap()))
                .thenReturn(Mono.just(new HashMap<>(Map.of("uploadId", "strategy-upload-1"))));
        when(multipart.uploadPart(anyString(), anyInt(), any()))
                .thenAnswer(inv -> Mono.just("etag-" + inv.getArgument(1)));

        StorageStrategyRegistry registry = mock(StorageStrategyRegistry.class);
        when(registry.getActiveStrategy()).thenReturn(Mono.just(strategy));
        when(registry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        return new FileUploadService(fileRepository, redis, registry, mock(StorageConfigManager.class));
    }

}
