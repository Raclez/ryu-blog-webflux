package com.ryu.blog.strategy.impl;

import com.ryu.blog.constant.SystemConstants;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.strategy.ChecksumCapable;
import com.ryu.blog.strategy.MetadataCapable;
import com.ryu.blog.strategy.PresignCapable;
import com.ryu.blog.strategy.StorageConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 本地文件存储策略实现类
 *
 * @author ryu 475118582@qq.com
 */
@Service
@Slf4j
public class LocalStorageStrategy extends AbstractFileStorageStrategy
        implements MetadataCapable, ChecksumCapable, PresignCapable {

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    /**
     * 分片临时目录名，位于 {@code basePath} 之下。上传完成或终止后整个目录删除。
     */
    private static final String MULTIPART_TEMP_DIR = ".multipart";

    /**
     * 合并分片时的缓冲区大小。
     */
    private static final int MERGE_BUFFER_SIZE = 64 * 1024;

    /**
     * 本地存储的根目录。
     *
     * <p>取自 {@code app.upload.base-path}（环境变量 {@code UPLOAD_PATH}）。
     * 早先这里硬编码 {@code /tmp/uploads}，而 {@code application.yml} 里的
     * {@code app.upload.base-path} <b>从来没被任何代码读过</b> ——
     * 两个配置源打架，运维改了 yml 却不生效。
     *
     * <p>DB 里的 {@code basePath} 配置项优先级更高（可按策略单独配），
     * 这个字段只是它的兜底默认值。
     */
    @Value("${app.upload.base-path:/tmp/uploads}")
    private String defaultBasePath;

    public LocalStorageStrategy(StorageConfigManager configManager) {
        super(configManager);
    }

    /**
     * 把阻塞的文件系统调用包装成非阻塞的 Mono。
     * <p>
     * {@link Files} 与 {@link AsynchronousFileChannel#open} 都是阻塞实现，
     * 必须在 boundedElastic 线程池执行，否则会占用 Netty event-loop 线程。
     *
     * @param callable 阻塞操作
     * @return 在 boundedElastic 线程上执行的 Mono
     */
    private static <T> Mono<T> blocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public String getStrategyKey() {
        return "local";
    }

    @Override
    public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
        log.debug("[local] 开始上传文件: fileName={}, size={}", fileName, size);
        
        // 使用日期分组的存储路径
        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> {
                String relativePath = extractPath(objectName);
                String uniqueFileName = extractFileName(objectName);
                
                // 确保目录结构存在
                return getBasePathAsync()
                    .flatMap(basePath -> createDirectories(basePath, relativePath)
                        .flatMap(directory -> {
                            Path filePath = Paths.get(directory.toString(), uniqueFileName);
                            
                            log.debug("[local] 准备写入文件: filePath={}", filePath);
                            
                            // 打开文件通道进行异步写入。
                            // TRUNCATE_EXISTING 是必须的：只 CREATE + WRITE 的话，
                            // 撞名时新内容只盖住文件头部，旧文件多出来的尾部会留下来 ——
                            // 「10 字节的新图 + 旧文件尾巴」就是一具打不开的尸体。
                            return blocking(() ->
                                    AsynchronousFileChannel.open(
                                        filePath,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.TRUNCATE_EXISTING
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
                                // 中途失败（客户端断开、超限中断、存储异常）会在磁盘上留下
                                // 只写了一半、且<b>没有任何引用</b>的文件 —— 落库在最后一步，
                                // 失败就没有记录，谁也找不到它去删。这里当场清掉。
                                .doOnError(error -> deletePartialFile(filePath))
                                .doOnSuccess(path -> log.info("[local] 文件上传成功: fileName={}, path={}", fileName, path))
                                .doOnError(error -> log.error("[local] 文件上传失败: fileName={}, error={}", fileName, error.getMessage(), error));
                        }));
            });
    }

    @Override
    public Mono<Flux<DataBuffer>> downloadFile(String path) {
        return getBasePathAsync()
            .map(basePath -> resolveWithinBasePath(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 开始下载文件: path={}, filePath={}", path, filePath);
                
                return Mono.fromCallable(() -> {
                    // 「文件不存在」必须报错，不能返回空流 ——
                    // 空流会被上层当成「正常结束的响应」，下载接口于是返回 200 + 0 字节，
                    // 调用方看到的是一条成功的空文件，而不是一条 404
                    if (!Files.exists(filePath)) {
                        log.warn("[local] 下载的文件不存在: path={}, filePath={}", path, filePath);
                        throw new ResourceNotFoundException("文件不存在: " + path);
                    }

                    if (Files.isDirectory(filePath)) {
                        log.warn("[local] 下载路径是一个目录: path={}, filePath={}", path, filePath);
                        throw new ResourceNotFoundException("路径是一个目录，不是文件: " + path);
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
                }).subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(flux -> log.debug("[local] 文件下载准备就绪: path={}", path))
                .doOnError(error -> log.error("[local] 准备下载文件失败: path={}, error={}", path, error.getMessage(), error));
            });
    }

    @Override
    public Mono<Boolean> deleteFile(String path) {
        return getBasePathAsync()
            .map(basePath -> resolveWithinBasePath(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 开始删除文件: path={}, filePath={}", path, filePath);
                
                return blocking(() -> {
                    try {
                        boolean result = Files.deleteIfExists(filePath);
                        log.info("[local] 文件删除结果: path={}, filePath={}, result={}", path, filePath, result);
                        return result;
                    } catch (IOException e) {
                        // 删除失败必须向上抛：吞掉之后返回 false 与「文件本来就不存在」无法区分，
                        // 上层会把一次真实的 IO 失败（权限、句柄占用、磁盘只读）当成「没啥可删的」
                        log.error("[local] 删除文件失败: path={}, filePath={}", path, filePath, e);
                        throw new BusinessException("删除文件失败: " + e.getMessage());
                    }
                });
            });
    }

    @Override
    public Mono<Boolean> fileExists(String path) {
        return getBasePathAsync()
            .map(basePath -> resolveWithinBasePath(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 检查文件是否存在: path={}, filePath={}", path, filePath);
                
                return blocking(() -> {
                    boolean exists = Files.exists(filePath);
                    log.debug("[local] 文件存在检查结果: path={}, filePath={}, exists={}", path, filePath, exists);
                    return exists;
                });
            });
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(String path) {
        return getBasePathAsync()
            .map(basePath -> resolveWithinBasePath(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 获取文件元数据: path={}, filePath={}", path, filePath);
                
                return blocking(() -> {
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
            .map(basePath -> resolveWithinBasePath(basePath, path))
            .flatMap(filePath -> {
                log.debug("[local] 计算文件校验和: path={}, filePath={}", path, filePath);
                
                return blocking(() -> {
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

        return buildObjectNameAsync(fileName)
            .flatMap(objectName -> getBasePathAsync()
                .flatMap(basePath -> blocking(() -> {
                    // 分片要落到磁盘上的临时目录，而不是躺在堆里：
                    // {basePath}/.multipart/{uploadId}/
                    // 路径虽由服务端 UUID 拼成，仍然过一遍包含性校验 —— 这是唯一的路径护栏，不留例外
                    Path tempDir = resolveWithinBasePath(basePath, MULTIPART_TEMP_DIR + "/" + uploadId);
                    Files.createDirectories(tempDir);

                    // 上传上下文只留元数据：分片数据已经落盘，不再占堆
                    Map<String, Object> uploadContext = new HashMap<>();
                    uploadContext.put("objectName", objectName);
                    uploadContext.put("fileName", fileName);
                    uploadContext.put("fileSize", fileSize);
                    uploadContext.put("tempDir", tempDir);
                    uploadContext.put("parts", new ConcurrentHashMap<Integer, String>());
                    uploadContext.put("createdAt", System.currentTimeMillis());

                    multipartUploadCache.put(uploadId, uploadContext);

                    Map<String, String> result = new HashMap<>();
                    result.put("uploadId", uploadId);
                    result.put("objectName", objectName);

                    log.info("[local] 分片上传初始化成功: fileName={}, uploadId={}, objectName={}, tempDir={}",
                            fileName, uploadId, objectName, tempDir);

                    return result;
                })));
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
        log.debug("[local] 上传分片: uploadId={}, partNumber={}", uploadId, partNumber);

        Map<String, Object> uploadContext = multipartUploadCache.get(uploadId);
        if (uploadContext == null) {
            log.warn("[local] 无效的上传ID: uploadId={}", uploadId);
            return Mono.error(new RuntimeException("无效的上传ID: " + uploadId));
        }

        Path tempDir = asPath(uploadContext.get("tempDir"));
        if (tempDir == null) {
            log.warn("[local] 上传上下文缺少临时目录: uploadId={}", uploadId);
            return Mono.error(new BusinessException("上传会话已失效，请重新发起分片上传"));
        }

        Path partFile = tempDir.resolve(partNumber + ".part");

        // 直接把流传进文件通道：不再 join 成 byte[]，峰值内存与文件大小无关。
        // TRUNCATE_EXISTING 是必须的 —— 同号重传时若只 CREATE+WRITE，
        // 上一次的尾部字节会留在文件里，合并出来的文件就被污染了。
        return blocking(() -> {
                Files.createDirectories(tempDir);
                return Files.newOutputStream(partFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            })
            .flatMap(out -> DataBufferUtils.write(content, out)
                // 写的是普通 OutputStream —— onNext 就发生在订阅线程上。
                // 不切走的话，分片接口会在 Netty 事件循环线程里做磁盘 IO，
                // 一个慢写会把它上面所有请求一起拖住（CLAUDE.md 的硬性约束）。
                .subscribeOn(Schedulers.boundedElastic())
                .then(blocking(() -> {
                    @SuppressWarnings("unchecked")
                    Map<Integer, String> parts = (Map<Integer, String>) uploadContext.get("parts");
                    String partETag = "part-" + partNumber;
                    parts.put(partNumber, partETag);
                    log.info("[local] 分片上传成功: uploadId={}, partNumber={}, path={}",
                            uploadId, partNumber, partFile);
                    return partETag;
                }))
                .doFinally(signal -> closeQuietly(out, partFile)));
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
        Path tempDir = asPath(uploadContext.get("tempDir"));

        String relativePath = extractPath(objectName);
        String fileName = extractFileName(objectName);

        return getBasePathAsync()
            .flatMap(basePath -> createDirectories(basePath, relativePath)
                .flatMap(directory -> blocking(() -> {
                    // 清理放进 finally，且与合并同一个阻塞调用：
                    // doFinally 是在终止信号**之后**才跑，调用方拿到结果时目录可能还在 ——
                    // 那是个竞态，磁盘上会留下「已经合并完的临时分片」
                    try {
                        return mergeParts(tempDir, directory, fileName);
                    } finally {
                        deleteDirectoryQuietly(tempDir);
                        multipartUploadCache.remove(uploadId);
                    }
                }))
                // 直接返回objectName，不添加前缀
                .thenReturn(objectName));
    }

    /**
     * 按分片号<b>数值</b>顺序把临时分片追加进目标文件。
     *
     * <p>用固定大小的缓冲区流式复制，不把整个文件读进内存 ——
     * 早先的 {@code ByteArrayOutputStream} 版本峰值约 2 倍文件大小。
     *
     * @param tempDir   临时分片目录
     * @param directory 目标目录
     * @param fileName  目标文件名
     * @return 合并后的完整路径
     */
    private Path mergeParts(Path tempDir, Path directory, String fileName) {
        try {
            List<Integer> partNumbers = listPartNumbers(tempDir);
            if (partNumbers.isEmpty()) {
                throw new BusinessException("没有可合并的分片，请先上传分片");
            }

            Path target = directory.resolve(fileName);
            try (OutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(target, StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                    MERGE_BUFFER_SIZE)) {
                for (Integer partNumber : partNumbers) {
                    Files.copy(tempDir.resolve(partNumber + ".part"), out);
                }
            }

            log.info("[local] 分片合并成功: path={}, parts={}", target, partNumbers.size());
            return target;
        } catch (IOException e) {
            log.error("[local] 合并分片失败: tempDir={}", tempDir, e);
            throw new BusinessException("合并分片失败: " + e.getMessage());
        }
    }

    /**
     * 列出临时目录里的分片号，按<b>数值</b>升序 —— 字典序会把 10 排在 2 前面。
     */
    private List<Integer> listPartNumbers(Path tempDir) throws IOException {
        if (tempDir == null || !Files.isDirectory(tempDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(tempDir)) {
            return files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".part"))
                .map(name -> name.substring(0, name.length() - ".part".length()))
                .map(this::parsePartNumber)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        }
    }

    private Integer parsePartNumber(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            log.warn("[local] 忽略无法解析的分片文件名: {}", raw);
            return null;
        }
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId) {
        log.debug("[local] 终止分片上传: uploadId={}", uploadId);

        // 清理上传信息与磁盘上的临时分片
        Map<String, Object> uploadContext = multipartUploadCache.remove(uploadId);
        boolean success = uploadContext != null;

        if (success) {
            deleteDirectoryQuietly(asPath(uploadContext.get("tempDir")));
        }

        log.info("[local] 终止分片上传结果: uploadId={}, success={}", uploadId, success);

        return Mono.just(success);
    }

    /**
     * 删除一个写了一半的文件；失败只记日志。
     *
     * <p>清理失败不该把主流程变成另一个错误，但必须留下带堆栈的日志 ——
     * 否则磁盘会被这些无人引用的半截文件悄悄填满。
     *
     * @param filePath 目标文件
     */
    private void deletePartialFile(Path filePath) {
        try {
            if (Files.deleteIfExists(filePath)) {
                log.warn("[local] 上传中断，已删除半截文件: path={}", filePath);
            }
        } catch (IOException e) {
            log.warn("[local] 删除半截文件失败: path={}", filePath, e);
        }
    }

    /**
     * 删除目录及其中所有文件；失败只记日志。
     *
     * <p>临时分片是「清理」对象 —— 清理失败不该把主流程变成另一个错误，
     * 但它必须留下一条带堆栈的日志，否则磁盘会被悄悄占满。
     *
     * @param dir 目录，可为 {@code null}
     */
    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("[local] 删除临时分片失败: path={}", path, e);
                }
            });
            log.debug("[local] 临时分片目录已清理: {}", dir);
        } catch (IOException e) {
            log.warn("[local] 清理临时分片目录失败: dir={}", dir, e);
        }
    }

    /**
     * 关闭输出流，失败只记日志。
     */
    private void closeQuietly(OutputStream out, Path path) {
        try {
            out.close();
        } catch (IOException e) {
            log.warn("[local] 关闭分片输出流失败: path={}", path, e);
        }
    }

    /**
     * 从上传上下文里取临时目录（序列化/反序列化后可能是字符串）。
     */
    private Path asPath(Object value) {
        if (value instanceof Path path) {
            return path;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Paths.get(text);
        }
        return null;
    }

    /**
     * 创建目录结构
     *
     * <p>{@code Files.createDirectories} 是阻塞调用，必须移出 event-loop；
     * 路径同样要做包含性校验（{@code relativePath} 来自对象名，
     * 而对象名来自客户端提供的文件名）。
     *
     * @param basePath 基础路径
     * @param relativePath 相对路径
     * @return 完整路径
     */
    private Mono<Path> createDirectories(String basePath, String relativePath) {
        return Mono.fromCallable(() -> {
            Path directoryPath = resolveWithinBasePath(basePath, relativePath);

            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                log.debug("[local] 创建目录: {}", directoryPath);
            }

            return directoryPath;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取基础路径
     * @return 基础路径的Mono
     */
    private Mono<String> getBasePathAsync() {
        // 从配置获取基础路径；DB 里没配就用 app.upload.base-path
        return getConfigPropertyAsync("basePath", defaultBasePath)
            .doOnNext(basePath -> log.debug("[local] 获取基础路径: basePath={}", basePath));
    }

    /**
     * 解析「根目录 + 相对路径」，并做<b>包含性校验</b>。
     *
     * <p>为什么要校验：{@code path} 来自数据库，而数据库里的 {@code file_path}
     * 理论上可能含有 {@code ../}（历史数据、手工改库、或者别的入口写进去的）。
     * 直接 {@code Paths.get(basePath, path)} 的话，
     * {@code ../../etc/passwd} 就会落到根目录之外 ——
     * <b>任意文件读取</b>。
     *
     * <p>做法：先归一化（{@code normalize} 会把 {@code ..} 折叠掉），
     * 再要求结果仍然在根目录之下。
     *
     * @param basePath 根目录
     * @param path     相对路径
     * @return 解析后的绝对路径
     * @throws BusinessException 路径逃出根目录时抛出
     */
    private Path resolveWithinBasePath(String basePath, String path) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();

        // 空路径必须直接拒绝：
        // {@code base.resolve("")} 会原样返回 base —— 于是「查一个空路径的文件」
        // 变成了「查根目录这个目录本身」，fileExists 返回 true、getMetadata 给出目录元数据。
        if (path == null || path.isBlank()) {
            log.warn("[local] 空路径已拒绝: basePath={}", basePath);
            throw new BusinessException("非法的文件路径");
        }

        Path target = base.resolve(path).toAbsolutePath().normalize();

        // 两个条件缺一不可：
        //   - startsWith：逐个路径段比较（不是字符串前缀比较），
        //     所以「uploads-evil」不会被当成「uploads」的子目录；
        //   - equals：结果不能正好是根目录本身（path 为 "." 之类）。
        if (!target.startsWith(base) || target.equals(base)) {
            log.warn("[local] 路径逃出根目录，已拒绝: basePath={}, path={}", basePath, path);
            throw new BusinessException("非法的文件路径");
        }
        return target;
    }

    /**
     * 构建访问URL（异步方法）
     *
     * <p>本地存储没有「可直链的公开地址」：文件躺在磁盘目录里，
     * 只能由应用自己吐出来。所以没配 {@code accessUrl} 时兜底到
     * <b>应用内真实存在的公开路由</b> {@code /files/public/{path}}。
     *
     * <p>早先这里兜底到 {@code /files/{path}} —— 那个路由不存在，
     * 而且撞在 {@code /files/{id}} 这套按 ID 的 API 命名空间上。
     *
     * @param path 文件路径
     * @return 访问URL的Mono
     */
    protected Mono<String> buildAccessUrlAsync(String path) {
        String normalizedPath = normalizePath(path);
        return getConfigPropertyAsync("accessUrl", "")
            .map(accessUrl -> {
                if (accessUrl == null || accessUrl.isEmpty()) {
                    return SystemConstants.PUBLIC_FILE_ROUTE_PREFIX + normalizedPath;
                }
                return accessUrl.endsWith("/")
                        ? accessUrl + normalizedPath
                        : accessUrl + "/" + normalizedPath;
            });
    }
} 