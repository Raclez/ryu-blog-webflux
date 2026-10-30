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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 上传中途失败不能留下半截文件。
 *
 * <p><b>它守的是什么</b>：改流式之后，字节是<b>边收边写</b>到磁盘的。
 * 一旦中途出错（客户端断开、超过大小上限、存储异常），
 * 磁盘上就会留下一个只写了一半的文件 —— 而且它<b>没有任何引用</b>：
 * 落库发生在最后一步，失败了就没有记录，谁也找不到这个文件去删。
 * 反复触发就能把磁盘填满。
 *
 * @author ryu
 */
class LocalStoragePartialWriteTest {

    @TempDir
    Path tmp;

    private Path base;
    private LocalStorageStrategy strategy;

    @BeforeEach
    void setUp() throws IOException {
        base = Files.createDirectories(tmp.resolve("uploads"));

        StorageConfigManager configManager = mock(StorageConfigManager.class);
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenReturn(Mono.just(base.toString()));
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());

        strategy = new LocalStorageStrategy(configManager);
    }

    @Test
    @DisplayName("上传中途出错：磁盘上不能留下半截文件")
    void failedUpload_leavesNoPartialFile() throws IOException {
        // 先吐 4KB，然后报错 —— 模拟客户端断开 / 超限中断
        Flux<DataBuffer> broken = Flux.concat(
                Flux.just(new DefaultDataBufferFactory().wrap("AAAA".getBytes(StandardCharsets.UTF_8))),
                Flux.error(new IllegalStateException("客户端断开")));

        assertThrows(IllegalStateException.class,
                () -> strategy.uploadFile(broken, "a.png", 4).block());

        try (Stream<Path> files = Files.walk(base)) {
            long leftover = files.filter(Files::isRegularFile).count();
            assertEquals(0, leftover,
                    "上传失败后磁盘上还剩 " + leftover + " 个文件 —— "
                            + "这些是没有任何引用的半截文件，谁也找不到它们去清理");
        }
    }
}
