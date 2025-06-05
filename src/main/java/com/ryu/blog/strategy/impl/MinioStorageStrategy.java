package com.ryu.blog.strategy.impl;

import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.utils.MinioUtils;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * MinIO存储策略实现类
 *
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
public class MinioStorageStrategy extends AbstractFileStorageStrategy {

    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    
    // 缓存MinIO客户端，避免频繁创建
    private final Map<String, Object> minioClientCache = new ConcurrentHashMap<>();
    
    // 分片上传信息
    private final Map<String, Map<String, Object>> multipartUploadsInfo = new ConcurrentHashMap<>();

    @Override
    public String getStrategyKey() {
        return "minio";
    }
    
    @Override
    public Mono<String> uploadFile(FilePart filePart) {
        String fileName = filePart.filename();
        String contentType = FileUtils.getContentType(fileName);
        
        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> 
                DataBufferUtils.join(filePart.content())
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        return getMinioClient()
                            .flatMap(minioClient -> 
                                getBucketName().flatMap(bucketName -> 
                                    MinioUtils.uploadFile(
                                        minioClient,
                                        bucketName,
                                        objectName,
                                        bytes,
                                        contentType
                                    )
                                )
                            )
                            // 直接返回objectName，不添加前缀
                            .thenReturn(objectName);
                    })
            );
    }
    
    @Override
    public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
        String contentType = FileUtils.getContentType(fileName);
        
        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> 
                DataBufferUtils.join(dataBufferFlux)
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        
                        return getMinioClient()
                            .flatMap(minioClient -> 
                                getBucketName().flatMap(bucketName ->
                                    MinioUtils.uploadFile(
                                        minioClient, 
                                        bucketName, 
                                        objectName, 
                                        bytes, 
                                        contentType
                                    )
                                )
                            )
                            // 直接返回objectName，不添加前缀
                            .thenReturn(objectName);
                    })
            );
    }
    
    @Override
    public Mono<Flux<DataBuffer>> downloadFile(String path) {
        // 不需要添加prefix，因为path已经是完整存储路径
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.downloadFile(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path)
                        )
                    )
                )
                .map(bytes -> {
                    DataBuffer buffer = bufferFactory.wrap(bytes);
                    return Flux.just(buffer);
                });
    }
    
    @Override
    public Mono<Boolean> deleteFile(String path) {
        // 不需要添加prefix，因为path已经是完整存储路径
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.deleteFile(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path)
                        )
                    )
                );
    }
    
    @Override
    public Mono<Map<String, Boolean>> batchDeleteFiles(List<String> paths) {
        // 不需要添加prefix，因为paths中的每个路径已经是完整存储路径
        List<String> normalizedPaths = paths.stream()
                .map(FileUtils::normalizePath)
                .collect(Collectors.toList());
                
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.batchDeleteFiles(
                            minioClient, 
                            bucketName, 
                            normalizedPaths
                        )
                    )
                );
    }
    
    @Override
    public Mono<Boolean> fileExists(String path) {
        // 不需要添加prefix，因为path已经是完整存储路径
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.fileExists(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path)
                        )
                    )
                );
    }
    
    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.getMetadata(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path)
                        )
                    )
                );
    }
    
    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.generatePreviewUrl(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path), 
                            expireSeconds
                        )
                    )
                );
    }

    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.generateDownloadUrl(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path), 
                            expireSeconds
                        )
                    )
                );
    }
    
    @Override
    public Mono<String> calculateFileChecksum(String path) {
        return getMinioClient()
                .flatMap(minioClient -> 
                    getBucketName().flatMap(bucketName ->
                        MinioUtils.downloadFile(
                            minioClient, 
                            bucketName, 
                            FileUtils.normalizePath(path)
                        )
                    )
                )
                .map(FileUtils::calculateMD5);
    }
    
    @Override
    public Mono<String> generateThumbnail(String filePath, String thumbnailPath, int width, int height) {
        // 简化实现，不生成缩略图，直接返回原路径
        return Mono.just(filePath);
    }

    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams) {
        String contentType = FileUtils.getContentType(fileName);
        
        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> 
                getMinioClient()
                    .flatMap(minioClient -> 
                        getBucketName().flatMap(bucketName ->
                            MinioUtils.initiateMultipartUpload(
                                minioClient, 
                                bucketName, 
                                objectName, 
                                contentType
                            )
                        )
                    )
                    .map(uploadId -> {
                        // 存储上传信息
                        Map<String, Object> uploadInfo = new HashMap<>();
                        uploadInfo.put("objectName", objectName);
                        uploadInfo.put("contentType", contentType);
                        uploadInfo.put("parts", new ConcurrentHashMap<Integer, String>());
                        multipartUploadsInfo.put(uploadId, uploadInfo);
                        
                        // 返回上传ID和对象名
                        Map<String, String> result = new HashMap<>();
                        result.put("uploadId", uploadId);
                        result.put("objectName", objectName);
                        return result;
                    })
            );
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
        if (!multipartUploadsInfo.containsKey(uploadId)) {
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }
        
        Map<String, Object> uploadInfo = multipartUploadsInfo.get(uploadId);
        String objectName = (String) uploadInfo.get("objectName");
        
        return DataBufferUtils.join(content)
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    
                    return getMinioClient()
                            .flatMap(minioClient -> 
                                getBucketName().flatMap(bucketName ->
                                    MinioUtils.uploadPart(
                                        minioClient, 
                                        bucketName, 
                                        objectName, 
                                        uploadId, 
                                        partNumber, 
                                        bytes
                                    )
                                )
                            )
                            .doOnNext(etag -> {
                                // 存储分片ETag
                                @SuppressWarnings("unchecked")
                                Map<Integer, String> parts = (Map<Integer, String>) uploadInfo.get("parts");
                                parts.put(partNumber, etag);
                            });
                });
    }

    @Override
    public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
        if (!multipartUploadsInfo.containsKey(uploadId)) {
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }
        
        Map<String, Object> uploadInfo = multipartUploadsInfo.get(uploadId);
        String objectName = (String) uploadInfo.get("objectName");
        
        @SuppressWarnings("unchecked")
        Map<Integer, String> parts = (Map<Integer, String>) uploadInfo.get("parts");
        
        return getMinioClient()
            .flatMap(minioClient -> 
                getBucketName().flatMap(bucketName ->
                    MinioUtils.completeMultipartUpload(
                        minioClient, 
                        bucketName, 
                        objectName, 
                        uploadId, 
                        parts
                    )
                )
            )
            .doOnNext(result -> multipartUploadsInfo.remove(uploadId))
            // 直接返回path，不添加前缀
            .thenReturn(objectName);
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        // 不需要添加prefix，因为uploadId已经是完整标识
        Map<String, Object> uploadContext = multipartUploadsInfo.remove(uploadId);
        if (uploadContext == null) {
            log.warn("[minio] 无效的上传ID: {}", uploadId);
            return Mono.just(false);
        }
        
        String objectName = (String) uploadContext.get("objectName");
        String bucketName = (String) uploadContext.get("bucketName");
        
        log.info("[minio] 终止分片上传: uploadId={}, objectName={}, bucketName={}", 
                uploadId, objectName, bucketName);
        
        return getMinioClient()
                .flatMap(minioClient -> 
                    MinioUtils.abortMultipartUpload(
                        minioClient, 
                        bucketName, 
                        objectName, 
                        uploadId
                    )
                );
    }
    
    /**
     * 获取MinIO客户端
     */
    private Mono<MinioClient> getMinioClient() {
        // 从缓存获取客户端
        if (minioClientCache.containsKey("client")) {
            return Mono.just((MinioClient) minioClientCache.get("client"));
        }
        
        // 从配置获取参数并创建客户端
        return Mono.zip(
                getConfigPropertyAsync("endpoint", ""),
                getConfigPropertyAsync("accessKey", ""),
                getConfigPropertyAsync("secretKey", ""),
                getConfigPropertyAsync("useSSL", "false")
            )
            .flatMap(tuple -> {
                String endpoint = tuple.getT1();
                String accessKey = tuple.getT2();
                String secretKey = tuple.getT3();
                boolean secure = Boolean.parseBoolean(tuple.getT4());
                
                if (endpoint.isEmpty() || accessKey.isEmpty() || secretKey.isEmpty()) {
                    return Mono.error(new RuntimeException("MinIO配置不完整，缺少必要参数"));
                }
                
                try {
                    // 创建MinIO客户端
                    MinioClient minioClient = MinioUtils.createMinioClient(endpoint, accessKey, secretKey, secure);
                    
                    // 缓存客户端
                    minioClientCache.put("client", minioClient);
                    
                    return Mono.just(minioClient);
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("创建MinIO客户端失败: " + e.getMessage(), e));
                }
            });
    }
    
    /**
     * 获取存储桶名称
     */
    private Mono<String> getBucketName() {
        // 使用本地方法变量存储以减少频繁读取
        String cachedBucket = (String) minioClientCache.get("bucket");
        if (cachedBucket != null) {
            return Mono.just(cachedBucket);
        }
        
        // 从配置获取
        return getConfigPropertyAsync("bucket", "ryu-blog")
            .doOnNext(bucket -> minioClientCache.put("bucket", bucket));
    }
    
    /**
     * 监听配置变更事件
     * @param event 配置变更事件
     */
    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        // 只处理storage类型的配置变更，并且是当前策略的配置
        if (event.getConfigType().equals("storage") && 
            (event.getConfigKey().equals(getStrategyKey()) || event.getConfigKey().equals("*"))) {
            log.info("检测到MinIO存储策略配置变更，刷新缓存");
            refreshCache();
        }
    }
    
    /**
     * 刷新缓存
     */
    private void refreshCache() {
        minioClientCache.clear();
        log.info("MinIO存储策略配置缓存已刷新");
    }

    /**
     * 构建访问URL（异步方法）
     * @param objectName 对象名称
     * @return 访问URL的Mono
     */
    @Override
    protected Mono<String> buildAccessUrlAsync(String objectName) {
        return getConfigPropertyAsync("endpoint", "")
            .flatMap(endpoint -> getBucketName().map(bucketName -> {
                if (endpoint.isEmpty()) {
                    return objectName;
                }
                
                // 移除结尾的斜杠
                String finalEndpoint = endpoint.endsWith("/") 
                    ? endpoint.substring(0, endpoint.length() - 1) 
                    : endpoint;
                
                // 移除开头的斜杠
                String path = objectName;
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                
                // 构建完整的访问URL：endpoint/bucketName/objectName
                return finalEndpoint + "/" + bucketName + "/" + path;
            }));
    }

    /**
     * 获取文件的公共永久URL
     * 对于MinIO，URL格式为：endpoint/bucketName/objectName
     * 
     * @param path 文件路径
     * @return 公共永久URL
     */
    @Override
    public Mono<String> getPublicUrl(String path) {
        // 直接使用已有的buildAccessUrlAsync方法，它已经实现了endpoint/bucketName/objectName的格式
        return buildAccessUrlAsync(path);
    }
} 