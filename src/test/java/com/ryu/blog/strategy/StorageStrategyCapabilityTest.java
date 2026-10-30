package com.ryu.blog.strategy;

import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import com.ryu.blog.strategy.impl.MinioStorageStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储策略的「核心能力 / 可选能力」分界。
 *
 * <p><b>它守的是什么</b>：{@code FileStorageStrategy} 早先是一个 17 方法的胖子，
 * 把三类语义混在一起 —— 核心 CRUD、可选能力（缩略图 / 签名 URL / 校验和）、
 * 以及「不支持」的三种不同表达（返回空流、返回 false、抛
 * {@code UnsupportedOperationException}）。调用方在<b>编译期</b>看不出某个策略
 * 到底支不支持分片，只能等运行到一半炸掉。
 *
 * <p>拆开之后：核心接口只留 6 个方法；可选能力各自一个接口，
 * 调用方用 {@link StorageCapabilities} 取，拿不到就<b>在调用前</b>抛
 * {@link BusinessException}（而不是 {@code UnsupportedOperationException}）。
 *
 * @author ryu
 */
class StorageStrategyCapabilityTest {

    @Test
    @DisplayName("核心接口只保留 6 个方法：CRUD + 公开 URL + 是否实现")
    void coreInterfaceStaysSmall() {
        List<String> methods = Arrays.stream(FileStorageStrategy.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(
                        "deleteFile",
                        "downloadFile",
                        "getPublicUrl",
                        "getStrategyKey",
                        "isImplemented",
                        "uploadFile"),
                methods,
                "核心接口混进了可选能力 —— 调用方就没法在编译期区分「不支持」和「失败了」");
    }

    @Test
    @DisplayName("本地策略具备校验和与元数据能力")
    void localStrategyDeclaresItsCapabilities() {
        Class<LocalStorageStrategy> local = LocalStorageStrategy.class;

        assertTrue(ChecksumCapable.class.isAssignableFrom(local),
                "本地策略能算校验和，应当实现 ChecksumCapable");
        assertTrue(MetadataCapable.class.isAssignableFrom(local),
                "本地策略能读元数据 / 判存在，应当实现 MetadataCapable");
        assertTrue(MultipartCapable.class.isAssignableFrom(local),
                "本地策略支持分片上传，应当实现 MultipartCapable");
    }

    @Test
    @DisplayName("MinIO 策略具备签名 URL 能力")
    void minioDeclaresPresignCapability() {
        assertTrue(PresignCapable.class.isAssignableFrom(MinioStorageStrategy.class),
                "MinIO 能生成带签名的预览 / 下载 URL，应当实现 PresignCapable");
    }

    @Test
    @DisplayName("能力缺失时在调用前抛 BusinessException，而不是跑到一半抛 UnsupportedOperationException")
    void missingCapabilityIsRejectedBeforeCalling() {
        FileStorageStrategy coreOnly = new CoreOnlyStrategy();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> StorageCapabilities.multipart(coreOnly),
                "只实现核心接口的策略不支持分片，取能力时就该拒绝");

        assertTrue(ex.getMessage().contains("分片") || ex.getMessage().contains("不支持"),
                "错误信息要说清是哪种能力不支持，实际为: " + ex.getMessage());

        // 核心能力对任何策略都成立
        assertInstanceOf(FileStorageStrategy.class, coreOnly);
    }

    /**
     * 只实现核心接口的策略 —— 用来验证「能力缺失」这条路径。
     */
    private static class CoreOnlyStrategy implements FileStorageStrategy {

        @Override
        public String getStrategyKey() {
            return "core-only";
        }

        @Override
        public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
            return Mono.just("path/" + fileName);
        }

        @Override
        public Mono<Flux<DataBuffer>> downloadFile(String path) {
            return Mono.just(Flux.empty());
        }

        @Override
        public Mono<Boolean> deleteFile(String path) {
            return Mono.just(true);
        }

        @Override
        public Mono<String> getPublicUrl(String path) {
            return Mono.just("/files/public/" + path);
        }
    }
}
