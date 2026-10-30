package com.ryu.blog.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存储文件名的唯一性与「同名覆盖」的干净程度。
 *
 * <p><b>它守的是什么</b>：
 * <ol>
 *   <li>{@code generateUniqueFileName} 早先是 {@code yyyyMMddHHmmss} + 8 位十六进制 ——
 *       只有 <b>32 bit</b> 随机量、外加秒级时间戳。同一秒内并发上传就有碰撞窗口，
 *       而文件名一旦撞了，后写的文件会覆盖先写的；</li>
 *   <li>{@link LocalStorageStrategy#uploadFile} 打开文件通道用 {@code CREATE, WRITE}
 *       <b>没有 {@code TRUNCATE_EXISTING}</b> —— 撞名时新内容只盖住文件头部，
 *       旧文件多出来的尾部字节会留在那里，于是「一张 10 字节的图」变成了
 *       「10 字节新图 + 旧文件尾巴」的坏文件。</li>
 * </ol>
 *
 * @author ryu
 */
class FileUniqueNameTest {

    @Test
    @DisplayName("同一秒内生成 10000 个名字：不允许重复")
    void manyNamesInSameSecond_areUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(seen.add(FileUtils.generateUniqueFileName("photo.png")),
                    "第 " + i + " 个名字与之前重复了");
        }
    }

    @Test
    @DisplayName("名字由完整 UUID（32 位十六进制）构成，不再靠秒级时间戳")
    void nameIsFullUuidNotTimestamp() {
        String name = FileUtils.generateUniqueFileName("photo.png");

        assertTrue(name.matches("^[0-9a-f]{32}\\.png$"),
                "文件名应当是「32 位十六进制 + 原扩展名」，实际为: " + name
                        + " —— 带时间戳前缀意味着同一秒内的随机量只有 32 bit");
    }

    @Test
    @DisplayName("保留原扩展名")
    void keepsExtension() {
        assertTrue(FileUtils.generateUniqueFileName("archive.tar.gz").endsWith(".gz"));
        assertTrue(FileUtils.generateUniqueFileName("a.JPEG").endsWith(".JPEG"));
    }

    @Test
    @DisplayName("名字里不含路径分隔符")
    void noPathSeparators() {
        String name = FileUtils.generateUniqueFileName("../../etc/passwd");
        assertFalse(name.contains("/") || name.contains("\\"),
                "生成的名字不能带路径分隔符，否则会被当成路径片段: " + name);
    }

    // ==================================================================
    // 同名覆盖：不能残留旧文件的尾部
    // ==================================================================

    @TempDir
    Path tmp;

    @Test
    @DisplayName("同名覆盖：新文件比旧文件短时，不能残留旧文件的尾部字节")
    void overwriteSameName_doesNotLeaveOldTail() throws IOException {
        Path base = Files.createDirectories(tmp.resolve("uploads"));

        StorageConfigManager configManager = mock(StorageConfigManager.class);
        when(configManager.getConfigPropertyAsync(eq("local"), eq("basePath"), any()))
                .thenReturn(Mono.just(base.toString()));
        when(configManager.getConfigPropertyAsync(eq("local"), eq("prefix"), any()))
                .thenReturn(Mono.just(""));
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());

        // 固定对象名，制造「两次上传撞到同一个路径」的局面
        LocalStorageStrategy strategy = new FixedNameStrategy(configManager, "images/2026/10/30/fixed.png");

        strategy.uploadFile(text("AAAAAAAAAAAAAAAAAAAA"), "fixed.png", 20).block();   // 20 字节
        strategy.uploadFile(text("BBBBBBBBBB"), "fixed.png", 10).block();            // 10 字节

        Path written = base.resolve("images/2026/10/30/fixed.png");
        assertEquals("BBBBBBBBBB", Files.readString(written),
                "第二次写入后文件必须恰好是新内容 —— 旧文件的尾部字节不能留在里面");
    }

    private Flux<DataBuffer> text(String value) {
        return Flux.just(new DefaultDataBufferFactory().wrap(value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 固定对象名的策略，用来复现「撞名」。
     */
    private static class FixedNameStrategy extends LocalStorageStrategy {

        private final String objectName;

        FixedNameStrategy(StorageConfigManager configManager, String objectName) {
            super(configManager);
            this.objectName = objectName;
        }

        @Override
        protected Mono<String> buildObjectNameAsync(String fileName) {
            return Mono.just(objectName);
        }
    }
}
