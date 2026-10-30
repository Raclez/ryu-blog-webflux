package com.ryu.blog.strategy;

import com.ryu.blog.strategy.impl.AliyunOssStorageStrategy;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import com.ryu.blog.strategy.impl.TencentCosStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存储策略契约：同一套断言，对<b>已实现</b>的策略验证闭环、
 * 对<b>未实现</b>的策略验证「明确报错而不是假成功」。
 *
 * <p><b>它守的是什么</b>：四个存储策略此前零直接测试、零契约测试。
 * 一个策略可以「上传成功」却什么都没存、可以「删除成功」却删了个空、
 * 可以「文件存在」而实际从来没写过 —— 没有任何一条测试能发现。
 *
 * <p>MinIO 不在这条契约里：它的每个方法都要真实的 MinIO 客户端
 * （{@code getMinioClient()} 会去连对象存储），纯单元测试跑不了。
 * 它需要嵌入式 MinIO 或 WireMock，属于后续补测。
 *
 * @author ryu
 */
class StorageStrategyContractTest {

    @TempDir
    Path tmp;

    private StorageConfigManager configManager;
    private Path base;

    @BeforeEach
    void setUp() throws IOException {
        base = Files.createDirectories(tmp.resolve("uploads"));

        configManager = mock(StorageConfigManager.class);
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenReturn(Mono.just(base.toString()));
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());
    }

    // ==================================================================
    // 已实现的策略：走通完整闭环
    // ==================================================================

    @Test
    @DisplayName("local：上传 → 存在 → 元数据 → 校验和 → 下载 → 删除 → 不存在")
    void implementedStrategy_closesTheLoop() {
        LocalStorageStrategy strategy = new LocalStorageStrategy(configManager);
        String content = "hello-contract";

        // 1. 上传
        String objectName = strategy.uploadFile(flux(content), "a.png", content.length()).block();
        assertNotNull(objectName, "上传必须返回存储路径");

        // 2. 存在
        assertTrue(metadata(strategy).fileExists(objectName).block(),
                "刚上传的对象必须报告存在");

        // 3. 元数据
        Map<String, Object> metadata = metadata(strategy).getMetadata(objectName).block();
        assertNotNull(metadata);

        // 4. 校验和：必须确定，且同一内容两次一致
        String checksum1 = checksum(strategy).calculateFileChecksum(objectName).block();
        String checksum2 = checksum(strategy).calculateFileChecksum(objectName).block();
        assertNotNull(checksum1);
        assertFalse(checksum1.isEmpty(), "能算出校验和的策略不该返回空串");
        assertEquals(checksum1, checksum2, "同一文件两次校验和必须一致（否则去重失效）");

        // 5. 下载：内容必须与上传的一致
        String downloaded = read(strategy, objectName);
        assertEquals(content, downloaded, "下载到的内容必须与上传的一致");

        // 6. 删除
        assertTrue(strategy.deleteFile(objectName).block(), "删除已存在的对象必须报告成功");

        // 7. 删除后不存在
        assertFalse(metadata(strategy).fileExists(objectName).block(),
                "删除之后必须报告不存在 —— 否则「删了个空」会被当成成功");
    }

    // ==================================================================
    // 未实现的策略：每一步都要明确报错
    // ==================================================================

    @Test
    @DisplayName("aliyun：未实现，所有核心操作都明确报错，且不自称已实现")
    void aliyunStrategyFailsLoudly() {
        assertUnimplemented(new AliyunOssStorageStrategy(configManager));
    }

    @Test
    @DisplayName("tencent：未实现，所有核心操作都明确报错，且不自称已实现")
    void tencentStrategyFailsLoudly() {
        assertUnimplemented(new TencentCosStorageStrategy(configManager));
    }

    private void assertUnimplemented(FileStorageStrategy strategy) {
        String key = strategy.getStrategyKey();

        assertFalse(strategy.isImplemented(),
                key + " 没实现却自称已实现 —— 后台会把它列成可选策略，选中后上传「成功」而文件不存在");

        assertThrows(RuntimeException.class,
                () -> strategy.uploadFile(flux("x"), "a.png", 1).block(),
                key + " 的上传必须报错，不能返回一个假路径");

        assertThrows(RuntimeException.class,
                () -> strategy.downloadFile("a/b.png").block(),
                key + " 的下载必须报错，不能返回空流");

        assertThrows(RuntimeException.class,
                () -> strategy.deleteFile("a/b.png").block(),
                key + " 的删除必须报错，不能返回 true");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private MetadataCapable metadata(FileStorageStrategy strategy) {
        return (MetadataCapable) strategy;
    }

    private ChecksumCapable checksum(FileStorageStrategy strategy) {
        return (ChecksumCapable) strategy;
    }

    private Flux<DataBuffer> flux(String text) {
        return Flux.just(new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String read(FileStorageStrategy strategy, String objectName) {
        Flux<DataBuffer> content = strategy.downloadFile(objectName).block();
        assertNotNull(content, "下载必须返回内容流");
        List<DataBuffer> buffers = content.collectList().block();
        assertNotNull(buffers);
        StringBuilder sb = new StringBuilder();
        for (DataBuffer buffer : buffers) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
            DataBufferUtils.release(buffer);
        }
        return sb.toString();
    }
}
