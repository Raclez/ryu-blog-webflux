package com.ryu.blog.service.impl.file;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传的「主动内容」护栏。
 *
 * <p><b>它守的是什么</b>：扩展名白名单只看<b>文件名</b>，而落库的 MIME 是
 * 按<b>内容</b>嗅探出来的。于是「内容是一段 HTML、名字叫 photo.png」可以
 * 一路走完白名单，最后以 {@code text/html} 入库 —— 文件名合法，内容却是可执行的主动内容。
 *
 * <p>这类文件一旦被浏览器按 HTML 渲染就是存储型 XSS。当前两个内联端点
 * （{@code /files/public/**}、{@code /files/preview/**}）都下发了
 * {@code nosniff} + {@code CSP: sandbox}，所以现在打不通；但那是<b>响应头</b>
 * 这一层的防线，一旦文件改由对象存储直读（桶公开）就完全失效。
 * 这里在<b>入库前</b>加一道：嗅探出的 MIME 属于主动内容 → 拒绝，
 * 并且把刚传上去的那个对象删掉，不留下孤儿文件。
 *
 * @author ryu
 */
class FileActiveContentUploadTest {

    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileStorageStrategy strategy;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(fileRepository.findFirstByChecksumAndCreatorIdAndIsDeleted(anyString(), anyLong(), anyInt()))
                .thenReturn(Mono.empty());

        strategy = mock(FileStorageStrategy.class);
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));
        when(strategy.deleteFile(anyString())).thenReturn(Mono.just(true));
        // 策略把流读空并返回存储路径
        doAnswer(inv -> ((Flux<DataBuffer>) inv.getArgument(0))
                .then(Mono.just("images/2026/10/30/photo.png")))
                .when(strategy).uploadFile(any(), anyString(), anyLong());

        strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getActiveStrategy()).thenReturn(Mono.just(strategy));
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        @SuppressWarnings("unchecked")
        ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
        StorageConfigManager configManager = mock(StorageConfigManager.class);

        service = new FileUploadService(fileRepository, redis, strategyRegistry, configManager);
    }

    @Test
    @DisplayName("内容为 HTML 的 photo.png：按主动内容拒绝，且刚上传的对象被删掉")
    void htmlDisguisedAsImage_isRejectedAndObjectCleanedUp() {
        String html = "<html><body><script>alert(document.cookie)</script></body></html>";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleFileUpload(partOf("photo.png", html), new UploadOptionsDTO(), 1L).block());

        assertEquals(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED, ex.getCode(),
                "按内容识别出的主动内容必须按「文件类型不允许」拒绝");

        verify(strategy).deleteFile("images/2026/10/30/photo.png");
        verify(fileRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("真正的内容（PNG 魔数）不受影响，照常入库")
    void genuineImage_isAccepted() {
        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
        };

        service.handleFileUpload(partOfBytes("photo.png", png), new UploadOptionsDTO(), 1L).block();

        verify(fileRepository).save(any(File.class));
        verify(strategy, never()).deleteFile(anyString());
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private FilePart partOf(String fileName, String text) {
        return partOfBytes(fileName, text.getBytes(StandardCharsets.UTF_8));
    }

    private FilePart partOfBytes(String fileName, byte[] bytes) {
        FilePart part = mock(FilePart.class);
        when(part.filename()).thenReturn(fileName);
        when(part.headers()).thenReturn(new HttpHeaders());
        when(part.content()).thenReturn(Flux.just(
                new DefaultDataBufferFactory().wrap(Arrays.copyOf(bytes, bytes.length))));
        return part;
    }
}
