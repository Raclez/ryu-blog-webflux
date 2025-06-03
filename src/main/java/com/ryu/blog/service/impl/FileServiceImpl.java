package com.ryu.blog.service.impl;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.FilesDTO;
import com.ryu.blog.dto.GroupFileQueryDTO;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.FileService;
import com.ryu.blog.strategy.ConfigurableStorageStrategy;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileUploadVO;
import com.ryu.blog.vo.FileVersionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件服务实现类 - 提供文件上传、下载、管理等功能
 * 
 * 主要功能模块：
 * 1. 文件上传：单文件上传、批量上传、分片上传
 * 2. 文件下载：通过ID下载、通过路径下载、获取文件流
 * 3. 文件管理：删除文件、批量删除、获取文件信息、更新文件信息
 * 4. 文件版本：获取文件版本历史
 * 5. 文件查询：搜索文件、获取分组文件、获取用户文件、按类型获取文件
 * 6. 文件权限：生成预览链接、生成下载链接
 *
 * 实现采用响应式编程模型（Reactor），所有操作都返回Mono或Flux
 * 使用策略模式处理不同的存储方式（本地存储、OSS等）
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    @Value("${app.upload.base-path}")
    private String uploadPath;

    @Value("${app.upload.max-size}")
    private String maxSizeStr;

    @Value("${app.upload.allowed-types}")
    private String allowedTypesStr;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final R2dbcEntityTemplate databaseClient;
    private final StorageStrategyRegistry strategyRegistry;
    private final StorageConfigManager configManager;

    // 分片上传的临时数据存储
    private static final Map<String, MultipartUploadInfo> MULTIPART_UPLOADS = new ConcurrentHashMap<>();
    private static final String UPLOAD_TEMP_DIR = "temp";
    private static final String MULTIPART_UPLOAD_PREFIX = "multipart:";
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int UPLOAD_EXPIRE_HOURS = 24; // 上传过期时间（小时）

    /**
     * 分片上传信息类
     */
    @Data
    @Builder
    @AllArgsConstructor
    private static class MultipartUploadInfo {
        private String fileName;
        private Long fileSize;
        private Long userId;
        private Long groupId;
        private String description;
        private Boolean isPublic;
        private String tempDir;
        @Builder.Default
        private List<String> parts = new ArrayList<>();
        private LocalDateTime createTime;
        
        // 无参构造函数，用于序列化
        public MultipartUploadInfo() {
            this.parts = new ArrayList<>();
        }
    }

    /**
     * 获取允许的文件类型列表
     * @return 允许的文件类型列表
     */
    private List<String> getAllowedTypes() {
        if (allowedTypesStr == null || allowedTypesStr.isEmpty()) {
            // 默认允许的文件类型
            return Arrays.asList(
                // 图片
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
                // 文档
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md",
                // 音视频
                "mp3", "mp4", "wav", "avi", "mov", "flv", "wmv",
                // 压缩文件
                "zip", "rar", "7z", "tar", "gz",
                // 其他常用格式
                "json", "xml", "html", "css", "js"
            );
        }
        return Arrays.asList(allowedTypesStr.split(","));
    }


    @Override
    public Mono<Boolean> isFileAllowed(String filename, long size) {
        log.debug("检查文件是否允许上传: filename={}, size={}", filename, size);
        // 文件名合法性校验
        if (!FileUtils.isValidFileName(filename)) {
            log.warn("文件名不合法: {}", filename);
            return Mono.just(false);
        }
        // 使用工具类验证文件类型和大小
        Map.Entry<Boolean, String> result = FileUtils.isFileAllowed(filename, size, FileUtils.getMaxSize(maxSizeStr), getAllowedTypes());
        if (!result.getKey()) {
            log.warn("文件验证失败: {}, filename={}, size={}", result.getValue(), filename, size);
        }
        return Mono.just(result.getKey());
    }

    @Override
    @Transactional
    public Mono<FileUploadVO> handleFileUpload(FilePart filePart, UploadOptionsDTO options) {
        String fileName = filePart.filename();
        log.info("处理文件上传请求: fileName={}, options={}", fileName, options);
        // 1. 读取文件内容到内存
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    // 2. 获取文件大小和内容
                    long fileSize = dataBuffer.readableByteCount();
                    byte[] fileBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(fileBytes);
                    DataBufferUtils.release(dataBuffer); // 释放资源
                    // 3. 验证文件类型和大小
                    return isFileAllowed(fileName, fileSize)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return Mono.error(new RuntimeException("文件类型或大小不允许"));
                                }
                                // 4. 使用Tika识别MIME类型
                                String mimeType = FileUtils.getMimeType(fileBytes, fileName);
                                log.debug("识别MIME类型: fileName={}, mimeType={}", fileName, mimeType);
                                // 5. 检查MIME类型是否在白名单
                                if (!FileUtils.isAllowedExtension(fileName, getAllowedTypes())) {
                                    return Mono.error(new RuntimeException("文件扩展名不被允许"));
                                }
                                // 6. 文件名安全化
                                String safeFileName = FileUtils.sanitizeFileName(fileName);
                                // 7. 计算文件校验和
                                String checksum = FileUtils.calculateChecksum(fileBytes);
                                log.debug("文件校验和: fileName={}, checksum={}", safeFileName, checksum);
                                // 8. 处理重复文件
                                String duplicateStrategy = options.getDuplicateStrategy();
                                if (duplicateStrategy == null || duplicateStrategy.isEmpty()) {
                                    duplicateStrategy = "reject"; // 默认拒绝上传重复文件
                                }
                                return handleDuplicateFile(safeFileName, fileSize, checksum, duplicateStrategy)
                                        .flatMap(result -> {
                                            // 如果是已存在的文件，直接返回文件信息
                                            if (result instanceof File) {
                                                File existingFile = (File) result;
                                                log.info("检测到重复文件，使用已存在文件: fileId={}, fileName={}",
                                                        existingFile.getId(), existingFile.getFileName());
                                                return buildFileUploadVO(existingFile);
                                            }
                                            // 获取新文件名（可能因为重命名策略而改变）
                                            String newFileName = result.toString();
                                            // 9. 选择存储策略
                                            return strategyRegistry.getActiveStrategy()
                                                    .flatMap(strategy -> {
                                                        DataBuffer newBuffer = new DefaultDataBufferFactory()
                                                                .wrap(ByteBuffer.wrap(fileBytes));
                                                        return strategy.uploadFile(
                                                                Flux.just(newBuffer),
                                                                newFileName,
                                                                fileSize
                                                        )
                                                        .flatMap(filePath -> {
                                                            // 10. 保存文件元数据到数据库
                                                            File file = createFileEntity(
                                                                    newFileName,
                                                                    filePath,
                                                                    fileSize,
                                                                    checksum,
                                                                    options,
                                                                    strategy.getStrategyKey()
                                                            );
                                                            file.setMimeType(mimeType); // 存储MIME类型
                                                            return fileRepository.save(file)
                                                                    .flatMap(this::buildFileUploadVO);
                                                        });
                                                    });
                                        });
                            });
                })
                // 文件内容预览（如图片/文本片段）可在此扩展
                .doOnSuccess(result -> log.info("文件上传成功: fileId={}, fileName={}",
                        result.getFileId(), result.getFileName()))
                .doOnError(error -> log.error("文件上传失败: fileName={}, error={}",
                        fileName, error.getMessage(), error));
    }

    /**
     * 创建文件实体对象
     * 
     * @param fileName 文件名
     * @param filePath 文件路径
     * @param fileSize 文件大小
     * @param checksum 文件校验和
     * @param options 上传选项
     * @param storageType 存储类型
     * @return 文件实体
     */
    private File createFileEntity(String fileName, String filePath, long fileSize, 
                                 String checksum, UploadOptionsDTO options, String storageType) {
                            File file = new File();
        file.setFileName(fileName);
                            file.setFilePath(filePath);
        file.setFileSize(fileSize);
        file.setFileType(FileUtils.getFileExtension(fileName));
        file.setMimeType(FileUtils.getContentType(fileName));
        file.setCreatorId(options.getGroup()); // 使用group作为creatorId
        file.setDescription(options.getDescription());
        file.setAccessType("public".equals(options.getAccess()) ? 0 : 1); // 0-公开, 1-私有
                            file.setStatus(1); // 1-激活
        file.setStorageType(storageType);
        file.setChecksum(checksum);
                            file.setUploadTime(LocalDateTime.now());
                            file.setCreateTime(LocalDateTime.now());
                            file.setUpdateTime(LocalDateTime.now());
                            file.setIsDeleted(0); // 0-未删除
                            
        return file;
    }

    /**
     * 构建文件上传结果VO
     * 
     * @param file 文件实体
     * @return 文件上传结果VO
     */
    private Mono<FileUploadVO> buildFileUploadVO(File file) {
        FileUploadVO vo = FileUploadVO.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .fileUrl(file.getFilePath())
                .fileSize(file.getFileSize())
                .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                .fileType(file.getFileType())
                .uploadTime(file.getUploadTime().toString())
                .build();
        return Mono.just(vo);
    }

    /**
     * 获取存储策略
     * 
     * @param storageType 存储类型
     * @return 存储策略
     */
    private Mono<FileStorageStrategy> getStorageStrategy(String storageType) {
        log.debug("获取存储策略: storageType={}", storageType);
        
        if (storageType != null && !storageType.isEmpty()) {
            // 获取指定类型的策略
            return strategyRegistry.getStrategy(storageType)
                .flatMap(strategy -> {
                    // 如果策略是可配置的，则获取配置并应用
                    if (strategy instanceof ConfigurableStorageStrategy configurableStrategy) {
                        log.debug("配置存储策略: strategyKey={}", strategy.getStrategyKey());
                        return configManager.getStrategyConfig(storageType)
                            .flatMap(configurableStrategy::configure)
                            .thenReturn(strategy)
                            .doOnSuccess(s -> log.debug("存储策略配置成功: strategyKey={}", s.getStrategyKey()));
                    }
                    return Mono.just(strategy);
                })
                .doOnError(error -> log.error("获取存储策略失败: storageType={}, error={}", 
                        storageType, error.getMessage(), error));
        } else {
            // 获取活跃策略
            return strategyRegistry.getActiveStrategy()
                .flatMap(strategy -> {
                    // 如果策略是可配置的，则获取配置并应用
                    if (strategy instanceof ConfigurableStorageStrategy configurableStrategy) {
                        String activeKey = configManager.getActiveStrategyKey();
                        log.debug("配置活跃存储策略: strategyKey={}", activeKey);
                        return configManager.getStrategyConfig(activeKey)
                            .flatMap(configurableStrategy::configure)
                            .thenReturn(strategy)
                            .doOnSuccess(s -> log.debug("活跃存储策略配置成功: strategyKey={}", s.getStrategyKey()));
                    }
                    return Mono.just(strategy);
                })
                .doOnError(error -> log.error("获取活跃存储策略失败: error={}", error.getMessage(), error));
        }
    }

    @Override
    public Flux<FileUploadVO> handleBatchFileUpload(Flux<FilePart> fileParts, UploadOptionsDTO options) {
        log.info("处理批量文件上传请求: options={}", options);
        
        // 使用flatMap对每个文件进行处理，保持并发性
        return fileParts.flatMap(filePart -> {
            log.info("处理批量上传中的文件: fileName={}", filePart.filename());
            return handleFileUpload(filePart, options)
                    .onErrorResume(error -> {
                        // 单个文件上传失败不应该影响整个批量上传过程
                        log.error("批量上传中的文件处理失败: fileName={}, error={}", 
                                filePart.filename(), error.getMessage());
                        return Mono.empty();
                    });
        });
    }

    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, UploadOptionsDTO options) {
        log.info("初始化分片上传: fileName={}, fileSize={}, options={}", fileName, fileSize, options);
        
        // 验证文件类型和大小
        return isFileAllowed(fileName, fileSize)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("文件类型或大小不允许: fileName={}, fileSize={}", fileName, fileSize);
                        return Mono.error(new RuntimeException("文件类型或大小不允许"));
                    }
                    
                    // 选择存储策略
                    return strategyRegistry.getActiveStrategy()
                            .flatMap(strategy -> {
                                // 创建上传ID
                                String uploadId = MULTIPART_UPLOAD_PREFIX + UUID.randomUUID().toString();
                                
                                // 创建元数据
                                Map<String, String> metadata = new HashMap<>();
                                if (options != null) {
                                    if (options.getDescription() != null) {
                                        metadata.put("description", options.getDescription());
                                    }
                                    if (options.getAccess() != null) {
                                        metadata.put("access", options.getAccess());
                                    }
                                    if (options.getGroup() != null) {
                                        metadata.put("group", options.getGroup().toString());
                                    }
                                    if (options.getStorage() != null) {
                                        metadata.put("storage", options.getStorage());
                                    }
                                }
                                
                                // 记录上传信息
                                MultipartUploadInfo uploadInfo = MultipartUploadInfo.builder()
                                        .fileName(fileName)
                                        .fileSize(fileSize)
                                        .userId(options != null ? options.getGroup() : null) // 使用group作为userId
                                        .groupId(options != null ? options.getGroup() : null)
                                        .description(options != null ? options.getDescription() : null)
                                        .isPublic(options != null && "public".equals(options.getAccess()))
                                        .createTime(LocalDateTime.now())
                                        .build();
                                MULTIPART_UPLOADS.put(uploadId, uploadInfo);
                                
                                // 初始化分片上传
                                return strategy.initiateMultipartUpload(fileName, fileSize, metadata)
                                        .map(result -> {
                                            // 合并结果
                                            result.put("uploadId", uploadId);
                                            result.put("chunkSize", String.valueOf(DEFAULT_CHUNK_SIZE));
                                            result.put("chunks", String.valueOf((fileSize + DEFAULT_CHUNK_SIZE - 1) / DEFAULT_CHUNK_SIZE));
                                            log.info("分片上传初始化成功: uploadId={}, fileName={}", uploadId, fileName);
                                            return result;
                                        });
                            });
                })
                .doOnError(error -> log.error("初始化分片上传失败: fileName={}, error={}", fileName, error.getMessage(), error));
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, byte[] partData) {
        log.info("上传分片: uploadId={}, partNumber={}, dataSize={}", uploadId, partNumber, partData.length);
        
        // 检查上传ID是否存在
        MultipartUploadInfo uploadInfo = MULTIPART_UPLOADS.get(uploadId);
        if (uploadInfo == null) {
            // 尝试从Redis恢复上传信息
            String redisKey = MULTIPART_UPLOAD_PREFIX + uploadId;
            return reactiveRedisTemplate.opsForValue().get(redisKey)
                    .cast(MultipartUploadInfo.class)
                    .flatMap(info -> {
                        MULTIPART_UPLOADS.put(uploadId, info);
                        return uploadPartWithStrategy(uploadId, partNumber, partData);
                    })
                    .switchIfEmpty(Mono.error(new RuntimeException("上传ID不存在或已过期")));
        }
        
        return uploadPartWithStrategy(uploadId, partNumber, partData);
    }
    
    private Mono<String> uploadPartWithStrategy(String uploadId, int partNumber, byte[] data) {
        log.debug("上传分片: uploadId={}, partNumber={}, dataSize={}", uploadId, partNumber, data.length);
        
        // 获取上传信息
        MultipartUploadInfo uploadInfo = MULTIPART_UPLOADS.get(uploadId);
        if (uploadInfo == null) {
            log.warn("上传ID不存在: uploadId={}", uploadId);
            return Mono.error(new RuntimeException("无效的上传ID"));
        }
        
        // 选择存储策略
        return strategyRegistry.getActiveStrategy()
                .flatMap(strategy -> {
                    // 创建数据缓冲区
                    DataBuffer buffer = new DefaultDataBufferFactory().wrap(ByteBuffer.wrap(data));
                    
                    // 上传分片
                    return strategy.uploadPart(uploadId, partNumber, Flux.just(buffer))
                            .doOnNext(etag -> {
                                // 记录分片信息
                                if (uploadInfo.getParts() == null) {
                                    uploadInfo.setParts(new ArrayList<>());
                                }
                                // 确保列表大小足够
                                while (uploadInfo.getParts().size() < partNumber) {
                                    uploadInfo.getParts().add(null);
                                }
                                // 设置ETag
                                if (uploadInfo.getParts().size() >= partNumber) {
                                    uploadInfo.getParts().set(partNumber - 1, etag);
                                } else {
                                    uploadInfo.getParts().add(etag);
                                }
                                log.debug("分片上传成功: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
                            });
                })
                .doOnError(error -> log.error("分片上传失败: uploadId={}, partNumber={}, error={}", 
                        uploadId, partNumber, error.getMessage(), error));
    }

    @Override
    @Transactional
    public Mono<FileUploadVO> completeMultipartUpload(String uploadId, List<String> partETags, UploadOptionsDTO options) {
        log.info("完成分片上传: uploadId={}, partETags={}", uploadId, partETags);
        
        // 获取上传信息
        MultipartUploadInfo uploadInfo = MULTIPART_UPLOADS.get(uploadId);
        if (uploadInfo == null) {
            log.warn("上传ID不存在: uploadId={}", uploadId);
            return Mono.error(new RuntimeException("无效的上传ID"));
        }
        
        // 合并上传选项
        final UploadOptionsDTO finalOptions = mergeUploadOptions(options, uploadInfo);
        
        // 选择存储策略
        return strategyRegistry.getActiveStrategy()
                .flatMap(strategy -> {
                    // 合并分片
                    return strategy.completeMultipartUpload(uploadId, partETags)
                            .flatMap(filePath -> {
                                // 创建文件记录
                                File file = createFileEntity(
                                        uploadInfo.getFileName(),
                                        filePath,
                                        uploadInfo.getFileSize(),
                                        "", // 分片上传不计算校验和
                                        finalOptions,
                                        strategy.getStrategyKey()
                                );
                                
                                // 保存文件记录
                                return fileRepository.save(file)
                                        .flatMap(this::buildFileUploadVO)
                                        .doOnSuccess(result -> {
                                            // 清理上传信息
                                            MULTIPART_UPLOADS.remove(uploadId);
                                            log.info("分片上传完成: fileId={}, fileName={}", 
                                                    result.getFileId(), result.getFileName());
                                        });
                            });
                })
                .doOnError(error -> log.error("完成分片上传失败: uploadId={}, error={}", 
                        uploadId, error.getMessage(), error));
    }

    /**
     * 合并上传选项
     * @param options 用户提供的选项
     * @param uploadInfo 上传信息
     * @return 合并后的选项
     */
    private UploadOptionsDTO mergeUploadOptions(UploadOptionsDTO options, MultipartUploadInfo uploadInfo) {
        UploadOptionsDTO mergedOptions = new UploadOptionsDTO();
        
        // 如果用户提供了选项，复制这些选项
        if (options != null) {
            mergedOptions.setDescription(options.getDescription());
            mergedOptions.setGroup(options.getGroup());
            mergedOptions.setAccess(options.getAccess());
            mergedOptions.setStorage(options.getStorage());
            mergedOptions.setVersionPolicy(options.getVersionPolicy());
            mergedOptions.setVersionTag(options.getVersionTag());
            mergedOptions.setDuplicateStrategy(options.getDuplicateStrategy());
        }
        
        // 从上传信息中填充缺失的选项
        if (mergedOptions.getDescription() == null && uploadInfo.getDescription() != null) {
            mergedOptions.setDescription(uploadInfo.getDescription());
        }
        if (mergedOptions.getGroup() == null && uploadInfo.getGroupId() != null) {
            mergedOptions.setGroup(uploadInfo.getGroupId());
        }
        if (mergedOptions.getAccess() == null && uploadInfo.getIsPublic() != null) {
            mergedOptions.setAccess(uploadInfo.getIsPublic() ? "public" : "private");
        }
        
        return mergedOptions;
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        log.info("终止分片上传: uploadId={}", uploadId);
        
        // 获取上传信息
        MultipartUploadInfo uploadInfo = MULTIPART_UPLOADS.get(uploadId);
        if (uploadInfo == null) {
            log.warn("上传ID不存在: uploadId={}", uploadId);
            return Mono.just(false);
        }
        
        // 选择存储策略
        return strategyRegistry.getActiveStrategy()
                .flatMap(strategy -> {
                    // 终止分片上传
                    return strategy.abortMultipartUpload(uploadId)
                            .doOnNext(result -> {
                                if (result) {
                                    // 清理上传信息
                                    MULTIPART_UPLOADS.remove(uploadId);
                                    log.info("分片上传已终止: uploadId={}", uploadId);
                                } else {
                                    log.warn("终止分片上传失败: uploadId={}", uploadId);
                                }
                            });
                })
                .doOnError(error -> log.error("终止分片上传失败: uploadId={}, error={}", 
                        uploadId, error.getMessage(), error));
    }

    @Override
    public Mono<ResponseEntity<Resource>> handleFileDownload(Long fileId) {
        log.info("处理文件下载请求: fileId={}", fileId);
        
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 获取文件存储策略
                    return strategyRegistry.getStrategy(file.getStorageType())
                            .flatMap(strategy -> {
                                // 获取文件数据流
                                return strategy.downloadFile(file.getFilePath())
                                        .flatMap(dataBufferFlux -> {
                                            // 创建临时文件
                                            try {
                                                java.io.File tempFile = java.io.File.createTempFile("download_", "_" + file.getFileName());
                                                tempFile.deleteOnExit();
                                                
                                                // 将数据流写入临时文件
                                                return DataBufferUtils.write(dataBufferFlux, tempFile.toPath())
                                                        .then(Mono.fromCallable(() -> {
                                                            // 创建Resource
                                                            Resource resource = new org.springframework.core.io.FileSystemResource(tempFile);
                                                            
                                                            // 设置响应头
                                                            HttpHeaders headers = new HttpHeaders();
                                                            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                                                                    "attachment; filename=\"" + file.getFileName() + "\"");
                                                            headers.add(HttpHeaders.CONTENT_TYPE, 
                                                                    file.getMimeType() != null ? file.getMimeType() : FileUtils.getContentType(file.getFileName()));
                                                            if (file.getFileSize() != null) {
                                                                headers.add(HttpHeaders.CONTENT_LENGTH, file.getFileSize().toString());
                                                            }
                                                            
                                                            // 记录下载日志（异步）
                                                            logFileDownload(file).subscribe();
                                                            
                                                            return ResponseEntity.ok()
                                                                    .headers(headers)
                                                                    .body(resource);
                                                        }));
                                            } catch (IOException e) {
                                                return Mono.error(new RuntimeException("创建临时文件失败", e));
                                            }
                                        });
                            });
                })
                .doOnSuccess(response -> log.info("文件下载成功: fileId={}", fileId))
                .doOnError(error -> log.error("文件下载失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    public Mono<byte[]> handleFileDownload(String path) {
        log.info("通过路径处理文件下载请求: path={}", path);
        
        // 先通过路径查找文件记录
        return findByFilePath(path, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 获取文件存储策略
                    return strategyRegistry.getStrategy(file.getStorageType())
                            .flatMap(strategy -> strategy.downloadFile(file.getFilePath())
                                    .flatMap(dataBufferFlux -> DataBufferUtils.join(dataBufferFlux)
                                            .map(dataBuffer -> {
                                                // 将DataBuffer转换为字节数组
                                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                                dataBuffer.read(bytes);
                                                DataBufferUtils.release(dataBuffer);
                                                
                                                // 记录下载日志（异步）
                                                logFileDownload(file).subscribe();
                                                
                                                return bytes;
                                            })));
                })
                .doOnSuccess(bytes -> log.info("通过路径下载文件成功: path={}, size={}", path, bytes.length))
                .doOnError(error -> log.error("通过路径下载文件失败: path={}, error={}", path, error.getMessage()));
    }

    @Override
    public Mono<InputStream> getFileStream(Long fileId) {
        log.info("获取文件流: fileId={}", fileId);
        
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 获取文件存储策略
                    return strategyRegistry.getStrategy(file.getStorageType())
                            .flatMap(strategy -> strategy.downloadFile(file.getFilePath())
                                    .flatMap(dataBufferFlux -> DataBufferUtils.join(dataBufferFlux)
                                            .map(dataBuffer -> {
                                                // 将DataBuffer转换为字节数组，然后创建InputStream
                                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                                dataBuffer.read(bytes);
                                                DataBufferUtils.release(dataBuffer);
                                                
                                                // 记录下载日志（异步）
                                                logFileDownload(file).subscribe();
                                                
                                                // 明确指定返回类型
                                                InputStream inputStream = new ByteArrayInputStream(bytes);
                                                return inputStream;
                                            })));
                })
                .doOnSuccess(stream -> log.info("获取文件流成功: fileId={}", fileId))
                .doOnError(error -> log.error("获取文件流失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    @Transactional
    public Mono<Void> handleFileDelete(Long fileId) {
        log.info("处理文件删除请求: fileId={}", fileId);
        
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                        .flatMap(file -> {
                    // 获取文件存储策略
                            return strategyRegistry.getStrategy(file.getStorageType())
                                    .flatMap(strategy -> strategy.deleteFile(file.getFilePath())
                                            .flatMap(result -> {
                                                if (result) {
                                                    // 逻辑删除文件记录
                                                    file.setIsDeleted(1);
                                                    file.setUpdateTime(LocalDateTime.now());
                                            return fileRepository.save(file).then();
                                                } else {
                                            return Mono.error(new RuntimeException("删除文件失败"));
                                                }
                                            }));
                        })
                .doOnSuccess(v -> log.info("文件删除成功: fileId={}", fileId))
                .doOnError(error -> log.error("文件删除失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    @Transactional
    public Mono<Map<Long, Boolean>> batchDeleteFiles(List<Long> fileIds) {
        log.info("批量删除文件: fileIds={}", fileIds);
        
        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        Map<Long, Boolean> results = new ConcurrentHashMap<>();
        
        // 对每个文件ID执行删除操作
        return Flux.fromIterable(fileIds)
                .flatMap(fileId -> 
                    handleFileDelete(fileId)
                    .thenReturn(true)
                    .doOnNext(success -> results.put(fileId, true))
                    .onErrorResume(e -> {
                        results.put(fileId, false);
                        return Mono.just(false);
                    })
                )
                .then(Mono.just(results));
    }

    @Override
    public Mono<Map<String, Object>> getGroupFiles(GroupFileQueryDTO groupFileQueryDTO) {
        log.info("获取文件分组: groupFileQueryDTO={}", groupFileQueryDTO);
        
        // 获取分页参数
        int page = groupFileQueryDTO.getCurrentPage() != null ? groupFileQueryDTO.getCurrentPage().intValue() : 1;
        int size = groupFileQueryDTO.getPageSize() != null ? groupFileQueryDTO.getPageSize().intValue() : 20;
        int skip = (page - 1) * size;
        
        // 构建查询条件
        org.springframework.data.relational.core.query.Criteria criteria = org.springframework.data.relational.core.query.Criteria.where("is_deleted").is(0);
        
        // 添加分组ID条件
        if (groupFileQueryDTO.getGroupId() != null) {
            // 假设有分组关联表，这里简化处理，直接使用creatorId代替
            criteria = criteria.and("creator_id").is(groupFileQueryDTO.getGroupId());
        }
        
        // 构建查询
        org.springframework.data.relational.core.query.Query query = org.springframework.data.relational.core.query.Query.query(criteria)
                .limit(size)
                .offset(skip);
        
        // 添加排序
        query = query.sort(org.springframework.data.domain.Sort.by("create_time").descending());
        
        // 执行查询
        return databaseClient.select(File.class)
                .from("t_file")
                .matching(query)
                .all()
                .map(file -> {
                    // 构建文件详情VO
                    return FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                })
                // 获取用户信息
                .flatMap(fileInfoVO -> {
                    if (fileInfoVO.getCreatorId() != null) {
                        return userRepository.findById(fileInfoVO.getCreatorId())
                                .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                .thenReturn(fileInfoVO)
                                .switchIfEmpty(Mono.just(fileInfoVO));
                    } else {
                        return Mono.just(fileInfoVO);
                    }
                })
                // 生成预览和下载链接
                .flatMap(fileInfoVO -> 
                    generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .collectList()
                .zipWith(countGroupFiles(groupFileQueryDTO))
                .map(tuple -> {
                    List<FileInfoVO> files = tuple.getT1();
                    Long total = tuple.getT2();
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("content", files);
                    result.put("page", page);
                    result.put("size", size);
                    result.put("total", total);
                    result.put("totalPages", (total + size - 1) / size);
                    
                    return result;
                })
                .doOnSuccess(result -> log.info("获取分组文件成功: groupId={}, 总数={}", 
                        groupFileQueryDTO.getGroupId(), result.get("total")))
                .doOnError(error -> log.error("获取分组文件失败: groupId={}, error={}", 
                        groupFileQueryDTO.getGroupId(), error.getMessage()));
    }
    
    /**
     * 统计分组文件数量
     * 
     * @param queryDTO 查询参数
     * @return 文件数量
     */
    private Mono<Long> countGroupFiles(GroupFileQueryDTO queryDTO) {
        // 构建查询条件
        org.springframework.data.relational.core.query.Criteria criteria = org.springframework.data.relational.core.query.Criteria.where("is_deleted").is(0);
        
        // 添加分组ID条件
        if (queryDTO.getGroupId() != null) {
            // 假设有分组关联表，这里简化处理，直接使用creatorId代替
            criteria = criteria.and("creator_id").is(queryDTO.getGroupId());
        }
        
        return databaseClient.select(File.class)
                .from("t_file")
                .matching(org.springframework.data.relational.core.query.Query.query(criteria))
                .count();
    }

    @Override
    public Mono<FileInfoVO> getFileInfo(Long fileId) {
        log.info("获取文件详情: fileId={}", fileId);
        
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 构建文件详情VO
                    FileInfoVO fileInfoVO = FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                    
                    // 获取创建者信息
                    Mono<FileInfoVO> userInfoMono;
                    if (file.getCreatorId() != null) {
                        userInfoMono = userRepository.findById(file.getCreatorId())
                                .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                .thenReturn(fileInfoVO)
                                .switchIfEmpty(Mono.just(fileInfoVO));
                    } else {
                        userInfoMono = Mono.just(fileInfoVO);
                    }
                    
                    // 生成预览和下载链接
                    return userInfoMono
                            .flatMap(vo -> generatePreviewUrl(fileId, 3600)
                                    .doOnNext(vo::setPreviewUrl)
                                    .then(generateDownloadUrl(fileId, 3600))
                                    .doOnNext(vo::setDownloadUrl)
                                    .thenReturn(vo));
                })
                .doOnSuccess(fileInfo -> log.info("获取文件详情成功: fileId={}", fileId))
                .doOnError(error -> log.error("获取文件详情失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    @Transactional
    public Mono<FileInfoVO> updateFileInfo(Long fileId, FilesDTO filesDTO) {
        log.info("更新文件信息: fileId={}, filesDTO={}", fileId, filesDTO);
        
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 更新文件信息
                    if (filesDTO.getFileName() != null && !filesDTO.getFileName().isEmpty()) {
                        file.setFileName(filesDTO.getFileName());
                    }
                    if (filesDTO.getDescription() != null) {
                        file.setDescription(filesDTO.getDescription());
                    }
                    if (filesDTO.getStatus() != null) {
                        file.setStatus(filesDTO.getStatus());
                    }
                    
                    file.setUpdateTime(LocalDateTime.now());
                    
                    // 保存更新后的文件信息
                    return fileRepository.save(file)
                            .flatMap(updatedFile -> {
                                // 构建文件详情VO
                                FileInfoVO fileInfoVO = FileInfoVO.builder()
                                        .id(updatedFile.getId())
                                        .fileName(updatedFile.getFileName())
                                        .filePath(updatedFile.getFilePath())
                                        .fileSize(updatedFile.getFileSize())
                                        .formattedSize(FileUtils.formatFileSize(updatedFile.getFileSize()))
                                        .fileType(updatedFile.getFileType())
                                        .mimeType(updatedFile.getMimeType())
                                        .uploadTime(Date.from(updatedFile.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                                        .updateTime(Date.from(updatedFile.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                                        .creatorId(updatedFile.getCreatorId())
                                        .status(updatedFile.getStatus())
                                        .storageType(updatedFile.getStorageType())
                                        .description(updatedFile.getDescription())
                                        .checksum(updatedFile.getChecksum())
                                        .hasThumbnail(updatedFile.getHasThumbnail())
                                        .accessType(updatedFile.getAccessType())
                                        .createTime(updatedFile.getCreateTime())
                                        .build();
                                
                                // 获取创建者信息
                                Mono<FileInfoVO> userInfoMono;
                                if (updatedFile.getCreatorId() != null) {
                                    userInfoMono = userRepository.findById(updatedFile.getCreatorId())
                                            .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                            .thenReturn(fileInfoVO)
                                            .switchIfEmpty(Mono.just(fileInfoVO));
                                } else {
                                    userInfoMono = Mono.just(fileInfoVO);
                                }
                                
                                // 生成预览和下载链接
                                return userInfoMono
                                        .flatMap(vo -> generatePreviewUrl(fileId, 3600)
                                                .doOnNext(vo::setPreviewUrl)
                                                .then(generateDownloadUrl(fileId, 3600))
                                                .doOnNext(vo::setDownloadUrl)
                                                .thenReturn(vo));
                            });
                })
                .doOnSuccess(fileInfo -> log.info("更新文件信息成功: fileId={}", fileId))
                .doOnError(error -> log.error("更新文件信息失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    public Flux<FileVersionVO> getFileVersions(Long fileId) {
        log.info("获取文件版本历史: fileId={}", fileId);
        
        // 先查询文件信息，确保文件存在
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMapMany(file -> {
                    // 查询版本历史（假设有一个FileVersionRepository）
                    // 这里简化处理，实际应该通过版本库查询
                    // TODO: 替换为实际的版本查询逻辑
                    
                    // 返回一个包含当前版本的列表
                    FileVersionVO currentVersion = FileVersionVO.builder()
                            .id(1L) // 版本ID通常不同于文件ID
                            .fileId(fileId)
                            .versionNumber(1)
                            .versionTag("v1")
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .creatorId(file.getCreatorId())
                            .isCurrent(true)
                            .createTime(Date.from(file.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .build();
                    
                    return Flux.just(currentVersion);
                })
                .doOnComplete(() -> log.info("获取文件版本历史完成: fileId={}", fileId))
                .doOnError(error -> log.error("获取文件版本历史失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> searchFiles(FileSearchDTO searchDTO) {
        log.info("搜索文件: searchDTO={}", searchDTO);
        
        int skip = (searchDTO.getCurrent() - 1) * searchDTO.getSize();
        int limit = searchDTO.getSize();
        
        // 构建查询条件
        org.springframework.data.relational.core.query.Query query = org.springframework.data.relational.core.query.Query.query(
                org.springframework.data.relational.core.query.Criteria.where("is_deleted").is(0)
                        .and(buildSearchCriteria(searchDTO))
        );
        
        // 添加排序
        String orderBy = searchDTO.getOrderBy() != null ? searchDTO.getOrderBy() : "upload_time";
        boolean isAscending = "asc".equals(searchDTO.getOrderDirection());
        query = query.sort(isAscending ? 
                org.springframework.data.domain.Sort.by(orderBy).ascending() : 
                org.springframework.data.domain.Sort.by(orderBy).descending());
        
        // 添加分页
        query = query.limit(limit).offset(skip);
        
        // 执行查询
        return databaseClient.select(File.class)
                .from("t_file")
                .matching(query)
                .all()
                .map(file -> {
                    // 构建文件详情VO
                    return FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                })
                // 获取用户信息
                .flatMap(fileInfoVO -> {
                    if (fileInfoVO.getCreatorId() != null) {
                        return userRepository.findById(fileInfoVO.getCreatorId())
                                .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                .thenReturn(fileInfoVO)
                                .switchIfEmpty(Mono.just(fileInfoVO));
                    } else {
                        return Mono.just(fileInfoVO);
                    }
                })
                // 生成预览和下载链接
                .flatMap(fileInfoVO -> 
                    generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .collectList()
                .zipWith(countFiles(searchDTO))
                .map(tuple -> {
                    List<FileInfoVO> files = tuple.getT1();
                    Long total = tuple.getT2();
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("content", files);
                    result.put("page", searchDTO.getCurrent());
                    result.put("size", searchDTO.getSize());
                    result.put("total", total);
                    result.put("totalPages", (total + searchDTO.getSize() - 1) / searchDTO.getSize());
                    
                    return result;
                })
                .doOnSuccess(result -> log.info("搜索文件成功: 总数={}", result.get("total")))
                .doOnError(error -> log.error("搜索文件失败: error={}", error.getMessage()));
    }
    
    /**
     * 构建搜索条件
     * 
     * @param searchDTO 搜索参数
     * @return 查询条件
     */
    private org.springframework.data.relational.core.query.Criteria buildSearchCriteria(FileSearchDTO searchDTO) {
        org.springframework.data.relational.core.query.Criteria criteria = org.springframework.data.relational.core.query.Criteria.empty();
        
        if (searchDTO.getKeyword() != null && !searchDTO.getKeyword().isEmpty()) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("file_name").like("%" + searchDTO.getKeyword() + "%")
                    .or("description").like("%" + searchDTO.getKeyword() + "%"));
        }
        
        if (searchDTO.getFileTypes() != null && !searchDTO.getFileTypes().isEmpty()) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("file_type").in(searchDTO.getFileTypes()));
        }
        
        if (searchDTO.getCreatorId() != null) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("creator_id").is(searchDTO.getCreatorId()));
        }
        
        if (searchDTO.getUploadStartTime() != null) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("upload_time")
                    .greaterThanOrEquals(LocalDateTime.ofInstant(searchDTO.getUploadStartTime().toInstant(), ZoneId.systemDefault())));
        }
        
        if (searchDTO.getUploadEndTime() != null) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("upload_time")
                    .lessThanOrEquals(LocalDateTime.ofInstant(searchDTO.getUploadEndTime().toInstant(), ZoneId.systemDefault())));
        }
        
        if (searchDTO.getMinSize() != null) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("file_size").greaterThanOrEquals(searchDTO.getMinSize()));
        }
        
        if (searchDTO.getMaxSize() != null) {
            criteria = criteria.and(org.springframework.data.relational.core.query.Criteria.where("file_size").lessThanOrEquals(searchDTO.getMaxSize()));
        }
        
        if (searchDTO.getGroupId() != null) {
            // 假设有文件分组关联表，这里需要调整为实际的查询逻辑
            // 这里简化处理
        }
        
        return criteria;
    }
    
    /**
     * 统计符合条件的文件数量
     *
     * @param searchDTO 搜索参数
     * @return 文件数量
     */
    private Mono<Long> countFiles(FileSearchDTO searchDTO) {
        return databaseClient.select(File.class)
                .from("t_file")
                .matching(org.springframework.data.relational.core.query.Query.query(
                        org.springframework.data.relational.core.query.Criteria.where("is_deleted").is(0)
                                .and(buildSearchCriteria(searchDTO))
                ))
                .count();
    }

    @Override
    public Mono<String> generatePreviewUrl(Long fileId, long expireSeconds) {
        log.info("生成文件预览URL: fileId={}, expireSeconds={}", fileId, expireSeconds);
        
        // 获取文件元数据
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 验证预览权限 (如果需要)
                    // 如果文件是私有的，可以在这里添加权限检查
                    
                    // 检查文件类型，确定是否可以预览
                    String fileType = file.getFileType() != null ? file.getFileType().toLowerCase() : "";
                    boolean isPreviewable = isFilePreviewable(fileType);
                    
                    if (!isPreviewable) {
                        // 如果文件不支持预览，返回下载链接
                        return generateDownloadUrl(fileId, expireSeconds);
                    }
                    
                    // 生成预览URL
                    // 1. 生成一个唯一的令牌
                    String token = generateToken(fileId, "preview", expireSeconds);
                    
                    // 2. 将令牌和过期时间存储在Redis中
                    String redisKey = "preview:" + token;
                    Map<String, Object> tokenData = new HashMap<>();
                    tokenData.put("fileId", fileId);
                    tokenData.put("filePath", file.getFilePath());
                    tokenData.put("expireTime", LocalDateTime.now().plusSeconds(expireSeconds));
                    tokenData.put("storageType", file.getStorageType());
                    
                    return reactiveRedisTemplate.opsForValue().set(redisKey, tokenData, Duration.ofSeconds(expireSeconds))
                            .thenReturn("/api/files/preview?token=" + token);
                })
                .doOnSuccess(url -> log.info("生成预览URL成功: fileId={}, url={}", fileId, url))
                .doOnError(error -> log.error("生成预览URL失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    public Mono<String> generateDownloadUrl(Long fileId, long expireSeconds) {
        log.info("生成文件下载URL: fileId={}, expireSeconds={}", fileId, expireSeconds);
        
        // 获取文件元数据
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除")))
                .flatMap(file -> {
                    // 验证下载权限 (如果需要)
                    // 如果文件是私有的，可以在这里添加权限检查
                    
                    // 生成下载URL
                    // 1. 生成一个唯一的令牌
                    String token = generateToken(fileId, "download", expireSeconds);
                    
                    // 2. 将令牌和过期时间存储在Redis中
                    String redisKey = "download:" + token;
                    Map<String, Object> tokenData = new HashMap<>();
                    tokenData.put("fileId", fileId);
                    tokenData.put("fileName", file.getFileName());
                    tokenData.put("filePath", file.getFilePath());
                    tokenData.put("expireTime", LocalDateTime.now().plusSeconds(expireSeconds));
                    tokenData.put("storageType", file.getStorageType());
                    
                    return reactiveRedisTemplate.opsForValue().set(redisKey, tokenData, Duration.ofSeconds(expireSeconds))
                            .thenReturn("/api/files/download?token=" + token);
                })
                .doOnSuccess(url -> log.info("生成下载URL成功: fileId={}, url={}", fileId, url))
                .doOnError(error -> log.error("生成下载URL失败: fileId={}, error={}", fileId, error.getMessage()));
    }

    @Override
    public Mono<Map<Long, FileInfoVO>> getBatchFileInfos(List<Long> fileIds) {
        log.info("批量获取文件详细信息: fileIds={}", fileIds);
        
        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 创建结果Map
        Map<Long, FileInfoVO> result = new ConcurrentHashMap<>();
        
        // 批量查询文件信息
        return fileRepository.findAllById(fileIds)
                .filter(file -> file.getIsDeleted() == 0) // 只处理未删除的文件
                .flatMap(file -> {
                    // 构建文件详情VO
                    FileInfoVO fileInfoVO = FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                    
                    // 添加到结果Map
                    result.put(file.getId(), fileInfoVO);
                    
                    // 如果有创建者ID，获取创建者信息
                    if (file.getCreatorId() != null) {
                        return userRepository.findById(file.getCreatorId())
                                .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                .thenReturn(file.getId());
                    } else {
                        return Mono.just(file.getId());
                    }
                })
                .collectList()
                .flatMap(ids -> {
                    // 批量生成预览和下载链接
                    // 这里简化处理，实际可能需要更高效的批量生成方式
                    List<Mono<Void>> urlGenerationTasks = new ArrayList<>();
                    
                    for (Long fileId : ids) {
                        FileInfoVO fileInfoVO = result.get(fileId);
                        if (fileInfoVO != null) {
                            Mono<Void> task = generatePreviewUrl(fileId, 3600)
                                    .doOnNext(fileInfoVO::setPreviewUrl)
                                    .then(generateDownloadUrl(fileId, 3600))
                                    .doOnNext(fileInfoVO::setDownloadUrl)
                                    .then();
                            urlGenerationTasks.add(task);
                        }
                    }
                    
                    return Flux.merge(urlGenerationTasks).then(Mono.just(result));
                })
                .doOnSuccess(map -> log.info("批量获取文件详情成功: 总数={}", map.size()))
                .doOnError(error -> log.error("批量获取文件详情失败: error={}", error.getMessage()));
    }

    @Override
    public Mono<Map<Long, String>> getBatchFileUrls(List<Long> fileIds) {
        log.info("批量获取文件URL: fileIds={}", fileIds);
        
        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 创建结果Map
        Map<Long, String> result = new ConcurrentHashMap<>();
        
        // 批量生成下载URL
        return Flux.fromIterable(fileIds)
                .flatMap(fileId -> 
                    fileRepository.findByIdAndIsDeleted(fileId, 0)
                        .flatMap(file -> generateDownloadUrl(fileId, 3600)
                                .doOnNext(url -> result.put(fileId, url)))
                        .onErrorResume(e -> {
                            log.warn("获取文件URL失败: fileId={}, error={}", fileId, e.getMessage());
                            return Mono.empty();
                        })
                )
                .then(Mono.just(result))
                .doOnSuccess(map -> log.info("批量获取文件URL成功: 总数={}", map.size()))
                .doOnError(error -> log.error("批量获取文件URL失败: error={}", error.getMessage()));
    }

    @Override
    public Flux<FileInfoVO> getUserFiles(Long userId) {
        log.info("获取用户文件列表: userId={}", userId);
        
        // 查询用户的文件列表
        return fileRepository.findByCreatorIdAndIsDeleted(userId, 0)
                .map(file -> {
                    // 构建文件详情VO
                    FileInfoVO fileInfoVO = FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                    return fileInfoVO;
                })
                // 获取用户信息
                .flatMap(fileInfoVO -> 
                    userRepository.findById(userId)
                        .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                        .thenReturn(fileInfoVO)
                )
                // 生成预览和下载链接
                .flatMap(fileInfoVO -> 
                    generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .doOnComplete(() -> log.info("获取用户文件列表完成: userId={}", userId))
                .doOnError(error -> log.error("获取用户文件列表失败: userId={}, error={}", userId, error.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> getFileList(int page, int size) {
        log.info("分页获取文件列表: page={}, size={}", page, size);
        
        // 计算分页参数
        int offset = (page - 1) * size;
        
        // 查询文件列表
        return fileRepository.findAllByIsDeletedOrderByCreateTimeDesc(0, size, offset)
                .map(file -> {
                    // 构建文件详情VO
                    return FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                })
                // 获取用户信息
                .flatMap(fileInfoVO -> {
                    if (fileInfoVO.getCreatorId() != null) {
                        return userRepository.findById(fileInfoVO.getCreatorId())
                                .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                .thenReturn(fileInfoVO)
                                .switchIfEmpty(Mono.just(fileInfoVO));
                    } else {
                        return Mono.just(fileInfoVO);
                    }
                })
                // 生成预览和下载链接
                .flatMap(fileInfoVO -> 
                    generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .collectList()
                .zipWith(fileRepository.countByIsDeleted(0))
                .map(tuple -> {
                    List<FileInfoVO> files = tuple.getT1();
                    Long total = tuple.getT2();
        
        Map<String, Object> result = new HashMap<>();
                    result.put("content", files);
                    result.put("page", page);
                    result.put("size", size);
                    result.put("total", total);
                    result.put("totalPages", (total + size - 1) / size);
                    
                    return result;
                })
                .doOnSuccess(result -> log.info("分页获取文件列表成功: page={}, size={}, total={}", 
                        page, size, result.get("total")))
                .doOnError(error -> log.error("分页获取文件列表失败: page={}, size={}, error={}", 
                        page, size, error.getMessage()));
    }

    @Override
    public Flux<FileInfoVO> getFilesByType(String type) {
        log.info("根据类型获取文件列表: type={}", type);
        
        // 查询指定类型的文件列表
        return fileRepository.findByFileTypeAndIsDeleted(type, 0)
                .map(file -> {
                    // 构建文件详情VO
                    FileInfoVO fileInfoVO = FileInfoVO.builder()
                            .id(file.getId())
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .fileSize(file.getFileSize())
                            .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                            .fileType(file.getFileType())
                            .mimeType(file.getMimeType())
                            .uploadTime(Date.from(file.getUploadTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .updateTime(Date.from(file.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()))
                            .creatorId(file.getCreatorId())
                            .status(file.getStatus())
                            .storageType(file.getStorageType())
                            .description(file.getDescription())
                            .checksum(file.getChecksum())
                            .hasThumbnail(file.getHasThumbnail())
                            .accessType(file.getAccessType())
                            .createTime(file.getCreateTime())
                            .build();
                    return fileInfoVO;
                })
                // 获取用户信息
                .flatMap(fileInfoVO -> {
                    if (fileInfoVO.getCreatorId() != null) {
                        return userRepository.findById(fileInfoVO.getCreatorId())
                                .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                                .thenReturn(fileInfoVO)
                                .switchIfEmpty(Mono.just(fileInfoVO));
                    } else {
                        return Mono.just(fileInfoVO);
                    }
                })
                // 生成预览和下载链接
                .flatMap(fileInfoVO -> 
                    generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .doOnComplete(() -> log.info("根据类型获取文件列表完成: type={}", type))
                .doOnError(error -> log.error("根据类型获取文件列表失败: type={}, error={}", type, error.getMessage()));
    }

    /**
     * 处理重复文件
     * 
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param checksum 文件校验和
     * @param strategy 处理策略（reject, replace, rename）
     * @return 处理结果，如果是reject则返回错误，如果是replace则返回已存在的文件，如果是rename则返回新文件名
     */
    private Mono<?> handleDuplicateFile(String fileName, Long fileSize, String checksum, String strategy) {
        // 如果没有提供校验和，则不进行重复检查
        if (checksum == null || checksum.isEmpty()) {
            return Mono.just(fileName);
        }
        
        // 查询是否存在相同校验和的文件
        return fileRepository.findFirstByChecksumAndIsDeleted(checksum, 0)
                .flatMap(existingFile -> {
                    switch (strategy) {
                        case "reject":
                            // 拒绝上传
                            return Mono.error(new RuntimeException("文件已存在: " + existingFile.getFileName()));
                        case "replace":
                            // 使用已存在的文件
                            return Mono.just(existingFile);
                        case "rename":
                            // 重命名新文件
                            String baseName = fileName;
        String extension = "";
                            int dotIndex = fileName.lastIndexOf('.');
                            if (dotIndex > 0) {
                                baseName = fileName.substring(0, dotIndex);
                                extension = fileName.substring(dotIndex);
                            }
                            String newFileName = baseName + "_" + System.currentTimeMillis() + extension;
                            return Mono.just(newFileName);
            default:
                            // 默认拒绝上传
                            return Mono.error(new RuntimeException("文件已存在: " + existingFile.getFileName()));
                    }
                })
                .switchIfEmpty(Mono.just(fileName)); // 如果没有找到重复文件，则返回原文件名
    }

    /**
     * 根据文件路径查找文件
     *
     * @param path 文件路径
     * @param isDeleted 是否已删除（0-未删除，1-已删除）
     * @return 文件信息
     */
    private Mono<File> findByFilePath(String path, Integer isDeleted) {
        // 使用databaseClient查询文件
        return databaseClient.select(File.class)
                .from("t_file")
                .matching(org.springframework.data.relational.core.query.Query.query(
                        org.springframework.data.relational.core.query.Criteria.where("file_path").is(path)
                                .and("is_deleted").is(isDeleted)
                ))
                .one()
                .switchIfEmpty(Mono.error(new RuntimeException("文件不存在或已删除: " + path)));
    }
    
    /**
     * 检查文件类型是否支持预览
     * 
     * @param fileType 文件类型
     * @return 是否支持预览
     */
    private boolean isFilePreviewable(String fileType) {
        return FileUtils.isFilePreviewable(fileType);
    }
    
    /**
     * 生成访问令牌
     * 
     * @param fileId 文件ID
     * @param type 令牌类型（preview、download）
     * @param expireSeconds 过期时间（秒）
     * @return 令牌
     */
    private String generateToken(Long fileId, String type, long expireSeconds) {
        // 组合令牌数据
        String data = fileId + ":" + type + ":" + LocalDateTime.now().plusSeconds(expireSeconds) + ":" + UUID.randomUUID();
        
        // 使用工具类生成令牌
        return FileUtils.generateSecureToken(data);
    }

    /**
     * 记录文件下载日志
     *
     * @param file 文件信息
     * @return 完成信号
     */
    private Mono<Void> logFileDownload(File file) {
        // TODO: 实现下载日志记录
        // 这里可以将下载记录保存到数据库中
        return Mono.empty();
    }

    /**
     * 根据文件路径和校验和查找文件
     *
     * @param path 文件路径
     * @param checksum 文件校验和
     * @param isDeleted 是否已删除（0-未删除，1-已删除）
     * @return 文件信息
     */
    private Mono<File> findByFilePathAndChecksum(String path, String checksum, Integer isDeleted) {
        // 使用databaseClient查询文件
        return databaseClient.select(File.class)
                .from("t_file")
                .matching(org.springframework.data.relational.core.query.Query.query(
                        org.springframework.data.relational.core.query.Criteria.where("file_path").is(path)
                                .and("checksum").is(checksum)
                                .and("is_deleted").is(isDeleted)
                ))
                .one();
    }
}