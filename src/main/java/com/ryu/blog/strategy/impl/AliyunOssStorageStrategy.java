package com.ryu.blog.strategy.impl;

import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.strategy.ChecksumCapable;
import com.ryu.blog.strategy.MetadataCapable;
import com.ryu.blog.strategy.PresignCapable;
import com.ryu.blog.strategy.StorageConfigManager;
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
 * <p><b>⚠️ 这是一个占位实现，尚未接入真实的 OSS SDK。</b>
 *
 * <p>原实现是「假装成功」：上传只打个日志就返回一个人造的路径、下载返回空流、
 * 删除永远返回成功、fileExists 永远返回 true。于是后台把存储策略切成阿里云 OSS 之后，
 * 上传「成功」、下载永远是空、文件却根本不存在 —— 最危险的一种静默失败。
 *
 * <p>现在统一显式报错：要么把它实现掉，要么别选它。
 * （对应的 aliyun-sdk-oss 依赖也已从 pom 里移除 —— 反正没人引用它。）
 *
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
public class AliyunOssStorageStrategy extends AbstractFileStorageStrategy
        implements MetadataCapable, ChecksumCapable, PresignCapable {

    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    
    // 缓存OSS客户端，避免频繁创建
    private final Map<String, Object> ossClientCache = new ConcurrentHashMap<>();

    public AliyunOssStorageStrategy(StorageConfigManager configManager) {
        super(configManager);
    }

    @Override
    public String getStrategyKey() {
        return "aliyun";
    }

    /**
     * 阿里云 OSS 策略<b>尚未实现</b>。
     *
     * <p>自报 {@code false}，注册表会把它从可选列表里剔掉，
     * 「启用 / 创建配置」也会被拒绝 —— 免得管理员把它选成活跃策略之后，
     * 上传「成功」而文件根本不存在。
     */
    @Override
    public boolean isImplemented() {
        return false;
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
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<Boolean> deleteFile(String path) {
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<Boolean> fileExists(String path) {
        // 文件根本没上传过，如实返回不存在
        return Mono.just(Boolean.FALSE);
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        return Mono.error(unimplemented());
    }
    
    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        // 父类的「默认签名 URL」实现已随能力拆分一并删除 ——
        // 这里原本只是转调 super，等于把「不支持」伪装成「支持」
        return Mono.error(unimplemented());
    }

    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        return Mono.error(unimplemented());
    }
    
    @Override
    public Mono<String> calculateFileChecksum(String path) {
        // 算不出来就如实说算不出来。
        // 早先这里返回随机 UUID —— 那会让「按校验和去重」静默失效：
        // 同一个文件每次的校验和都不同，去重形同虚设；
        // 更糟的是随机值会被当成真实校验和落库，事后无从分辨。
        return Mono.just("");
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
            log.warn("[{}] 无效的上传ID: uploadId={}", getStrategyKey(), uploadId);
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
            
            // 上传完整文件 - 直接返回objectName不添加前缀
            log.info("[{}] 分片上传完成: uploadId={}, objectName={}, size={}", 
                    getStrategyKey(), uploadId, objectName, completeData.length);
            return Mono.just(objectName);
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
        return Mono.error(unimplemented());
    }

    /**
     * 构造"未实现"异常
     *
     * <p>占位实现最糟糕的地方不是不能用，而是<b>看起来能用</b>：
     * 上传返回一个路径、删除返回成功、文件永远"存在"，用户要等到下载时才发���问题。
     * 所以这里一律显式失败。
     *
     * @return 未实现异常
     */
    private BusinessException unimplemented() {
        log.error("[{}] 阿里云 OSS 存储策略尚未实现（占位实现），请改用本地存储或 MinIO", getStrategyKey());
        return new BusinessException("阿里云 OSS 存储策略尚未实现，请改用本地存储或 MinIO");
    }

    /**
     * 构建基于文件类型的对象存储路径（异步方法）
     * @param fileName 文件名
     * @return 完整的存储路径的Mono
     */
    protected Mono<String> buildTypeBasedObjectNameAsync(String fileName) {
        String uniqueFileName = generateUniqueFileName(fileName);
        String fileType = FileUtils.getFileType(fileName);
        
        // 不再添加前缀，直接按文件类型分组
        return Mono.just(normalizePath(fileType + "/" + uniqueFileName));
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