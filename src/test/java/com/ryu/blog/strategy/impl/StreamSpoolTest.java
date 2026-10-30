package com.ryu.blog.strategy.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 上传流落临时文件：内容要完整，失败要清理。
 *
 * <p>它替掉的是 {@code DataBufferUtils.join(整个流)} —— 那一步会把
 * 已经流式化的上传重新变回「整个文件进堆」。
 *
 * @author ryu
 */
class StreamSpoolTest {

    @Test
    @DisplayName("多段流完整落盘，字节数正确，用完后临时文件被删除")
    void spoolsAllChunksAndCleansUp() throws Exception {
        Flux<DataBuffer> content = Flux.just(buffer("AAA"), buffer("BBB"), buffer("CCC"));

        StreamSpool.SpooledFile spooled = StreamSpool.spool(content).block();

        assertEquals(9L, spooled.size());
        assertEquals("AAABBBCCC", Files.readString(spooled.path()),
                "落盘内容必须与分段流拼接一致");

        Path path = spooled.path();
        spooled.close();
        assertFalse(Files.exists(path), "用完必须删掉临时文件");
    }

    @Test
    @DisplayName("流失败：临时文件被清理，不能留下垃圾")
    void failedStream_leavesNoTempFile() {
        Flux<DataBuffer> broken = Flux.concat(
                Flux.just(buffer("AAA")),
                Flux.error(new IllegalStateException("连接中断")));

        assertThrows(IllegalStateException.class, () -> StreamSpool.spool(broken).block());

        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (var files = Files.list(tmpDir)) {
            long leftovers = files
                    .filter(path -> path.getFileName().toString().startsWith("ryu-upload-"))
                    .count();
            assertEquals(0, leftovers, "失败的落盘留下了 " + leftovers + " 个临时文件");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private DataBuffer buffer(String text) {
        return new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
    }
}
