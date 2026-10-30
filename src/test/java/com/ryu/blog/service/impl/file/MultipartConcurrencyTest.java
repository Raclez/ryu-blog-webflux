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
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 并行分片上传不能互相覆盖。
 *
 * <p><b>它守的是什么</b>：分片是<b>并发</b>上传的 —— 每个 JS 分片上传器都会同时发好几片。
 * 早先每个分片都是「读出整份会话 → 改 parts → 整份写回」，
 * 两个分片各读到同一份快照，后写的那份把先写的那份<b>覆盖掉</b>：
 * local 策略还能靠临时目录合并出来，MinIO 那边丢了 ETag 就直接合并失败。
 *
 * <p>现在每个分片只往 Redis Hash 里写<b>自己那几个字段</b>（HSET 原子），互不干扰。
 * 这条测试用栅栏把两个分片卡在「都还没写回」的那一刻，再让它们同时写 ——
 * 只要还整份覆盖，就必然会丢一个 ETag。
 *
 * @author ryu
 */
class MultipartConcurrencyTest {

    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileStorageStrategy strategy;
    private MultipartCapable multipart;
    private FileUploadService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        strategy = mock(FileStorageStrategy.class, withSettings().extraInterfaces(MultipartCapable.class));
        multipart = (MultipartCapable) strategy;
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));
        when(multipart.initiateMultipartUpload(anyString(), anyLong(), anyMap()))
                .thenReturn(Mono.just(new HashMap<>(Map.of("uploadId", "strategy-upload-1"))));
        when(multipart.completeMultipartUpload(anyString(), anyList()))
                .thenReturn(Mono.just("images/2026/10/30/merged.png"));

        strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getActiveStrategy()).thenReturn(Mono.just(strategy));
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        ReactiveRedisTemplate<String, Object> redis = FakeMultipartRedis.create();
        service = new FileUploadService(fileRepository, redis, strategyRegistry, mock(StorageConfigManager.class));
    }

    @Test
    @DisplayName("两个分片并发上传：两个 ETag 都要留下，合并时都能交给策略")
    void concurrentParts_bothSurvive() {
        String uploadId = service
                .initiateMultipartUpload("big.png", 1024L, new UploadOptionsDTO(), 1L)
                .block()
                .get("uploadId");

        // 两个分片都到达「该写回了」这一刻之后才继续 —— 复现真正的并发窗口
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(multipart.uploadPart(anyString(), anyInt(), any())).thenAnswer(inv -> {
            int partNumber = inv.getArgument(1);
            barrier.await();                       // 等到两片都进来
            return Mono.just("etag-" + partNumber);
        });

        Mono<String> part1 = service.uploadPart(uploadId, 1, new byte[100], 1L)
                .subscribeOn(Schedulers.parallel());
        Mono<String> part2 = service.uploadPart(uploadId, 2, new byte[200], 1L)
                .subscribeOn(Schedulers.parallel());

        Mono.zip(part1, part2).block();

        ArgumentCaptor<List<String>> etags = ArgumentCaptor.forClass(List.class);
        service.completeMultipartUpload(uploadId, List.of(), new UploadOptionsDTO(), 1L).block();
        verify(multipart).completeMultipartUpload(anyString(), etags.capture());

        List<String> captured = new ArrayList<>(etags.getValue());
        assertNotNull(captured);
        assertEquals(2, captured.size(),
                "合并时只剩 " + captured.size() + " 个 ETag —— 并发分片互相覆盖了：" + captured);
        assertTrue(captured.containsAll(List.of("etag-1", "etag-2")),
                "两个分片的 ETag 都必须留下，实际为: " + captured);

        // 字节数也不能丢：落库的是两片之和
        ArgumentCaptor<File> saved = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(saved.capture());
        assertEquals(300L, saved.getValue().getFileSize(),
                "分片字节数记账丢了 —— 大小上限会因此失效");
    }
}
