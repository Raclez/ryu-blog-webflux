package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.service.impl.FileServiceImpl;
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
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传的事务边界与孤儿文件补偿。
 *
 * <p><b>它守的是什么</b>：上传整条链（读流 → 嗅探 → <b>网络传对象存储</b> → 落库）
 * 罩在一个 {@code @Transactional} 里。真正需要事务的只有最后一句 {@code save}，
 * 而事务包住整条链带来两个后果：
 * <ol>
 *   <li>回滚时对象存储里的文件已经写进去了 —— <b>孤儿文件</b>，没人引用也删不掉；</li>
 *   <li>事务期间占住一条 R2DBC 连接，批量上传时 N 条连接同时占用，
 *       正是 CLAUDE.md 记的 {@code Request queue is full} 的触发条件。</li>
 * </ol>
 *
 * <p>所以这里盯两件事：上传方法上<b>不再有</b> {@code @Transactional}；
 * 落库失败时刚传上去的对象要<b>被删掉</b>。
 *
 * @author ryu
 */
class FileUploadRollbackTest {

    private FileRepository fileRepository;
    private FileStorageStrategy strategy;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.findFirstByChecksumAndCreatorIdAndIsDeleted(anyString(), anyLong(), anyInt()))
                .thenReturn(Mono.empty());

        strategy = mock(FileStorageStrategy.class);
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));
        when(strategy.deleteFile(anyString())).thenReturn(Mono.just(true));
        doAnswer(inv -> ((Flux<DataBuffer>) inv.getArgument(0))
                .then(Mono.just("images/2026/10/30/photo.png")))
                .when(strategy).uploadFile(any(), anyString(), anyLong());

        StorageStrategyRegistry strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getActiveStrategy()).thenReturn(Mono.just(strategy));
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        @SuppressWarnings("unchecked")
        ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
        StorageConfigManager configManager = mock(StorageConfigManager.class);

        service = new FileUploadService(fileRepository, redis, strategyRegistry, configManager);
    }

    @Test
    @DisplayName("落库失败：刚传上去的对象要删掉，否则留下孤儿文件")
    void saveFailure_deletesUploadedObject() {
        when(fileRepository.save(any(File.class)))
                .thenReturn(Mono.error(new IllegalStateException("数据库拒绝写入")));

        assertThrows(IllegalStateException.class,
                () -> service.handleFileUpload(partOf("photo.png"), new UploadOptionsDTO(), 1L).block());

        verify(strategy).deleteFile("images/2026/10/30/photo.png");
    }

    @Test
    @DisplayName("上传方法不再持有事务注解：事务只该罩住最后那句落库")
    void uploadMethodsAreNotTransactional() throws Exception {
        assertFalse(FileUploadService.class
                        .getMethod("handleFileUpload", FilePart.class, UploadOptionsDTO.class, Long.class)
                        .isAnnotationPresent(Transactional.class),
                "handleFileUpload 上的 @Transactional 罩住了「网络传对象存储」整段 —— "
                        + "回滚时对象存储里已经写进去了，留下孤儿文件，还占着一条 R2DBC 连接");

        assertFalse(FileUploadService.class
                        .getMethod("completeMultipartUpload", String.class, java.util.List.class,
                                UploadOptionsDTO.class, Long.class)
                        .isAnnotationPresent(Transactional.class),
                "completeMultipartUpload 同理：合并分片是存储侧动作，不该被事务罩住");

        // 门面层也要去掉 —— CLAUDE.md 记过：只改一层等于没改，
        // 外层的 @Transactional 照样会把整条链罩住
        assertFalse(FileServiceImpl.class
                        .getMethod("handleFileUpload", FilePart.class, UploadOptionsDTO.class, Long.class)
                        .isAnnotationPresent(Transactional.class),
                "FileServiceImpl 门面上的 @Transactional 会把内层去掉的效果完全抵消");

        assertFalse(FileServiceImpl.class
                        .getMethod("completeMultipartUpload", String.class, java.util.List.class,
                                UploadOptionsDTO.class, Long.class)
                        .isAnnotationPresent(Transactional.class),
                "门面层的 completeMultipartUpload 同样不能带事务");
    }

    private FilePart partOf(String fileName) {
        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
        };
        FilePart part = mock(FilePart.class);
        when(part.filename()).thenReturn(fileName);
        when(part.headers()).thenReturn(new HttpHeaders());
        when(part.content()).thenReturn(Flux.just(
                new DefaultDataBufferFactory().wrap(Arrays.copyOf(png, png.length))));
        return part;
    }
}
