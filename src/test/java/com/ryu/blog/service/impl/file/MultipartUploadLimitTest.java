package com.ryu.blog.service.impl.file;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.MultipartCapable;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

/**
 * 分片上传的「真实限额」护栏。
 *
 * <p><b>它守的是什么</b>：{@code initiateMultipartUpload} 校验的 {@code fileSize}
 * 是<b>客户端声明</b>的，而 {@code uploadPart} 对分片字节数<b>没有任何上限</b> ——
 * 声明 10MB、实际传 2GB 可以一路传完；{@code completeMultipartUpload} 也不复核总大小。
 * 于是「声明值」形同虚设，堆内存的上限完全由客户端说了算。
 *
 * <p>本测试盯两件事：
 * <ol>
 *   <li>累计已收字节数超过「声明值 + 容忍」→ 抛 {@code fileSizeLimit}；</li>
 *   <li>未超过 → 正常完成合并。</li>
 * </ol>
 *
 * @author ryu
 */
class MultipartUploadLimitTest {

    private static final int MB = 1024 * 1024;

    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileStorageStrategy strategy;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // 分片能力已从核心接口拆出：mock 必须同时实现 MultipartCapable，
        // 否则 FileUploadService 会在进入存储逻辑前按「策略不支持分片」拒绝
        strategy = mock(FileStorageStrategy.class, withSettings().extraInterfaces(MultipartCapable.class));
        MultipartCapable multipart = (MultipartCapable) strategy;
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));
        when(multipart.initiateMultipartUpload(anyString(), anyLong(), anyMap()))
                .thenReturn(Mono.just(new HashMap<>(Map.of("uploadId", "strategy-upload-1"))));
        when(multipart.uploadPart(anyString(), anyInt(), any()))
                .thenAnswer(inv -> Mono.just("etag-" + inv.getArgument(1)));
        when(multipart.completeMultipartUpload(anyString(), anyList()))
                .thenReturn(Mono.just("images/2026/10/30/merged.png"));
        when(strategy.deleteFile(anyString())).thenReturn(Mono.just(true));

        strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getActiveStrategy()).thenReturn(Mono.just(strategy));
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        // 分片会话与分片状态都在 Redis 里（后者是 Hash），
        // 用一个同时支持两种结构的替身，两个服务实例共享同一份数据
        ReactiveRedisTemplate<String, Object> redis = FakeMultipartRedis.create();
        StorageConfigManager configManager = mock(StorageConfigManager.class);
        service = new FileUploadService(fileRepository, redis, strategyRegistry, configManager);
    }

    @Test
    @DisplayName("累计分片字节数超出「声明值 + 容忍」：抛 fileSizeLimit")
    void cumulativeBytesExceedDeclared_isRejected() {
        // 声明 1MB，容忍 = max(10%, 1MB) = 1MB → 最多允许 2MB
        String uploadId = startSession("big.png", MB);

        // 前两片刚好 2MB，达到上限但未越界
        assertEquals("etag-1", service.uploadPart(uploadId, 1, new byte[MB], 1L).block());
        assertEquals("etag-2", service.uploadPart(uploadId, 2, new byte[MB], 1L).block());

        // 第 3 片再 1MB → 3MB，越界
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadPart(uploadId, 3, new byte[MB], 1L).block());

        assertEquals(ErrorCodeConstants.FILE_SIZE_LIMIT, ex.getCode(),
                "累计字节数超过「声明值 + 容忍」必须按「文件大小超出限制」拒绝");
    }

    @Test
    @DisplayName("同一分片号重传不重复计数：重试不该把正常上传顶成超限")
    void retriedPart_doesNotDoubleCount() {
        // 声明 1MB，容忍 1MB → 上限 2MB
        String uploadId = startSession("retry.png", MB);

        service.uploadPart(uploadId, 1, new byte[MB], 1L).block();
        // 第 1 片重传两次（客户端重试）—— 若按「累加」记账，这里就会误判超限
        service.uploadPart(uploadId, 1, new byte[MB], 1L).block();
        service.uploadPart(uploadId, 1, new byte[MB], 1L).block();

        assertEquals("etag-2", service.uploadPart(uploadId, 2, new byte[MB], 1L).block(),
                "重传同一片不应把累计字节数算多次");
    }

    @Test
    @DisplayName("未超限：正常合并完成")
    void withinLimit_completes() {
        String uploadId = startSession("ok.png", 3L * MB);

        service.uploadPart(uploadId, 1, new byte[MB], 1L).block();
        service.uploadPart(uploadId, 2, new byte[MB], 1L).block();

        assertNotNull(service.completeMultipartUpload(uploadId, List.of("etag-1", "etag-2"),
                new UploadOptionsDTO(), 1L).block());
    }

    private String startSession(String fileName, long declaredSize) {
        Map<String, String> result = service
                .initiateMultipartUpload(fileName, declaredSize, new UploadOptionsDTO(), 1L)
                .block();
        assertNotNull(result);
        return result.get("uploadId");
    }

    @Test
    @DisplayName("落库的文件大小必须是**实际收到**的字节数，而不是客户端声明的值")
    void complete_recordsActualSizeNotDeclaredSize() {
        // 声明 2MB，实际只传 100 字节 —— 在容忍上限内，允许完成
        String uploadId = startSession("lie.png", 2L * MB);
        service.uploadPart(uploadId, 1, new byte[100], 1L).block();

        service.completeMultipartUpload(uploadId, List.of("etag-1"), new UploadOptionsDTO(), 1L).block();

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());

        assertEquals(100L, captor.getValue().getFileSize(),
                "落库的是客户端声明的 2MB —— 下载接口会按这个值下发 Content-Length，"
                        + "客户端拿着 2GB 的头部等一个 100 字节的响应体，一直等到超时");
    }
}
