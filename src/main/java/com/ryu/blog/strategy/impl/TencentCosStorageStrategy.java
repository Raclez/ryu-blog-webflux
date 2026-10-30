package com.ryu.blog.strategy.impl;

import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.strategy.ChecksumCapable;
import com.ryu.blog.strategy.MetadataCapable;
import com.ryu.blog.strategy.PresignCapable;
import com.ryu.blog.strategy.StorageConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 腾讯云 COS 存储策略 —— <b>尚未实现</b>。
 *
 * <p>早先这里是一整套「假装成功」的实现：{@code getCosClient()} 返回
 * {@code new Object()}、上传只返回一个人造的路径、{@code fileExists} 恒为
 * {@code true}、删除恒成功、{@code calculateFileChecksum} 返回随机 UUID。
 * 更糟的是它是正常的 Spring Bean，注册表里有它、后台能选中它 ——
 * 管理员把存储策略切成 COS 之后，上传「成功」、文件却根本不存在。
 *
 * <p>现在改成与阿里云一致：<b>要么真做，要么明确报错</b>。
 * 每个方法都返回 {@link Mono#error}，自报 {@code isImplemented() == false}
 * 让注册表把它从可选列表里剔掉、也启用不了。
 *
 * <p>真要接入 COS 时，照着 {@link MinioStorageStrategy} 的结构补上真实实现，
 * 并把 {@code isImplemented()} 改回 {@code true}。
 *
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
public class TencentCosStorageStrategy extends AbstractFileStorageStrategy
        implements MetadataCapable, ChecksumCapable, PresignCapable {

    public TencentCosStorageStrategy(StorageConfigManager configManager) {
        super(configManager);
    }

    @Override
    public String getStrategyKey() {
        return "tencent";
    }

    /**
     * 腾讯云 COS 策略<b>尚未实现</b>。
     */
    @Override
    public boolean isImplemented() {
        return false;
    }

    @Override
    public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<Flux<DataBuffer>> downloadFile(String path) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<Boolean> deleteFile(String path) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<Boolean> fileExists(String path) {
        // 这个策略从来没上传过任何东西，所以「不存在」是事实而不是猜测
        return Mono.just(Boolean.FALSE);
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<String> calculateFileChecksum(String path) {
        // 算不出来就如实说算不出来。
        // 早先这里返回随机 UUID —— 那会让「按校验和去重」静默失效，
        // 而且随机值会被当成真实校验和落库，事后无从分辨。
        return Mono.just("");
    }

    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize,
                                                             Map<String, String> storageParams) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        return Mono.error(unimplemented());
    }

    private BusinessException unimplemented() {
        log.error("[{}] 腾讯云 COS 存储策略尚未实现（占位实现），请改用本地存储或 MinIO", getStrategyKey());
        return new BusinessException("腾讯云 COS 存储策略尚未实现，请改用本地存储或 MinIO");
    }
}
