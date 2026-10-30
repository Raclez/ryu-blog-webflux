package com.ryu.blog.strategy;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 可选能力：分片上传。
 *
 * <p>从 {@link FileStorageStrategy} 拆出来 —— 支持分片和不支持分片是两种策略，
 * 调用方应该在<b>调用前</b>就知道，而不是传到一半发现策略没实现。
 *
 * @author ryu
 */
public interface MultipartCapable {

    /**
     * 初始化分片上传
     *
     * @param fileName      文件名
     * @param fileSize      文件大小
     * @param storageParams 存储参数
     * @return 上传ID和对象名
     */
    Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams);

    /**
     * 上传分片
     *
     * @param uploadId   上传ID
     * @param partNumber 分片序号
     * @param content    分片内容
     * @return 分片ETag
     */
    Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content);

    /**
     * 完成分片上传
     *
     * @param uploadId  上传ID
     * @param partETags 分片ETag列表
     * @return 文件路径
     */
    Mono<String> completeMultipartUpload(String uploadId, List<String> partETags);

    /**
     * 终止分片上传
     *
     * @param uploadId 上传ID
     * @return 是否成功
     */
    Mono<Boolean> abortMultipartUpload(String uploadId);
}
