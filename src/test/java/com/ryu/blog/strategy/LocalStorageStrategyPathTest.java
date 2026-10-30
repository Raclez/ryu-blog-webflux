package com.ryu.blog.strategy;

import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本地存储的路径包含性护栏。
 *
 * <p><b>它守的是什么</b>：{@code LocalStorageStrategy.resolveWithinBasePath}
 * 是修过一次的<b>任意文件读取</b>漏洞 —— 文件路径来自数据库，
 * 而数据库里的 {@code file_path} 可能有 {@code ../}（历史数据、手工改库、
 * 或者别的入口写进去的）。修复方式是「归一化之后要求结果仍然在根目录之下」，
 * <b>但这道修复本身没有任何回归测试</b>：哪天有人把
 * {@code Path.startsWith} 换成字符串前缀比较，或者图省事去掉归一化，
 * 漏洞会悄无声息地回来。
 *
 * <p>本测试用真实的 {@link LocalStorageStrategy} + 真实的临时目录跑，
 * 走的是对外接口（{@code fileExists} / {@code downloadFile} / {@code deleteFile}），
 * 而不是反射调私有方法 —— 私有方法改签名是允许的，护栏不该被它绑死。
 *
 * @author ryu
 */
class LocalStorageStrategyPathTest {

    /**
     * 根目录：{@code tmp/uploads}。
     *
     * <p>刻意<b>不</b>直接拿 {@code @TempDir} 当根目录 ——
     * 「同前缀兄弟目录」那条用例需要一个和根目录同前缀的邻居目录。
     */
    @TempDir
    Path tmp;

    private Path base;
    private Path sibling;
    private LocalStorageStrategy strategy;

    @BeforeEach
    void setUp() throws IOException {
        base = Files.createDirectories(tmp.resolve("uploads"));
        sibling = Files.createDirectories(tmp.resolve("uploads-evil"));

        StorageConfigManager configManager = mock(StorageConfigManager.class);
        when(configManager.getConfigPropertyAsync(eq("local"), eq("basePath"), any()))
                .thenReturn(Mono.just(base.toString()));
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());

        strategy = new LocalStorageStrategy(configManager);
    }

    // ==================================================================
    // 1. 逃逸
    // ==================================================================

    @Test
    @DisplayName("相对路径里的 ../ 被拒绝")
    void parentTraversal_isRejected() {
        assertThrows(BusinessException.class,
                () -> strategy.fileExists("../secret.txt").block(),
                "../secret.txt 逃出了根目录，必须拒绝");
    }

    @Test
    @DisplayName("多级 ../ 被拒绝")
    void deepParentTraversal_isRejected() {
        assertThrows(BusinessException.class,
                () -> strategy.fileExists("../../../../etc/passwd").block(),
                "多级 ../ 逃出了根目录，必须拒绝");
    }

    @Test
    @DisplayName("绝对路径被拒绝")
    void absolutePath_isRejected() {
        assertThrows(BusinessException.class,
                () -> strategy.fileExists("/etc/passwd").block(),
                "绝对路径必须被拒绝 —— 它与根目录无关");
    }

    @Test
    @DisplayName("Windows 盘符绝对路径被拒绝")
    void windowsAbsolutePath_isRejected() {
        String outside = sibling.resolve("secret.txt").toAbsolutePath().toString();

        assertThrows(BusinessException.class,
                () -> strategy.fileExists(outside).block(),
                "指向根目录之外的绝对路径必须被拒绝: " + outside);
    }

    // ==================================================================
    // 2. 空路径：不能落到根目录本身
    // ==================================================================

    @Test
    @DisplayName("空路径被拒绝（不能指向根目录本身）")
    void emptyPath_isRejected() {
        assertThrows(BusinessException.class,
                () -> strategy.fileExists("").block(),
                "空路径不能解析成根目录本身 —— 那等于把根目录当成一个文件来操作");
    }

    @Test
    @DisplayName("null 路径被拒绝")
    void nullPath_isRejected() {
        assertThrows(BusinessException.class,
                () -> strategy.fileExists(null).block(),
                "null 路径同样不能解析成根目录本身");
    }

    // ==================================================================
    // 3. 同前缀兄弟目录：字符串前缀比较会放过它
    // ==================================================================

    @Test
    @DisplayName("同前缀的兄弟目录（uploads-evil）不被误判为在根目录内")
    void siblingDirectoryWithSamePrefix_isRejected() throws IOException {
        Files.writeString(sibling.resolve("secret.txt"), "top secret");

        // ../uploads-evil/secret.txt 归一化后落在 tmp/uploads-evil 下 ——
        // 它是 tmp/uploads 的「同前缀兄弟」，字符串前缀比较会放它过关
        assertThrows(BusinessException.class,
                () -> strategy.fileExists("../uploads-evil/secret.txt").block(),
                "同前缀兄弟目录在根目录之外，必须拒绝");
    }

    @Test
    @DisplayName("同前缀兄弟目录里的文件确实读不到")
    void siblingFileIsNotReachable() throws IOException {
        Files.writeString(sibling.resolve("secret.txt"), "top secret");

        assertThrows(BusinessException.class,
                () -> strategy.downloadFile("../uploads-evil/secret.txt")
                        .flatMapMany(flux -> flux).collectList().block(),
                "同前缀兄弟目录里的文件必须读不到");
    }

    // ==================================================================
    // 4. 正常路径：不能被误伤
    // ==================================================================

    @Test
    @DisplayName("根目录内的正常相对路径可以访问")
    void normalRelativePath_isAllowed() throws IOException {
        Path nested = Files.createDirectories(base.resolve("images/2026/10/30"));
        Files.writeString(nested.resolve("ok.png"), "png-bytes");

        assertTrue(strategy.fileExists("images/2026/10/30/ok.png").block(),
                "根目录内的正常文件必须能访问 —— 护栏不能把正常路径一起拦掉");

        assertFalse(strategy.fileExists("images/2026/10/30/nope.png").block(),
                "根目录内不存在的文件应当返回 false，而不是抛异常");
    }
}
