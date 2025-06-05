package com.ryu.blog.strategy.impl;

import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.strategy.ConfigurableStorageStrategy;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.utils.FileUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储策略抽象实现
 * 提供通用的配置管理和默认实现
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
public abstract class AbstractFileStorageStrategy implements ConfigurableStorageStrategy, FileStorageStrategy {
    
    // 分片上传缓存
    protected final Map<String, Map<String, Object>> multipartUploadCache = new ConcurrentHashMap<>();
    
    @Autowired
    protected StorageConfigManager configManager;
    
    /**
     * 初始化配置
     * 在子类构造完成后自动调用
     */
    @PostConstruct
    public void init() {
        log.info("[{}] 初始化存储策略配置", getStrategyKey());
        loadConfig()
            .doOnSuccess(v -> log.info("[{}] 存储策略配置加载完成", getStrategyKey()))
            .doOnError(e -> log.error("[{}] 存储策略配置加载失败: {}", getStrategyKey(), e.getMessage(), e))
            .subscribe();
    }
    
    /**
     * 加载配置
     * @return 完成信号
     */
    protected Mono<Void> loadConfig() {
        return configManager.getStrategyConfig(getStrategyKey())
            .flatMap(this::configure)
            .onErrorResume(e -> {
                log.warn("[{}] 加载配置失败，使用默认配置: {}", getStrategyKey(), e.getMessage());
                return Mono.empty();
            });
    }
    
    /**
     * 监听配置变更事件
     * @param event 配置变更事件
     */
    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        if ("storage".equals(event.getConfigType()) && 
            (getStrategyKey().equals(event.getConfigKey()) || "*".equals(event.getConfigKey()))) {
            log.info("[{}] 检测到配置变更，重新加载配置", getStrategyKey());
            loadConfig().subscribe();
        }
    }
    
    @Override
    public Mono<Void> configure(StorageConfig config) {
        return Mono.empty(); // 默认实现为空，子类可以覆盖此方法
    }
    
    /**
     * 获取配置属性
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值的Mono
     */
    @Override
    public Mono<String> getConfigPropertyAsync(String key, String defaultValue) {
        return configManager.getConfigPropertyAsync(getStrategyKey(), key, defaultValue);
    }
    
    /**
     * 获取访问URL
     * @return 访问URL的Mono
     */
    protected Mono<String> getAccessUrlAsync() {
        return configManager.getAccessUrlAsync(getStrategyKey());
    }
    
    /**
     * 生成唯一文件名
     * @param originalFileName 原始文件名
     * @return 唯一文件名
     */
    protected String generateUniqueFileName(String originalFileName) {
        return FileUtils.generateUniqueFileName(originalFileName);
    }
    
    /**
     * 生成随机文件名
     * @param originalFileName 原始文件名
     * @return 随机文件名（保留扩展名）
     */
    protected String generateRandomFileName(String originalFileName) {
        return FileUtils.generateRandomFileName(originalFileName);
    }
    
    /**
     * 构建对象存储路径
     * 采用统一的按内容类型和日期分层的路径结构：
     * {prefix}/{content-type}/{year}/{month}/{day}/{uuid}.{extension}
     * 
     * @param fileName 文件名
     * @return 完整的存储路径的Mono
     */
    protected Mono<String> buildObjectNameAsync(String fileName) {
        // 获取文件类型分组（images, documents, videos等）
        String extension = getFileExtension(fileName);
        String contentTypeGroup = getFileTypeGroup(extension);
        
        // 生成UUID作为文件名（保留原始扩展名）
        String uniqueFileName = generateUniqueFileName(fileName);
        
        // 生成日期路径
        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        
        // 构建基本路径：{content-type}/{year}/{month}/{day}/{uuid}.{extension}
        String basePath = joinPath(contentTypeGroup, year, month, day, uniqueFileName);
        String normalizedBasePath = normalizePath(basePath);
        
        // 获取前缀并添加到路径
        return getConfigPropertyAsync("prefix", "")
            .map(prefix -> {
                if (prefix.isEmpty()) {
                    return normalizedBasePath;
                } else {
                    return normalizePath(joinPath(prefix, normalizedBasePath));
                }
            });
    }
    
    /**
     * 构建按文件类型分组的对象存储路径
     * @param fileName 文件名
     * @return 完整的存储路径（按文件类型分组）的Mono
     */
    protected Mono<String> buildTypeBasedObjectNameAsync(String fileName) {
        String extension = FileUtils.getFileExtension(fileName);
        String fileType = getFileTypeGroup(extension);
        String uniqueFileName = generateUniqueFileName(fileName);
        
        // 按文件类型分组
        return Mono.just(joinPath(fileType, uniqueFileName));
    }
    
    /**
     * 根据文件扩展名获取文件类型分组
     * @param extension 文件扩展名
     * @return 文件类型分组
     */
    protected String getFileTypeGroup(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "other";
        }
        
        extension = extension.toLowerCase();
        
        // 图片类型
        if (extension.matches("jpg|jpeg|png|gif|bmp|webp|svg|ico")) {
            return "images";
        }
        
        // 文档类型
        if (extension.matches("pdf|doc|docx|xls|xlsx|ppt|pptx|txt|csv|md|markdown")) {
            return "documents";
        }
        
        // 视频类型
        if (extension.matches("mp4|avi|mov|wmv|flv|mkv|webm|m4v|3gp")) {
            return "videos";
        }
        
        // 音频类型
        if (extension.matches("mp3|wav|ogg|flac|aac|m4a|wma")) {
            return "audios";
        }
        
        // 压缩文件
        if (extension.matches("zip|rar|7z|tar|gz|bz2")) {
            return "archives";
        }
        
        return "other";
    }
    
    /**
     * 构建访问URL
     * @param objectName 对象名称
     * @return 完整的访问URL的Mono
     */
    protected Mono<String> buildAccessUrlAsync(String objectName) {
        return getAccessUrlAsync()
            .map(accessUrl -> {
                if (!StringUtils.hasText(accessUrl)) {
                    return objectName;
                }
                return accessUrl + (accessUrl.endsWith("/") ? "" : "/") + objectName;
            });
    }
    
    /**
     * 从对象名称中提取文件名
     * @param objectName 对象名称
     * @return 文件名
     */
    protected String extractFileName(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return "";
        }
        
        int lastSlashIndex = objectName.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < objectName.length() - 1) {
            return objectName.substring(lastSlashIndex + 1);
        }
        
        return objectName;
    }
    
    /**
     * 从对象名称中提取路径
     * @param objectName 对象名称
     * @return 路径
     */
    protected String extractPath(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return "";
        }
        
        int lastSlashIndex = objectName.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            return objectName.substring(0, lastSlashIndex);
        }
        
        return "";
    }
    
    @Override
    public Mono<String> uploadFile(FilePart filePart) {
        // 默认实现：将FilePart转换为DataBuffer流，然后调用另一个uploadFile方法
        String fileName = filePart.filename();
        log.debug("[{}] 开始上传文件: fileName={}", getStrategyKey(), fileName);
        
        return Mono.just(filePart)
                .flatMap(part -> {
                    Flux<DataBuffer> content = part.content();
                    // 由于无法直接获取文件大小，这里传入-1
                    return uploadFile(content, fileName, -1);
                })
                .doOnSuccess(resultPath -> log.info("[{}] 文件上传成功: fileName={}, resultPath={}", getStrategyKey(), fileName, resultPath))
                .doOnError(error -> log.error("[{}] 文件上传失败: fileName={}, error={}", getStrategyKey(), fileName, error.getMessage(), error));
    }
    
    @Override
    public Mono<Map<String, Boolean>> batchDeleteFiles(List<String> paths) {
        // 默认实现：逐个删除文件
        log.debug("[{}] 批量删除文件: paths={}", getStrategyKey(), paths);
        Map<String, Boolean> results = new HashMap<>();
        
        return Flux.fromIterable(paths)
                .flatMap(path -> 
                    deleteFile(path)
                        .doOnNext(success -> {
                            results.put(path, success);
                            log.debug("[{}] 删除文件结果: path={}, success={}", getStrategyKey(), path, success);
                        })
                        .onErrorResume(e -> {
                            log.error("[{}] 删除文件失败: path={}, error={}", getStrategyKey(), path, e.getMessage(), e);
                            results.put(path, false);
                            return Mono.just(false);
                        })
                )
                .then(Mono.just(results))
                .doOnSuccess(result -> log.info("[{}] 批量删除文件完成: 总数={}, 成功数={}", 
                        getStrategyKey(), paths.size(), 
                        result.values().stream().filter(Boolean::booleanValue).count()));
    }
    
    @Override
    public Mono<Boolean> fileExists(String path) {
        // 默认实现：不支持检查文件是否存在，子类需要覆盖此方法
        log.warn("[{}] 不支持检查文件是否存在: path={}", getStrategyKey(), path);
        return Mono.just(false);
    }
    
    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        // 默认实现：不支持获取文件元数据，子类需要覆盖此方法
        log.warn("[{}] 不支持获取文件元数据: path={}", getStrategyKey(), path);
        return Mono.just(new HashMap<>());
    }
    
    @Override
    public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
        // 默认实现：直接使用公共URL
        log.debug("[{}] 生成文件预览URL: path={}, expireSeconds={}", getStrategyKey(), path, expireSeconds);
        return getPublicUrl(path);
    }
    
    @Override
    public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
        // 默认实现：使用公共URL，添加下载参数
        log.debug("[{}] 生成文件下载URL: path={}, expireSeconds={}", getStrategyKey(), path, expireSeconds);
        String filename = extractFileName(path);
        
        return getPublicUrl(path)
            .map(url -> {
                // 添加下载参数
                if (url.contains("?")) {
                    url += "&";
                } else {
                    url += "?";
                }
                
                url += "download=true";
                
                if (StringUtils.hasText(filename)) {
                    url += "&filename=" + filename;
                }
                
                return url;
            });
    }
    
    @Override
    public Mono<String> calculateFileChecksum(String path) {
        // 默认实现：不支持计算文件校验和，子类需要覆盖此方法
        log.warn("[{}] 不支持计算文件校验和: path={}", getStrategyKey(), path);
        return Mono.error(new UnsupportedOperationException("不支持计算文件校验和"));
    }
    
    @Override
    public Mono<String> generateThumbnail(String filePath, String thumbnailPath, int width, int height) {
        // 默认实现：不支持生成缩略图
        log.warn("[{}] 不支持生成缩略图: filePath={}, thumbnailPath={}, width={}, height={}", 
                getStrategyKey(), filePath, thumbnailPath, width, height);
        return Mono.error(new UnsupportedOperationException("不支持生成缩略图"));
    }
    
    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams) {
        // 默认实现：创建一个简单的内存缓存，用于模拟分片上传
        String uploadId = UUID.randomUUID().toString();
        
        // 使用异步方法构建对象名
        return buildObjectNameAsync(fileName)
            .map(objectName -> {
                Map<String, Object> uploadContext = new HashMap<>();
                uploadContext.put("objectName", objectName);
                uploadContext.put("fileName", fileName);
                uploadContext.put("fileSize", fileSize);
                uploadContext.put("parts", new ConcurrentHashMap<Integer, String>());
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
        // 默认实现：不支持分片上传，子类需要覆盖此方法
        log.warn("[{}] 不支持分片上传: uploadId={}, partNumber={}", getStrategyKey(), uploadId, partNumber);
        return Mono.error(new UnsupportedOperationException("不支持分片上传"));
    }
    
    @Override
    public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
        // 默认实现：不支持完成分片上传，子类需要覆盖此方法
        log.warn("[{}] 不支持完成分片上传: uploadId={}, partETags={}", getStrategyKey(), uploadId, partETags);
        return Mono.error(new UnsupportedOperationException("不支持完成分片上传"));
    }
    
    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        // 默认实现：清除上传缓存
        Map<String, Object> uploadContext = multipartUploadCache.remove(uploadId);
        boolean success = uploadContext != null;
        
        log.info("[{}] 终止分片上传: uploadId={}, success={}", getStrategyKey(), uploadId, success);
        
        return Mono.just(success);
    }
    
    /**
     * 规范化路径
     * @param path 原始路径
     * @return 规范化后的路径
     */
    protected String normalizePath(String path) {
        return FileUtils.normalizePath(path);
    }
    
    /**
     * 拼接路径
     * @param basePath 基础路径
     * @param paths 子路径
     * @return 完整路径
     */
    protected String joinPath(String basePath, String... paths) {
        return FileUtils.joinPath(basePath, paths);
    }
    
    /**
     * 获取文件扩展名
     * @param fileName 文件名
     * @return 扩展名
     */
    protected String getFileExtension(String fileName) {
        return FileUtils.getFileExtension(fileName);
    }
    
    /**
     * 获取内容类型
     * @param fileName 文件名
     * @return 内容类型
     */
    protected String getContentType(String fileName) {
        return FileUtils.getContentType(fileName);
    }

    /**
     * 获取文件的公共永久URL（不需要身份验证，适用于博客图片等公开内容）
     * 默认实现使用基本的访问URL构建，子类可以根据需要覆盖此方法
     * 
     * @param path 文件路径
     * @return 公共永久URL
     */
    @Override
    public Mono<String> getPublicUrl(String path) {
        return getAccessUrlAsync()
            .map(accessUrl -> {

                // 处理路径和URL拼接
                String normalizedPath = normalizePath(path);
                if (accessUrl.endsWith("/")) {
                    accessUrl = accessUrl.substring(0, accessUrl.length() - 1);
                }
                if (normalizedPath.startsWith("/")) {
                    normalizedPath = normalizedPath.substring(1);
                }
                return accessUrl + "/" + normalizedPath;
            })
            .defaultIfEmpty("/api/files/public/" + path); // 如果没有配置访问URL，使用默认API路径
    }

    /**
     * 获取前缀路径
     * @return 前缀路径的Mono
     */
    protected Mono<String> getPrefixAsync() {
        return getConfigPropertyAsync("prefix", "");
    }

    /**
     * 构建下载URL
     * @param objectName 对象名称
     * @param filename 下载时显示的文件名
     * @param expireSeconds 过期时间（秒）
     * @return 下载URL的Mono
     */
    protected Mono<String> buildDownloadUrlAsync(String objectName, String filename, long expireSeconds) {
        return buildAccessUrlAsync(objectName)
            .map(accessUrl -> {
                // 添加下载参数
                if (accessUrl.contains("?")) {
                    accessUrl += "&";
                } else {
                    accessUrl += "?";
                }
                
                accessUrl += "download=true";
                
                if (StringUtils.hasText(filename)) {
                    accessUrl += "&filename=" + filename;
                }
                
                return accessUrl;
            });
    }

} 