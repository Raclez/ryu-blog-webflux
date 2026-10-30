package com.ryu.blog.service.impl.file;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.MultipartCapable;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.utils.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 分片上传的「主动内容」护栏。
 *
 * <p><b>它守的是什么</b>：单文件上传路径已经按嗅探出的 MIME 拦主动内容了，
 * 但分片上传是<b>另一条落库路径</b>（{@code completeMultipartUpload} → {@code createFileEntity} → save），
 * 上面既没有这道判定，也从来没设置过 {@code mimeType} / {@code fileType}。
 * 于是「分片拼出来的 HTML」会绕过整条护栏入库。
 *
 * <p>判定依据是<b>第 1 个分片</b>的头部字节 —— 文件的头就在第 1 片里，
 * 拿后面的分片去嗅探只会得到无关结论。
 *
 * @author ryu
 */
class FileMultipartActiveContentTest {

    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileStorageStrategy strategy;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // 分片能力已从核心接口拆出，mock 必须同时实现 MultipartCapable
        strategy = mock(FileStorageStrategy.class, withSettings().extraInterfaces(MultipartCapable.class));
        MultipartCapable multipart = (MultipartCapable) strategy;
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));
        when(strategy.deleteFile(anyString())).thenReturn(Mono.just(true));
        when(multipart.initiateMultipartUpload(anyString(), anyLong(), anyMap()))
                .thenReturn(Mono.just(new HashMap<>(Map.of("uploadId", "strategy-upload-1"))));
        when(multipart.uploadPart(anyString(), anyInt(), any()))
                .thenAnswer(inv -> Mono.just("etag-" + inv.getArgument(1)));
        when(multipart.completeMultipartUpload(anyString(), anyList()))
                .thenReturn(Mono.just("images/2026/10/30/merged.png"));

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
    @DisplayName("分片拼出的 HTML（photo.png）：按主动内容拒绝，并删掉合并后的对象")
    void multipartHtml_isRejectedAndObjectDeleted() {
        String uploadId = startSession("photo.png");

        byte[] html = "<html><body><script>alert(document.cookie)</script></body></html>"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals("etag-1", service.uploadPart(uploadId, 1, html, 1L).block());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeMultipartUpload(uploadId, List.of("etag-1"), new UploadOptionsDTO(), 1L).block());

        assertEquals(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED, ex.getCode(),
                "分片上传也必须在落库前按内容拦下主动内容");

        verify(strategy).deleteFile("images/2026/10/30/merged.png");
        verify(fileRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("分片拼出的真图：落库时必须带上嗅探出的 mimeType 与 fileType")
    void multipartGenuineImage_recordsMimeAndFileType() {
        String uploadId = startSession("photo.png");

        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
                0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0
        };
        service.uploadPart(uploadId, 1, png, 1L).block();

        service.completeMultipartUpload(uploadId, List.of("etag-1"), new UploadOptionsDTO(), 1L).block();

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());
        File saved = captor.getValue();

        assertEquals("image/png", saved.getMimeType(),
                "分片上传落库时 mimeType 不能是 null —— 否则下载接口只能回退成八位字节流");
        // 与单文件上传路径同一来源（FileUtils.getFileType），不在这里写死大小写
        assertEquals(FileUtils.getFileType("image/png"), saved.getFileType());
        verify(strategy, never()).deleteFile(anyString());
    }

    /**
     * 走一遍 initiate，拿到应用层的 uploadId。
     */
    private String startSession(String fileName) {
        Map<String, String> result = service
                .initiateMultipartUpload(fileName, 1024L, new UploadOptionsDTO(), 1L)
                .block();
        assertNotNull(result);
        return result.get("uploadId");
    }

    // ==================================================================
    // 会话归属：必须 fail-closed
    // ==================================================================

    @Test
    @DisplayName("请求者身份为空：不得因为「没法比对」就放行别人的上传会话")
    void nullRequester_isDenied() {
        String uploadId = startSession("owner-only.png");

        assertThrows(PermissionDeniedException.class,
                () -> service.uploadPart(uploadId, 1, new byte[]{1, 2, 3}, null).block(),
                "身份为空的请求必须被拒 —— 「比不了」不等于「没问题」");
    }

    @Test
    @DisplayName("会话没有记录归属：同样拒绝，而不是放行")
    void sessionWithoutOwner_isDenied() {
        // 早期会话可能是在「没人登录」的上下文中创建的 —— userId 为空。
        // 造一条这样的会话：initiate 时 uploaderId 传 null。
        String uploadId = service
                .initiateMultipartUpload("legacy.png", 1024L, new UploadOptionsDTO(), null)
                .block()
                .get("uploadId");

        assertThrows(PermissionDeniedException.class,
                () -> service.uploadPart(uploadId, 1, new byte[]{1, 2, 3}, 1L).block(),
                "归属未知的会话必须拒绝，不能默认「谁都能续传」");
    }
}
