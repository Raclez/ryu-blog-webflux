package com.ryu.blog.strategy;

import com.ryu.blog.strategy.impl.AliyunOssStorageStrategy;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import com.ryu.blog.strategy.impl.TencentCosStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存储策略的失败语义：四个实现对同一个操作给出同一种「失败」表达。
 *
 * <p><b>它守的是什么</b>：同一个方法在不同策略下语义完全不一致 ——
 * {@code deleteFile} 有的返回 {@code true}（文件根本没删），
 * 有的吞掉异常返回 {@code false}，有的抛错；
 * {@code downloadFile} 有的返回空流，有的抛错；
 * {@code calculateFileChecksum} 有的返回随机 UUID。
 * 调用方无从判断「到底成功了没有」，于是「上传成功但文件不存在」这类问题
 * 要等到用户点开才暴露。
 *
 * <p>契约只有一条：<b>要么真做，要么明确报错</b>。禁止假成功、空流、随机值。
 *
 * @author ryu
 */
class StorageStrategyFailureSemanticsTest {

    /** 四个实现里都会「不存在」的对象路径。 */
    private static final String MISSING = "no/such/file.png";

    @TempDir
    Path tmp;

    private List<FileStorageStrategy> strategies;

    @BeforeEach
    void setUp() throws IOException {
        Path base = Files.createDirectories(tmp.resolve("uploads"));

        StorageConfigManager configManager = mock(StorageConfigManager.class);
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenReturn(Mono.just(base.toString()));
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());

        // MinIO 实现依赖真实的 MinIO 客户端（getMinioClient() 会去连对象存储），
        // 在纯单元测试里跑不了；它留待策略契约测试（需要嵌入式/WireMock 的 MinIO）覆盖
        strategies = List.of(
                new LocalStorageStrategy(configManager),
                new TencentCosStorageStrategy(configManager),
                new AliyunOssStorageStrategy(configManager)
        );
    }

    @Test
    @DisplayName("删除不存在的对象：要么报错，要么返回 false —— 绝不能返回 true")
    void deleteMissingObject_neverPretendsSuccess() {
        for (FileStorageStrategy strategy : strategies) {
            Object outcome = settle(strategy.deleteFile(MISSING));

            if (outcome instanceof Boolean deleted && deleted) {
                fail(strategy.getStrategyKey() + " 对不存在的对象返回了 true —— "
                        + "这是假成功：调用方会以为文件已经删掉，实际它从来没存在过，"
                        + "而真正的失败（权限不足、存储不可达）也会被一起吞掉");
            }
            assertTrue(outcome instanceof Throwable || Boolean.FALSE.equals(outcome),
                    strategy.getStrategyKey() + " 给出了契约之外的删除结果: " + outcome);
        }
    }

    @Test
    @DisplayName("下载不存在的对象：要么报错，要么给出非空流 —— 绝不能返回空流")
    void downloadMissingObject_neverReturnsEmptyStream() {
        for (FileStorageStrategy strategy : strategies) {
            Object outcome = settleContent(strategy.downloadFile(MISSING));

            if (outcome instanceof Integer size) {
                fail(strategy.getStrategyKey() + " 对不存在的对象返回了空流（" + size + " 个分片）—— "
                        + "调用方拿到的是一条正常结束的响应，于是「文件不存在」被伪装成「空文件」，"
                        + "下载接口会返回 200 + 0 字节");
            }
            assertTrue(outcome instanceof Throwable,
                    strategy.getStrategyKey() + " 给出了契约之外的下载结果: " + outcome);
        }
    }

    @Test
    @DisplayName("无法计算校验和时不能返回随机值：那会让去重静默失效")
    void checksum_isNeverRandom() {
        for (FileStorageStrategy strategy : strategies) {
            if (!(strategy instanceof ChecksumCapable checksumCapable)) {
                continue;
            }
            String first = checksumCapable.calculateFileChecksum(MISSING).block();
            String second = checksumCapable.calculateFileChecksum(MISSING).block();

            assertNotNull(first, strategy.getStrategyKey() + " 的校验和返回了 null");
            assertEquals(first, second,
                    strategy.getStrategyKey() + " 两次调用返回了不同的校验和 —— "
                            + "这是伪造的随机值：按校验和去重会静默失效（同一文件每次指纹都不同），"
                            + "而且随机值会被当成真实校验和落库，事后无从分辨");
        }
    }

    // ==================================================================
    // 工具：把「同步抛错 / 信号错误 / 正常值」统一成一个结果对象
    // ==================================================================

    private Object settle(Mono<Boolean> mono) {
        try {
            return mono.block();
        } catch (Throwable error) {
            return error;
        }
    }

    /**
     * 把下载结果收敛成「错误」或「分片数」。
     */
    private Object settleContent(Mono<Flux<DataBuffer>> mono) {
        try {
            Flux<DataBuffer> flux = mono.block();
            if (flux == null) {
                return null;
            }
            List<DataBuffer> buffers = flux.collectList().block();
            return buffers == null ? null : buffers.size();
        } catch (Throwable error) {
            return error;
        }
    }
}
