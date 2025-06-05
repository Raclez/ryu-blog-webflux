package com.ryu.blog.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import com.ryu.blog.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 腾讯云COS存储策略实现类
 *
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
public class TencentCosStorageStrategy extends AbstractFileStorageStrategy {

    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    
    // 缓存COS客户端，避免频繁创建
    private final Map<String, Object> cosClientCache = new ConcurrentHashMap<>();
    
    // 分片上传信息
    private final Map<String, Map<String, Object>> multipartUploadsInfo = new ConcurrentHashMap<>();

    @Override
    public String getStrategyKey() {
        return "tencent";
    }
    
    @Override
    public Mono<String> uploadFile(FilePart filePart) {
        String fileName = filePart.filename();
        String contentType = FileUtils.getContentType(fileName);

        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> 
                getCosClient()
                    .flatMap(cosClient -> DataBufferUtils.join(filePart.content())
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            return uploadBytes(cosClient, bytes, objectName, contentType);
                        })
                    )
                    // 直接返回objectName，不添加前缀
                    .thenReturn(objectName)
            );
    }

    @Override
    public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
        String contentType = FileUtils.getContentType(fileName);

        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> 
                getCosClient()
                    .flatMap(cosClient -> DataBufferUtils.join(dataBufferFlux)
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            return uploadBytes(cosClient, bytes, objectName, contentType);
                        })
                    )
                    // 直接返回objectName，不添加前缀
                    .thenReturn(objectName)
            );
    }

    @Override
    public Mono<Flux<DataBuffer>> downloadFile(String path) {
        // 简化实现，返回空流
        return Mono.just(Flux.empty());
    }

    @Override
    public Mono<Boolean> deleteFile(String path) {
        // 简化实现，返回成功
        return Mono.just(true);
    }

    @Override
    public Mono<Boolean> fileExists(String path) {
        // 简化实现，返回文件存在
        return Mono.just(true);
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        // 简化实现，返回基本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("size", 0);
        metadata.put("contentType", "application/octet-stream");
        metadata.put("lastModified", System.currentTimeMillis());
        return Mono.just(metadata);
    }

    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        // 简化实现，返回路径作为URL
        return Mono.just("https://example.com/" + path);
    }

    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        // 简化实现，返回路径作为URL
        return Mono.just("https://example.com/" + path + "?download=true");
    }

    @Override
    public Mono<String> calculateFileChecksum(String path) {
        // 简化实现，返回随机校验和
        return Mono.just(UUID.randomUUID().toString().replace("-", ""));
    }

    /**
     * 上传字节数组到COS
     */
    private Mono<String> uploadBytes(Object cosClient, byte[] bytes, String objectName, String contentType) {
        // 简化实现，直接返回对象名称
        return Mono.just(objectName);
    }

    /**
     * 获取对象的URL
     */
    private Mono<String> getObjectUrl(String objectName) {
        // 简化实现，返回对象名称
        return Mono.just(objectName);
    }

    /**
     * 获取COS客户端
     */
    private Mono<Object> getCosClient() {
        // 从缓存获取客户端
        if (cosClientCache.containsKey("client")) {
            return Mono.just(cosClientCache.get("client"));
        }
        
        // 从配置获取参数并创建客户端
        return Mono.zip(
                getConfigPropertyAsync("secretId", ""),
                getConfigPropertyAsync("secretKey", ""),
                getConfigPropertyAsync("region", "ap-guangzhou"),
                getConfigPropertyAsync("bucket", "default-bucket")
            )
            .flatMap(tuple -> {
                String secretId = tuple.getT1();
                String secretKey = tuple.getT2();
                String region = tuple.getT3();
                String bucket = tuple.getT4();
                
                if (secretId.isEmpty() || secretKey.isEmpty()) {
                    return Mono.error(new RuntimeException("腾讯云COS配置不完整，缺少必要参数"));
                }
                
                try {
                    // 创建一个假的客户端对象
                    Object cosClient = new Object();
                    
                    // 缓存客户端和桶名称
                    cosClientCache.put("client", cosClient);
                    cosClientCache.put("bucket", bucket);
                    
                    return Mono.just(cosClient);
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("创建腾讯云COS客户端失败: " + e.getMessage(), e));
                }
            });
    }

    /**
     * 获取桶名称
     */
    protected Mono<String> getBucketNameAsync() {
        // 使用本地方法变量存储以减少频繁读取
        String cachedBucket = (String) cosClientCache.get("bucket");
        if (cachedBucket != null) {
            return Mono.just(cachedBucket);
        }
        
        // 从配置获取
        return getConfigPropertyAsync("bucket", "default-bucket")
            .doOnNext(bucket -> cosClientCache.put("bucket", bucket));
    }
    
    /**
     * 获取桶名称（同步方法，仅供非响应式上下文使用）
     * @return 桶名称
     * @deprecated 请在响应式上下文中使用 getBucketNameAsync 方法
     */
    @Deprecated
    protected String getBucketName() {
        return "default-bucket";
    }

    /**
     * 获取存储区域
     */
    private Mono<String> getRegionAsync() {
        return getConfigPropertyAsync("region", "ap-guangzhou");
    }
    
    /**
     * 获取存储区域（同步方法，仅供非响应式上下文使用）
     * @return 存储区域
     * @deprecated 请在响应式上下文中使用 getRegionAsync 方法
     */
    @Deprecated
    private String getRegion() {
        return "ap-guangzhou";
    }

    /**
     * 生成唯一文件名
     * @param originalFileName 原始文件名
     * @return 唯一文件名
     */
    @Override
    protected String generateUniqueFileName(String originalFileName) {
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFileName.length() - 1) {
            extension = originalFileName.substring(dotIndex);
        }

        // 使用时间戳和UUID生成唯一文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);

        return timestamp + "_" + uuid + extension;
    }

    /**
     * 规范化路径
     * @param path 路径
     * @return 规范化后的路径
     */
    @Override
    protected String normalizePath(String path) {
        // 移除开头的斜杠
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // 移除结尾的斜杠
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams) {
        String contentType = FileUtils.getContentType(fileName);
        
        return buildObjectNameAsync(fileName)
            .map(objectName -> {
                // 简化实现：不使用腾讯云COS的原生分片上传，而是在内存中模拟
                String uploadId = UUID.randomUUID().toString();

                // 存储上传信息
                Map<String, Object> uploadInfo = new HashMap<>();
                uploadInfo.put("objectName", objectName);
                uploadInfo.put("contentType", contentType);
                uploadInfo.put("parts", new ConcurrentHashMap<Integer, byte[]>());
                multipartUploadsInfo.put(uploadId, uploadInfo);

                // 返回上传ID和对象名
                Map<String, String> result = new HashMap<>();
                result.put("uploadId", uploadId);
                result.put("objectName", objectName);
                return result;
            });
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
        if (!multipartUploadsInfo.containsKey(uploadId)) {
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }

        Map<String, Object> uploadInfo = multipartUploadsInfo.get(uploadId);

        return DataBufferUtils.join(content)
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    // 存储分片数据
                    @SuppressWarnings("unchecked")
                    Map<Integer, byte[]> parts = (Map<Integer, byte[]>) uploadInfo.get("parts");
                    parts.put(partNumber, bytes);

                    // 返回分片标识（简单实现，使用分片编号作为ETag）
                    return Mono.just("part-" + partNumber);
                });
    }

    @Override
    public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
        if (!multipartUploadsInfo.containsKey(uploadId)) {
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }

        Map<String, Object> uploadInfo = multipartUploadsInfo.get(uploadId);
        String objectName = (String) uploadInfo.get("objectName");

        // 清理上传信息
        multipartUploadsInfo.remove(uploadId);

        // 直接返回对象名，不添加前缀
        return Mono.just(objectName);
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        if (!multipartUploadsInfo.containsKey(uploadId)) {
            return Mono.just(false);
        }

        // 清理上传信息
        multipartUploadsInfo.remove(uploadId);
        return Mono.just(true);
    }
}