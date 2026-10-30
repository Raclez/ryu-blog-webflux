package com.ryu.blog.service.impl.file;

import com.ryu.blog.entity.File;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.FileStorageStrategy;

import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.vo.FileDownloadVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公开直链的<b>路径形态</b>契约。
 *
 * <p><b>它守的是什么</b>：路由写的是 {@code GET /files/public/{*path}}，
 * 而 Spring 的 {@code {*path}}（CaptureTheRest）捕获到的值是
 * <b>带前导斜杠</b>的剩余路径 —— {@code /files/public/images/2026/a.png}
 * 会得到 {@code /images/2026/a.png}。
 *
 * <p>库里 {@code file_path} 存的是不带前导斜杠的 {@code images/2026/a.png}
 * （{@code buildObjectNameAsync} 走 {@code normalizePath} 会去掉）。
 * 两边一相减，公开直链就<b>永远查不到文件</b> —— 文章里的图片全部 404，
 * 而按 ID 的下载/预览却是好的，所以本地手工验证很容易漏掉。
 *
 * @author ryu
 */
class PublicFileRouteContractTest {

    private FileRepository fileRepository;
    private FileDownloadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);

        FileStorageStrategy strategy = mock(FileStorageStrategy.class);
        when(strategy.downloadFile(anyString())).thenReturn(Mono.just(
                Flux.just(new DefaultDataBufferFactory().wrap("img".getBytes()))));

        StorageStrategyRegistry registry = mock(StorageStrategyRegistry.class);
        when(registry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        service = new FileDownloadService(fileRepository, registry);
    }

    @Test
    @DisplayName("路由捕获到带前导斜杠的路径时，仍能按库里的相对路径查到文件")
    void leadingSlashFromRouteIsNormalized() {
        File file = new File();
        file.setId(1L);
        file.setFileName("a.png");
        file.setFilePath("images/2026/10/30/a.png");
        file.setStorageType("local");
        file.setFileSize(3L);
        file.setMimeType("image/png");

        // 任何路径都返回这份文件，这样失败信息会指向「查的路径不对」，
        // 而不是一个难读的 NullPointer
        when(fileRepository.findByFilePathAndIsDeleted(anyString(), anyInt()))
                .thenReturn(Mono.just(file));

        FileDownloadVO download = service.downloadPublicByPath("/images/2026/10/30/a.png").block();

        assertNotNull(download, "带前导斜杠的公开直链必须能查到文件 —— 否则文章里的图片全部 404");
        assertEquals("a.png", download.getFileName());

        verify(fileRepository).findByFilePathAndIsDeleted(eq("images/2026/10/30/a.png"), eq(0));
    }
}
