package com.ryu.blog.strategy.impl;

import com.ryu.blog.entity.StorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地文件存储策略实现类
 *
 * @author ryu 475118582@qq.com
 */
@Service
@Slf4j
public class LocalStorageStrategy extends AbstractFileStorageStrategy {

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Override
    public String getStrategyKey() {
        return "local";
    }

    @Override
    public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
        log.debug("[local] 开始上传文件: fileName={}, size={}", fileName, size);
        
        // 使用日期分组的存储路径
        return buildDateBasedObjectNameAsync(fileName)
            .flatMap(objectName -> {
                String relativePath = extractPath(objectName);
                String uniqueFileName = extractFileName(objectName);
                
                // 确保目录结构存在
                return getBasePathAsync()
                    .flatMap(basePath -> createDirectories(basePath, relativePath)
                        .flatMap(directory -> {
                            Path filePath = Paths.get(directory.toString(), uniqueFileName);
                            
                            log.debug("[local] 准备写入文件: filePath={}", filePath);
                            
                            // 打开文件通道进行异步写入
                            return Mono.fromCallable(() -> 
                                    AsynchronousFileChannel.open(
                                        filePath,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE
                                    )
                                )
                                .flatMap(channel -> 
                                    DataBufferUtils.write(dataBufferFlux, channel, 0)
                                        .doFinally(signalType -> {
                                            try {
                                                channel.close();
                                            } catch (IOException e) {
                                                log.error("[local] 关闭文件通道失败: {}", e.getMessage(), e);
                                            }
                                        })
                                        .then(Mono.just(objectName))
                                )
                                .doOnSuccess(path -> log.info("[local] 文件上传成功: fileName={}, path={}", fileName, path))
                                .doOnError(error -> log.error("[local] 文件上传失败: fileName={}, error={}", fileName, error.getMessage(), error));
                        }));
            });
    }

    @Override
    public Mono<Flux<DataBuffer>> downloadFile(String path) {
        return getBasePathAsync()
            .map(basePath -> Paths.get(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 开始下载文件: path={}, filePath={}", path, filePath);
                
                return Mono.fromCallable(() -> {
                    if (!Files.exists(filePath)) {
                        log.warn("[local] 下载的文件不存在: path={}, filePath={}", path, filePath);
                        return Flux.<DataBuffer>empty();
                    }
                    
                    if (Files.isDirectory(filePath)) {
                        log.warn("[local] 下载路径是一个目录: path={}, filePath={}", path, filePath);
                        return Flux.<DataBuffer>empty();
                    }
                    
                    AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);
                    return DataBufferUtils.readAsynchronousFileChannel(() -> channel, bufferFactory, 8192)
                            .doFinally(signalType -> {
                                try {
                                    channel.close();
                                    log.debug("[local] 文件通道已关闭: path={}", path);
                                } catch (IOException e) {
                                    log.error("[local] 关闭文件通道失败: path={}, error={}", path, e.getMessage(), e);
                                }
                            });
                })
                .doOnSuccess(flux -> log.debug("[local] 文件下载准备就绪: path={}", path))
                .doOnError(error -> log.error("[local] 准备下载文件失败: path={}, error={}", path, error.getMessage(), error));
            });
    }

    @Override
    public Mono<Boolean> deleteFile(String path) {
        return getBasePathAsync()
            .map(basePath -> Paths.get(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 开始删除文件: path={}, filePath={}", path, filePath);
                
                return Mono.fromCallable(() -> {
                    try {
                        boolean result = Files.deleteIfExists(filePath);
                        log.info("[local] 文件删除结果: path={}, filePath={}, result={}", path, filePath, result);
                        return result;
                    } catch (IOException e) {
                        log.error("[local] 删除文件失败: path={}, filePath={}, error={}", path, filePath, e.getMessage(), e);
                        return false;
                    }
                });
            });
    }

    @Override
    public Mono<Boolean> fileExists(String path) {
        return getBasePathAsync()
            .map(basePath -> Paths.get(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 检查文件是否存在: path={}, filePath={}", path, filePath);
                
                return Mono.fromCallable(() -> {
                    boolean exists = Files.exists(filePath);
                    log.debug("[local] 文件存在检查结果: path={}, filePath={}, exists={}", path, filePath, exists);
                    return exists;
                });
            });
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        return getBasePathAsync()
            .map(basePath -> Paths.get(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 获取文件元数据: path={}, filePath={}", path, filePath);
                
                return Mono.fromCallable(() -> {
                    Map<String, Object> metadata = new HashMap<>();
                    try {
                        if (Files.exists(filePath)) {
                            metadata.put("size", Files.size(filePath));
                            metadata.put("lastModified", Files.getLastModifiedTime(filePath).toMillis());
                            metadata.put("creationTime", Files.getAttribute(filePath, "creationTime"));
                            metadata.put("isDirectory", Files.isDirectory(filePath));
                            metadata.put("isRegularFile", Files.isRegularFile(filePath));
                            metadata.put("contentType", getContentType(path));
                            metadata.put("fileName", extractFileName(path));
                            log.debug("[local] 文件元数据获取成功: path={}, metadata={}", path, metadata);
                        } else {
                            log.warn("[local] 获取元数据的文件不存在: path={}", path);
                        }
                        return metadata;
                    } catch (IOException e) {
                        log.error("[local] 获取文件元数据失败: path={}, error={}", path, e.getMessage(), e);
                        return metadata;
                    }
                });
            });
    }

    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        log.debug("[local] 生成文件预览URL: path={}, expireSeconds={}", path, expireSeconds);
        
        // 本地存储实现简单返回访问路径，实际项目中可能需要加上域名前缀
        return buildAccessUrlAsync(path)
                .doOnSuccess(generatedUrl -> log.debug("[local] 文件预览URL生成成功: path={}, url={}", path, generatedUrl));
    }

    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        log.debug("[local] 生成文件下载URL: path={}, expireSeconds={}", path, expireSeconds);
        
        // 本地存储实现简单返回访问路径，实际项目中可能需要加上域名前缀和下载参数
        return buildAccessUrlAsync(path)
                .map(url -> url + "?download=true")
                .doOnSuccess(generatedUrl -> log.debug("[local] 文件下载URL生成成功: path={}, url={}", path, generatedUrl));
    }

    @Override
    public Mono<String> calculateFileChecksum(String path) {
        return getBasePathAsync()
            .map(basePath -> Paths.get(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 计算文件校验和: path={}, filePath={}", path, filePath);
                
                return Mono.fromCallable(() -> {
                    try {
                        if (!Files.exists(filePath)) {
                            log.warn("[local] 计算校验和的文件不存在: path={}", path);
                            return "";
                        }
                        byte[] bytes = Files.readAllBytes(filePath);
                        String checksum = com.ryu.blog.utils.FileUtils.calculateMD5(bytes);
                        log.debug("[local] 文件校验和计算成功: path={}, checksum={}", path, checksum);
                        return checksum;
                    } catch (IOException e) {
                        log.error("[local] 计算文件校验和失败: path={}, error={}", path, e.getMessage(), e);
                        return "";
                    }
                });
            });
    }

    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams) {
        log.debug("[local] 初始化分片上传: fileName={}, fileSize={}", fileName, fileSize);
        
        // 生成唯一上传ID
        String uploadId = UUID.randomUUID().toString();
        
        // 使用异步方法构建对象名
        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> {
                // 创建上传上下文
                Map<String, Object> uploadContext = new HashMap<>();
                uploadContext.put("objectName", objectName);
                uploadContext.put("fileName", fileName);
                uploadContext.put("fileSize", fileSize);
                uploadContext.put("parts", new ConcurrentHashMap<Integer, byte[]>());
                uploadContext.put("createdAt", System.currentTimeMillis());
                
                // 确保目录存在
                return getPrefixAsync()
                    .flatMap(relativePath -> 
                        getBasePathAsync()
                            .flatMap(basePath -> createDirectories(basePath, relativePath)
                                .map(directory -> {
                                    // 存储上传上下文
                                    multipartUploadCache.put(uploadId, uploadContext);
                                    
                                    // 返回上传ID和对象名
                                    Map<String, String> result = new HashMap<>();
                                    result.put("uploadId", uploadId);
                                    result.put("objectName", objectName);
                                    
                                    log.info("[local] 分片上传初始化成功: fileName={}, uploadId={}, objectName={}", 
                                            fileName, uploadId, objectName);
                                    
                                    return result;
                                })
                            )
                    );
            });
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
        log.debug("[local] 上传分片: uploadId={}, partNumber={}", uploadId, partNumber);
        
        Map<String, Object> uploadContext = multipartUploadCache.get(uploadId);
        if (uploadContext == null) {
            log.warn("[local] 无效的上传ID: uploadId={}", uploadId);
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
                
                String partETag = "part-" + partNumber;
                log.info("[local] 分片上传成功: uploadId={}, partNumber={}, size={}", 
                        uploadId, partNumber, bytes.length);
                
                return Mono.just(partETag);
            });
    }

    @Override
    public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
        log.debug("[local] 完成分片上传: uploadId={}, partETags={}", uploadId, partETags);
        
        Map<String, Object> uploadContext = multipartUploadCache.get(uploadId);
        if (uploadContext == null) {
            log.warn("[local] 无效的上传ID: uploadId={}", uploadId);
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }
        
        String objectName = (String) uploadContext.get("objectName");
        
        @SuppressWarnings("unchecked")
        Map<Integer, byte[]> parts = (Map<Integer, byte[]>) uploadContext.get("parts");
        
        // 获取存储路径
        String relativePath = extractPath(objectName);
        String fileName = extractFileName(objectName);
        
        return getBasePathAsync()
            .flatMap(basePath -> createDirectories(basePath, relativePath)
                .flatMap(directory -> Mono.fromCallable(() -> {
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
                        
                        // 写入文件
                        Path filePath = Paths.get(directory.toString(), fileName);
                        Files.write(filePath, completeData);
                        
                        // 清理上传信息
                        multipartUploadCache.remove(uploadId);
                        
                        log.info("[local] 分片上传完成并合并成功: uploadId={}, objectName={}, size={}", 
                                uploadId, objectName, completeData.length);
                        
                        return objectName;
                    } catch (IOException e) {
                        log.error("[local] 合并分片失败: uploadId={}, error={}", uploadId, e.getMessage(), e);
                        throw new RuntimeException("合并分片失败: " + e.getMessage(), e);
                    }
                }))
                .flatMap(path -> buildAccessUrlAsync(path))
            );
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        log.debug("[local] 终止分片上传: uploadId={}", uploadId);
        
        // 清理上传信息
        Map<String, Object> uploadContext = multipartUploadCache.remove(uploadId);
        boolean success = uploadContext != null;
        
        log.info("[local] 终止分片上传结果: uploadId={}, success={}", uploadId, success);
        
        return Mono.just(success);
    }

    /**
     * 创建目录结构
     * @param basePath 基础路径
     * @param relativePath 相对路径
     * @return 完整路径
     */
    private Mono<Path> createDirectories(String basePath, String relativePath) {
        return Mono.fromCallable(() -> {
            Path directoryPath;
            if (relativePath == null || relativePath.isEmpty()) {
                directoryPath = Paths.get(basePath);
            } else {
                directoryPath = Paths.get(basePath, relativePath);
            }
            
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                log.debug("[local] 创建目录: {}", directoryPath);
            }
            
            return directoryPath;
        });
    }

    /**
     * 获取基础路径
     * @return 基础路径的Mono
     */
    private Mono<String> getBasePathAsync() {
        // 从配置获取基础路径
        return getConfigPropertyAsync("basePath", "/tmp/uploads")
            .doOnNext(basePath -> log.debug("[local] 获取基础路径: basePath={}", basePath));
    }

    /**
     * 构建对象存储路径（异步方法）
     * @param fileName 文件名
     * @return 完整的存储路径的Mono
     */
    protected Mono<String> buildObjectNameAsync(String fileName) {
        String uniqueFileName = generateUniqueFileName(fileName);
        return getPrefixAsync()
            .map(prefix -> normalizePath(prefix + "/" + uniqueFileName));
    }
    
    /**
     * 构建基于日期的对象存储路径（异步方法）
     * @param fileName 文件名
     * @return 完整的存储路径的Mono
     */
    protected Mono<String> buildDateBasedObjectNameAsync(String fileName) {
        String uniqueFileName = generateUniqueFileName(fileName);
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        
        return getPrefixAsync()
            .map(prefix -> normalizePath(prefix + "/" + datePath + "/" + uniqueFileName));
    }
    
    /**
     * 构建访问URL（异步方法）
     * @param path 文件路径
     * @return 访问URL的Mono
     */
    protected Mono<String> buildAccessUrlAsync(String path) {
        return getConfigPropertyAsync("accessUrl", "")
            .map(accessUrl -> {
                if (accessUrl.isEmpty()) {
                    return "/files/" + path;
                }
                return accessUrl + "/" + path;
            });
    }
} 