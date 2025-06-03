package com.ryu.blog.strategy.impl;

import com.ryu.blog.entity.StorageConfig;
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

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 阿里云OSS存储策略实现类
 *
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
public class AliyunOssStorageStrategy extends AbstractFileStorageStrategy {

    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    
    // 缓存OSS客户端，避免频繁创建
    private final Map<String, Object> ossClientCache = new ConcurrentHashMap<>();

    @Override
    public String getStrategyKey() {
        return "aliyun";
    }
    
    @Override
    public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
        // 使用按文件类型分组的存储路径
        return buildTypeBasedObjectNameAsync(fileName)
            .flatMap(objectName -> {
                String contentType = getContentType(fileName);
                
                return DataBufferUtils.join(dataBufferFlux)
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        
                        return uploadBytes(bytes, objectName, contentType);
                    });
            });
    }
    
    @Override
    public Mono<Flux<DataBuffer>> downloadFile(String path) {
        // 简化实现，返回空流
        log.warn("[{}] 下载文件未实现: path={}", getStrategyKey(), path);
        return Mono.just(Flux.empty());
    }
    
    @Override
    public Mono<Boolean> deleteFile(String path) {
        // 简化实现，返回成功
        log.info("[{}] 删除文件: path={}", getStrategyKey(), path);
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
        metadata.put("path", path);
        metadata.put("size", 0L);
        metadata.put("contentType", getContentType(path));
        metadata.put("lastModified", new Date());
        metadata.put("fileName", extractFileName(path));
        return Mono.just(metadata);
    }
    
    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        // 使用父类的实现
        return super.generatePreviewUrl(path, expireSeconds);
    }

    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        // 使用父类的实现
        return super.generateDownloadUrl(path, expireSeconds);
    }
    
    @Override
    public Mono<String> calculateFileChecksum(String path) {
        // 简化实现，返回随机UUID作为校验和
        return Mono.just(UUID.randomUUID().toString());
    }
    
    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams) {
        // 使用按文件类型分组的存储路径
        return buildTypeBasedObjectNameAsync(fileName)
            .map(objectName -> {
                String uploadId = UUID.randomUUID().toString();
                
                Map<String, Object> uploadContext = new HashMap<>();
                uploadContext.put("objectName", objectName);
                uploadContext.put("fileName", fileName);
                uploadContext.put("fileSize", fileSize);
                uploadContext.put("parts", new ConcurrentHashMap<Integer, byte[]>());
                uploadContext.put("createdAt", System.currentTimeMillis());
                
                // 存储上传上下文
                multipartUploadCache.put(uploadId, uploadContext);
                
                Map<String, String> result = new HashMap<>();
                result.put("uploadId", uploadId);
                result.put("objectName", objectName);
                
                log.info("[{}] 初始化分片上传: fileName={}, uploadId={}, objectName={}", 
                        getStrategyKey(), fileName, uploadId, objectName);
                
                return result;
            });
    }
    
    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
        Map<String, Object> uploadContext = multipartUploadCache.get(uploadId);
        if (uploadContext == null) {
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }
        
        return DataBufferUtils.join(content)
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    
                    // 存储分片数据
                    @SuppressWarnings("unchecked")
                    Map<Integer, byte[]> parts = (Map<Integer, byte[]>) uploadContext.get("parts");
                    parts.put(partNumber, bytes);
                    
                    // 返回分片标识（简单实现，使用分片编号作为ETag）
                    return Mono.just("part-" + partNumber);
                });
    }

    @Override
    public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
        Map<String, Object> uploadContext = multipartUploadCache.get(uploadId);
        if (uploadContext == null) {
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }
        
        String objectName = (String) uploadContext.get("objectName");
        
        @SuppressWarnings("unchecked")
        Map<Integer, byte[]> parts = (Map<Integer, byte[]>) uploadContext.get("parts");
        
        // 合并所有分片
        try {
            // 计算总大小
            int totalSize = 0;
            List<Integer> partNumbers = new ArrayList<>(parts.keySet());
            Collections.sort(partNumbers);
            
            for (Integer partNumber : partNumbers) {
                totalSize += parts.get(partNumber).length;
            }
            
            // 合并分片
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(totalSize);
            for (Integer partNumber : partNumbers) {
                outputStream.write(parts.get(partNumber));
            }
            
            byte[] completeData = outputStream.toByteArray();
            
            // 清理上传信息
            multipartUploadCache.remove(uploadId);
            
            // 上传完整文件
            String contentType = getContentType(objectName);
            return uploadBytes(completeData, objectName, contentType);
        } catch (Exception e) {
            log.error("[{}] 合并分片失败: {}", getStrategyKey(), e.getMessage(), e);
            return Mono.error(new RuntimeException("合并分片失败: " + e.getMessage(), e));
        }
    }
    
    /**
     * 获取OSS客户端
     */
    private Mono<Object> getOssClient() {
        // 简化实现，返回一个假的客户端对象
        return Mono.just(new Object());
    }
    
    /**
     * 上传字节数据
     */
    private Mono<String> uploadBytes(byte[] bytes, String objectName, String contentType) {
        // 简化实现，直接返回对象名
        log.info("[{}] 上传文件: objectName={}, contentType={}, size={}",
                getStrategyKey(), objectName, contentType, bytes.length);
        return buildAccessUrlAsync(objectName);
    }

    /**
     * 构建基于文件类型的对象存储路径（异步方法）
     * @param fileName 文件名
     * @return 完整的存储路径的Mono
     */
    protected Mono<String> buildTypeBasedObjectNameAsync(String fileName) {
        String uniqueFileName = generateUniqueFileName(fileName);
        String fileType = FileUtils.getFileType(fileName);
        
        return getPrefixAsync()
            .map(prefix -> normalizePath(prefix + "/" + fileType + "/" + uniqueFileName));
    }
    
    /**
     * 构建访问URL（异步方法）
     * @param pathParam 文件路径
     * @return 访问URL的Mono
     */
    protected Mono<String> buildAccessUrlAsync(String pathParam) {
        return getConfigPropertyAsync("endpoint", "")
            .map(endpoint -> {
                String path = pathParam;
                if (endpoint.isEmpty()) {
                    return path;
                }
                
                if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }
                
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                
                return endpoint + "/" + path;
            });
    }
} 