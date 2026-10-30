package com.ryu.blog.strategy;

import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本地策略的分片上传：分片要落盘，不要待在堆里。
 *
 * <p><b>它守的是什么</b>：早先 {@code uploadPart} 把每个分片存成内存 {@code byte[]}，
 * {@code completeMultipartUpload} 再用 {@code ByteArrayOutputStream} 拼一遍 ——
 * 峰值内存约 <b>2 倍文件大小</b>，而且全部在堆上。传一个 2GB 的文件，
 * 进程要么 OOM，要么疯狂 GC。
 *
 * <p>分片本来就是「已经有磁盘/网络在搬数据」的场景，
 * 正确的做法是每片直接写临时文件、合并时按分片号顺序追加。
 *
 * <p>本测试用真实的 {@link LocalStorageStrategy} + 真实临时目录，
 * 走对外接口断言：<b>磁盘上确实出现了分片文件</b>，合并顺序正确，合并后临时目录被清理。
 *
 * @author ryu
 */
class LocalMultipartStorageTest {

    @TempDir
    Path tmp;

    private Path base;
    private LocalStorageStrategy strategy;

    @BeforeEach
    void setUp() throws IOException {
        base = Files.createDirectories(tmp.resolve("uploads"));

        StorageConfigManager configManager = mock(StorageConfigManager.class);
        when(configManager.getConfigPropertyAsync(eq("local"), eq("basePath"), any()))
                .thenReturn(Mono.just(base.toString()));
        when(configManager.getConfigPropertyAsync(eq("local"), eq("prefix"), any()))
                .thenReturn(Mono.just(""));
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());

        strategy = new LocalStorageStrategy(configManager);
    }

    @Test
    @DisplayName("分片上传后分片落在磁盘上，而不是只存在内存里")
    void uploadPart_writesToDisk() {
        String uploadId = start("big.bin");

        strategy.uploadPart(uploadId, 1, buffer("AAA")).block();
        strategy.uploadPart(uploadId, 2, buffer("BBB")).block();

        Path partDir = base.resolve(".multipart").resolve(uploadId);
        assertTrue(Files.isDirectory(partDir),
                "分片必须落到临时目录 " + partDir + " —— 全放内存时峰值是 2 倍文件大小");

        try (Stream<Path> files = Files.list(partDir)) {
            assertEquals(2, files.count(), "两片应当对应两个临时分片文件");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("合并结果等于分片按分片号顺序拼接")
    void complete_mergesInPartOrder() throws IOException {
        String uploadId = start("big.bin");

        strategy.uploadPart(uploadId, 1, buffer("AAA")).block();
        strategy.uploadPart(uploadId, 2, buffer("BBB")).block();
        strategy.uploadPart(uploadId, 3, buffer("CCC")).block();

        String objectName = strategy.completeMultipartUpload(uploadId,
                List.of("part-1", "part-2", "part-3")).block();

        assertNotNull(objectName);
        assertEquals("AAABBBCCC", Files.readString(base.resolve(objectName)),
                "合并不是按字典序或插入序，而是按分片号数值顺序拼接");
    }

    @Test
    @DisplayName("合并完成后临时目录被清理，不留下分片垃圾")
    void complete_cleansTempDirectory() throws IOException {
        String uploadId = start("big.bin");

        strategy.uploadPart(uploadId, 1, buffer("AAA")).block();
        strategy.completeMultipartUpload(uploadId, List.of("part-1")).block();

        assertFalse(Files.exists(base.resolve(".multipart").resolve(uploadId)),
                "合并完成后临时分片目录必须删掉 —— 否则每传一次大文件就在磁盘上留一堆垃圾");
    }

    @Test
    @DisplayName("终止上传后临时目录同样被清理")
    void abort_cleansTempDirectory() {
        String uploadId = start("big.bin");

        strategy.uploadPart(uploadId, 1, buffer("AAA")).block();
        strategy.abortMultipartUpload(uploadId).block();

        assertFalse(Files.exists(base.resolve(".multipart").resolve(uploadId)),
                "用户放弃上传后临时分片不能永久留在磁盘上");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private String start(String fileName) {
        Map<String, String> init = strategy.initiateMultipartUpload(fileName, 9L, Map.of()).block();
        assertNotNull(init);
        return init.get("uploadId");
    }

    private Flux<DataBuffer> buffer(String text) {
        return Flux.just(new DefaultDataBufferFactory()
                .wrap(text.getBytes(StandardCharsets.UTF_8)));
    }
}
