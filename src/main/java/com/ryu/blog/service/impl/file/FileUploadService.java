package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.ConfigurableStorageStrategy;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageCapabilities;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.vo.FileUploadVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传服务
 *
 * <p>承载单文件上传、批量上传、分片上传与重复文件处理。
 * 缓存注解仍留在 {@code FileServiceImpl} 门面上，由门面负责失效，
 * 这里只关心业务逻辑。
 *
 * <p><b>分片会话有三个必须记住的东西</b>，缺一个整条链路就断：
 * <ol>
 *   <li><b>会话 ID</b> —— 应用层的 ID，给客户端用（{@code multipart:<uuid>}）；</li>
 *   <li><b>存储策略自己的 uploadId</b> —— MinIO 的 multipart upload id /
 *       本地策略的缓存键。<b>必须原样回传给策略</b>：
 *       早先把应用层 ID 直接丢给策略，策略在自己的缓存里查不到，
 *       分片上传从头到尾就没通过过；</li>
 *   <li><b>存储策略 key</b> —— 会话创建时用的策略。分片传完可能已经过了很久，
 *       期间管理员可能把策略切走了；用「当前活跃策略」去合并分片，
 *       分片就找不到家了。</li>
 * </ol>
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    @Value("${app.upload.max-size}")
    private String maxSizeStr;

    @Value("${app.upload.allowed-types}")
    private String allowedTypesStr;

    private final FileRepository fileRepository;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final StorageStrategyRegistry strategyRegistry;
    private final StorageConfigManager configManager;

    // 分片会话<b>只存在 Redis 里</b>：早先进程内还有一份 static Map，
    // 多实例部署时 B 节点看不到 A 节点建的会话，分片上传必挂；
    // 而且那份 Map 从不清理，用户中途放弃的会话会永久占着堆。
    private static final String MULTIPART_UPLOAD_PREFIX = "multipart:";
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 分片上传会话的存活时间。
     *
     * <p>这个 Map 原先永不过期也从不清理：用户开了分片上传却中途放弃，
     * 条目就永久留在 JVM 堆里（每个条目还带着分片 ETag 列表）。
     * 现在每次新建会话时顺手清掉超时条目，把常驻规模限制在「最近 24h 的活跃会话」。
     *
     * <p>同一个 TTL 也用于 Redis 里的会话副本 —— 进程重启后还能续传。
     */
    private static final Duration MULTIPART_UPLOAD_TTL = Duration.ofHours(24);

    /**
     * 分片状态的 Redis Hash 后缀。
     *
     * <p><b>为什么状态要拆成独立的 Hash</b>：分片是<b>并发</b>上传的。
     * 早先把整份会话读出来、改完再整体写回（读-改-写），
     * 两个分片同时进行时各读同一份快照，后写的那份会把先写的那份**覆盖掉** ——
     * local 策略还能靠临时目录合并出来，MinIO 那边 ETag 丢了就直接合并失败。
     * 拆成 Hash 之后每个分片只写<b>自己那几个字段</b>（HSET 是原子操作），
     * 互不干扰。
     */
    private static final String PART_STATE_SUFFIX = ":parts";

    /** 分片 ETag 字段前缀。 */
    private static final String FIELD_ETAG = "e:";

    /** 分片字节数字段前缀。 */
    private static final String FIELD_SIZE = "s:";

    /** 文件头样本字段（只在第 1 个分片时写一次）。 */
    private static final String FIELD_HEAD = "h";

    /**
     * 从 Redis Hash 读出来的分片状态。
     *
     * @param etags 分片号 → ETag
     * @param sizes 分片号 → 字节数
     * @param head  文件头样本（可能为空）
     */
    private record PartState(Map<Integer, String> etags, Map<Integer, Long> sizes, byte[] head) {
    }

    /**
     * 批量上传的并发上限。
     *
     * <p>每个在途文件都占着一段堆内存和一条存储侧连接；不设上限时
     * N 个 10MB 的文件会同时躺在堆里，连接池也会被一起打满。
     */
    private static final int BATCH_UPLOAD_CONCURRENCY = 4;

    /**
     * MIME 识别只取流头部的这么多字节（Tika 的 magic number 都在头几百字节内）。
     */
    private static final int HEAD_SAMPLE_SIZE = 8 * 1024;

    /**
     * 分片上传累计字节数的容忍下限。
     *
     * <p>容忍量取 {@code max(声明值 10%, 1MB)}：分片边界不会刚好落在声明值上，
     * 一分不容忍会让最后一片永远传不上来。
     */
    private static final long MULTIPART_MIN_TOLERANCE_BYTES = 1024 * 1024L;

    /**
     * 分片上传信息类
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class MultipartUploadInfo {
        private String fileName;
        private Long fileSize;
        private Long userId;
        private String description;
        private Boolean isPublic;
        private String tempDir;

        /**
         * 会话创建时选定的存储策略 key —— 分片合并阶段必须用它，
         * 而不是「当前的活跃策略」。
         */
        private String strategyKey;

        /**
         * 存储策略自己的 uploadId —— MinIO 的 multipart upload id、
         * 本地策略的缓存键。调用策略的分片方法时必须传它。
         */
        private String strategyUploadId;

        private LocalDateTime createTime;

        // 无参构造函数，用于序列化
        public MultipartUploadInfo() {
        }
    }

    /**
     * 上传流的记账器：边传边算，常量内存。
     *
     * <p>它取代的是「先把整个文件 join 成 {@code byte[]}，再从头算一遍」——
     * 那种写法下 10MB 的文件在堆上要占两三份。这里只保留三样东西：
     * 累计字节数、增量 MD5 摘要、以及流头部的一小段样本（给 Tika 用）。
     *
     * <p>超限在<b>流上</b>拒绝：一旦累计字节数越过上限就抛
     * {@link BusinessException#fileSizeLimit()}。因为抛出发生在
     * {@code Flux.handle} 里，Reactor 会取消上游订阅 —— 不会把剩下的几百 MB 读完再拒绝。
     */
    static final class UploadStreamMeter {

        private final long maxSize;
        private final MessageDigest digest;
        private final ByteArrayOutputStream headSample = new ByteArrayOutputStream(HEAD_SAMPLE_SIZE);
        private long totalBytes;
        private String checksum;

        UploadStreamMeter(long maxSize) {
            this.maxSize = maxSize;
            try {
                this.digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                // 每个 JVM 都带 MD5；拿不到说明运行环境坏了，不该在上传路径上吞掉
                throw new IllegalStateException("当前运行环境不支持 MD5", e);
            }
        }

        /**
         * {@code Flux.handle} 的处理器：记账、校验上限，然后把 buffer 原样放行。
         */
        void inspect(DataBuffer buffer, SynchronousSink<DataBuffer> sink) {
            int readable = buffer.readableByteCount();
            long next = totalBytes + readable;

            if (maxSize > 0 && next > maxSize) {
                log.warn("上传流超过大小上限，取消上游: received={}, max={}", next, maxSize);
                throw BusinessException.fileSizeLimit();
            }

            totalBytes = next;

            // asByteBuffer(index, length) 是只读视图，不推进 buffer 自己的读写位置 ——
            // buffer 还要原样交给存储策略
            ByteBuffer view = buffer.asByteBuffer(buffer.readPosition(), readable);
            digest.update(view.duplicate());

            int room = HEAD_SAMPLE_SIZE - headSample.size();
            if (room > 0) {
                byte[] chunk = new byte[Math.min(room, readable)];
                view.duplicate().get(chunk);
                headSample.write(chunk, 0, chunk.length);
            }

            sink.next(buffer);
        }

        long totalBytes() {
            return totalBytes;
        }

        /**
         * 流头部样本（最多 {@link #HEAD_SAMPLE_SIZE} 字节）。
         */
        byte[] headBytes() {
            return headSample.toByteArray();
        }

        /**
         * 全量 MD5 —— 增量摘要的结果与「读完整个文件再算」一致。
         *
         * <p>摘要只能取一次（{@code digest()} 会重置状态），所以算完就缓存住。
         */
        String checksum() {
            if (checksum == null) {
                byte[] bytes = digest.digest();
                StringBuilder sb = new StringBuilder(bytes.length * 2);
                for (byte b : bytes) {
                    sb.append(String.format("%02x", b));
                }
                checksum = sb.toString();
            }
            return checksum;
        }
    }

    /**
     * 获取允许的文件类型列表
     * @return 允许的文件类型列表
     */
    public List<String> getAllowedTypes() {
        if (allowedTypesStr == null || allowedTypesStr.isEmpty()) {
            // 默认允许的文件类型。
            // 刻意**不包含** html / svg / js / css / xml / json ——
            // 这些是「主动内容」：如果对象存储桶或静态目录与站点同源，
            // 上传一个 .html/.svg 就能在站点域下执行脚本（存储型 XSS）。
            // 需要时可由部署方通过 app.upload.allowed-types 显式放开。
            return Arrays.asList(
                // 图片（不含 svg）
                "jpg", "jpeg", "png", "gif", "bmp", "webp",
                // 文档
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md",
                // 音视频
                "mp3", "mp4", "wav", "avi", "mov", "flv", "wmv",
                // 压缩文件
                "zip", "rar", "7z", "tar", "gz"
            );
        }
        return Arrays.asList(allowedTypesStr.split(","));
    }

    /**
     * 检查文件是否允许上传
     */
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

    /**
     * 处理单文件上传
     *
     * <p><b>流式</b>：文件内容以 {@link DataBuffer} 流的形式<b>直接</b>交给存储策略，
     * 不再先 {@code join} 成一大块内存。早先的写法是
     * {@code join(整个文件) → byte[] → 再包成 DataBuffer → Flux.just(一个)}，
     * 10MB 的文件要在堆上占两三份，批量上传时还没有并发上限 ——
     * N 个文件同时在堆里。
     *
     * <p>三件事改成在流上做：
     * <ul>
     *   <li><b>大小</b>：{@code Content-Length} 预检 + 流上累计字节数，
     *       超限立刻取消上游并拒绝；</li>
     *   <li><b>MIME</b>：扩展名 + 头部若干字节的 magic number，不再依赖全量字节；</li>
     *   <li><b>校验和</b>：增量摘要边传边算，与全量 {@code MD5} 结果一致，
     *       但额外内存是 O(1)。</li>
     * </ul>
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：事务罩住「网络传对象存储」这一段，
     * 既占着一条 R2DBC 连接（批量上传时就是 {@code Request queue is full} 的触发条件），
     * 又保不住已经写进对象存储的那份 —— 回滚不会撤销存储侧写入，只会留下孤儿文件。
     * 落库是单条写入，失败时用补偿删除（{@code discardUploaded}）收拾存储侧。
     */
    public Mono<FileUploadVO> handleFileUpload(FilePart filePart, UploadOptionsDTO options, Long uploaderId) {
        String fileName = filePart.filename();
        log.info("处理文件上传请求: fileName={}, options={}", fileName, options);

        long maxSize = FileUtils.getMaxSize(maxSizeStr);
        // options 来自请求体，客户端完全可以不传 —— 后续读取它的字段时不能直接解引用
        final UploadOptionsDTO effectiveOptions = options != null ? options : new UploadOptionsDTO();

        // 1. 先按声明的长度快速拒绝，避免为了拒绝一个超大文件而把它整个传上去
        long declaredLength = filePart.headers().getContentLength();
        if (declaredLength > maxSize) {
            log.warn("文件超过大小上限，直接拒绝: fileName={}, size={}, max={}", fileName, declaredLength, maxSize);
            return Mono.error(BusinessException.fileSizeLimit());
        }

        // 2. 文件名与扩展名的白名单：这两项不需要读内容就能判断
        if (!FileUtils.isValidFileName(fileName) || !FileUtils.isAllowedExtension(fileName, getAllowedTypes())) {
            log.warn("文件类型不允许: fileName={}", fileName);
            return Mono.error(BusinessException.fileTypeNotAllowed());
        }

        // 3. 文件名安全化（去掉 ../ 、路径分隔符、控制字符）
        final String safeFileName = FileUtils.sanitizeFileName(fileName);

        // 4. 选择存储策略，把内容以流的形式直接交给它
        return strategyRegistry.getActiveStrategy()
                .flatMap(strategy -> {
                    // 边传边记账：累计字节数（超限即拒）、增量摘要、头部字节样本
                    UploadStreamMeter meter = new UploadStreamMeter(maxSize);
                    Flux<DataBuffer> content = filePart.content().handle(meter::inspect);

                    return strategy.uploadFile(content, safeFileName, declaredLength > 0 ? declaredLength : 0L)
                            .flatMap(filePath -> finishUpload(strategy, meter, safeFileName, filePath,
                                    effectiveOptions, uploaderId));
                })
                .doOnSuccess(result -> log.info("文件上传成功: fileId={}, fileName={}",
                        result.getFileId(), result.getFileName()))
                .doOnError(error -> log.error("文件上传失败: fileName={}", fileName, error));
    }

    /**
     * 上传完成后的收尾：算 MIME、查重、落库。
     *
     * <p><b>查重为什么在上传之后</b>：校验和要等整个流传完才知道，
     * 而流式传输没法「先看完再决定要不要传」。
     * 所以重复文件是「先传上去、发现重复、再把刚传的那份删掉」——
     * 用户看到的结果与从前一致（重复文件被拒 / 返回已存在的那份），
     * 代价是多了一次传输。
     *
     * @param strategy 存储策略
     * @param meter    流式记账器
     * @param safeFileName 安全化后的文件名
     * @param filePath 存储路径
     * @param options  上传选项
     * @param uploaderId 上传者
     * @return 上传结果
     */
    private Mono<FileUploadVO> finishUpload(FileStorageStrategy strategy, UploadStreamMeter meter,
                                            String safeFileName, String filePath,
                                            UploadOptionsDTO options, Long uploaderId) {
        long fileSize = meter.totalBytes();
        String checksum = meter.checksum();
        log.debug("上传完成: fileName={}, size={}, checksum={}", safeFileName, fileSize, checksum);

        // MIME：扩展名 + 头部字节的 magic number（Tika 只看头部样本，不看全量）。
        // 识别本身是阻塞的，丢到 boundedElastic 上；绝不在这条响应式链上 .block()。
        Mono<String> mimeTypeMono = Mono
                .fromCallable(() -> FileUtils.getMimeType(meter.headBytes(), safeFileName))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn("MIME 识别失败，降级为扩展名判定: fileName={}", safeFileName, error);
                    return Mono.just(FileUtils.getContentType(safeFileName));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> FileUtils.getContentType(safeFileName)));

        return mimeTypeMono.flatMap(mimeType -> {
            // 扩展名白名单只看文件名，落库的 MIME 却是按内容嗅探的 ——
            // 两者之间必须再判一次，否则「内容是一段 HTML、名字叫 photo.png」
            // 会以 text/html 入库（存储型 XSS 的原料）。
            // 拒绝的同时把刚传上去的对象删掉，不留孤儿文件。
            if (FileContentTypePolicy.isActiveContent(mimeType)) {
                log.warn("上传内容被识别为主动内容，拒绝: fileName={}, mimeType={}", safeFileName, mimeType);
                return discardUploaded(strategy, filePath)
                        .then(Mono.error(BusinessException.fileTypeNotAllowed()));
            }

            // 重复文件处理
            String duplicateStrategy = options.getDuplicateStrategy();
            if (duplicateStrategy == null || duplicateStrategy.isEmpty()) {
                duplicateStrategy = "reject"; // 默认拒绝上传重复文件
            }

            return handleDuplicateFile(safeFileName, fileSize, checksum, duplicateStrategy, uploaderId)
                    // 查重失败（默认策略下就是「文件已存在」）→ 刚传上去的那份要删掉，
                    // 否则存储里留下一个没人引用的孤儿对象
                    .onErrorResume(error -> discardUploaded(strategy, filePath).then(Mono.error(error)))
                    .flatMap(result -> {
                        // 「replace」：用已存在的文件，把刚传的那份删掉
                        if (result instanceof File existingFile) {
                            log.info("检测到重复文件，使用已存在文件: fileId={}, fileName={}",
                                    existingFile.getId(), existingFile.getFileName());
                            return discardUploaded(strategy, filePath)
                                    .then(buildFileUploadVO(existingFile));
                        }
                        // 其余情况拿到的是最终文件名（正常时就是原名，「rename」时是改名后的）
                        String finalFileName = result.toString();

                        // 落库
                        File file = createFileEntity(
                                finalFileName,
                                filePath,
                                fileSize,
                                checksum,
                                options,
                                strategy.getStrategyKey(),
                                uploaderId
                        );
                        file.setMimeType(mimeType);
                        file.setFileType(FileUtils.getFileType(mimeType));
                        return fileRepository.save(file)
                                // 落库失败 → 刚传上去的那份要删掉。
                                // 这里没有事务兜底（也兜不住存储侧），只能补偿。
                                .onErrorResume(error ->
                                        discardUploaded(strategy, filePath).then(Mono.error(error)))
                                .flatMap(this::buildFileUploadVO);
                    });
        });
    }

    /**
     * 删掉刚上传上去、但不打算保留的那个对象。
     *
     * <p>失败只记日志：这一步是「清理」，清理失败不该把主流程变成另一个错误。
     *
     * @param strategy 存储策略
     * @param filePath 存储路径
     * @return 完成信号
     */
    private Mono<Void> discardUploaded(FileStorageStrategy strategy, String filePath) {
        return strategy.deleteFile(filePath)
                .doOnNext(deleted -> {
                    if (!Boolean.TRUE.equals(deleted)) {
                        log.warn("清理重复上传的对象失败（可能已不存在）: path={}", filePath);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("清理重复上传的对象异常: path={}", filePath, error);
                    return Mono.just(Boolean.FALSE);
                })
                .then();
    }

    /**
     * 处理批量文件上传
     *
     * <p><b>并发上限 4</b>：批量上传每个文件都要占用一段内存与一条连接，
     * 不设上限的话 N 个文件会同时在堆里、同时占着连接池。
     */
    public Flux<FileUploadVO> handleBatchFileUpload(Flux<FilePart> fileParts, UploadOptionsDTO options, Long uploaderId) {
        log.info("处理批量文件上传请求: options={}", options);

        // 并发上限 4：再多就会让堆和连接池同时吃紧
        return fileParts.flatMap(filePart -> {
            log.info("处理批量上传中的文件: fileName={}", filePart.filename());
            return handleFileUpload(filePart, options, uploaderId)
                    .onErrorResume(error -> {
                        // 单个文件上传失败不应该影响整个批量上传过程
                        log.error("批量上传中的文件处理失败: fileName={}", filePart.filename(), error);
                        return Mono.empty();
                    });
        }, BATCH_UPLOAD_CONCURRENCY);
    }

    /**
     * 初始化分片上传
     */
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, UploadOptionsDTO options, Long uploaderId) {
        log.info("初始化分片上传: fileName={}, fileSize={}, options={}", fileName, fileSize, options);

        // 客户端文件名在进入任何存储/DB 路径之前先安全化：
        // 去掉 ../、路径分隔符、控制字符，并截断到列长度上限。
        String safeFileName = FileUtils.sanitizeFileName(fileName);

        // 验证文件类型和大小
        return isFileAllowed(safeFileName, fileSize)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("文件类型或大小不允许: fileName={}, fileSize={}", fileName, fileSize);
                        return Mono.error(BusinessException.fileTypeNotAllowed());
                    }

                    // 选择存储策略
                    return strategyRegistry.getActiveStrategy()
                            .flatMap(strategy -> {
                                // 应用层的上传ID（给客户端用）
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
                                    if (options.getStorage() != null) {
                                        metadata.put("storage", options.getStorage());
                                    }
                                }

                                // 记录上传信息
                                MultipartUploadInfo uploadInfo = MultipartUploadInfo.builder()
                                        .fileName(safeFileName)
                                        .fileSize(fileSize)
                                        // 会话归属真实登录用户，不用客户端可控的参数当身份
                                        .userId(uploaderId)
                                        .description(options != null ? options.getDescription() : null)
                                        .isPublic(options != null && "public".equals(options.getAccess()))
                                        // 钉住会话创建时的策略：中途切策略会让分片找不到家
                                        .strategyKey(strategy.getStrategyKey())
                                        .createTime(LocalDateTime.now())
                                        .build();

                                // 初始化分片上传
                                // 能力缺失（策略不支持分片）会在进入存储逻辑之前就抛错
                                return StorageCapabilities.multipart(strategy)
                                        .initiateMultipartUpload(safeFileName, fileSize, metadata)
                                        .flatMap(result -> {
                                            // 存储策略自己的 uploadId 必须留着 ——
                                            // 后面每个分片、合并、终止都要用它去查策略侧的上下文
                                            uploadInfo.setStrategyUploadId(result.get("uploadId"));

                                            // 合并结果
                                            result.put("uploadId", uploadId);
                                            result.put("chunkSize", String.valueOf(DEFAULT_CHUNK_SIZE));
                                            result.put("chunks", String.valueOf((fileSize + DEFAULT_CHUNK_SIZE - 1) / DEFAULT_CHUNK_SIZE));

                                            // 会话只剩 Redis 一份，所以写失败就是初始化失败 ——
                                            // 不能像从前那样「写失败也无所谓，反正 JVM 里还有」
                                            return persistSession(uploadId, uploadInfo)
                                                    .thenReturn(result)
                                                    .doOnSuccess(ok -> log.info(
                                                            "分片上传初始化成功: uploadId={}, strategyUploadId={}, fileName={}",
                                                            uploadId, uploadInfo.getStrategyUploadId(), safeFileName));
                                        });
                            });
                })
                .doOnError(error -> log.error("初始化分片上传失败: fileName={}", fileName, error));
    }

    /**
     * 把分片会话写进 Redis（带 TTL）。
     *
     * <p><b>在响应链上执行</b>，不再 {@code .subscribe()} 火并遗忘：
     * 会话现在是唯一副本，写没写进去决定了后续分片能不能找到它，
     * 调用方必须等到结果。
     *
     * @param uploadId   应用层上传ID
     * @param uploadInfo 会话信息
     * @return 完成信号
     */
    private Mono<Void> persistSession(String uploadId, MultipartUploadInfo uploadInfo) {
        return reactiveRedisTemplate.opsForValue().set(uploadId, uploadInfo, MULTIPART_UPLOAD_TTL)
                .doOnError(error -> log.error("分片会话写入 Redis 失败: uploadId={}", uploadId, error))
                .then();
    }

    /**
     * 删除 Redis 里的分片会话（同样在响应链上执行）
     *
     * @param uploadId 应用层上传ID
     * @return 完成信号
     */
    private Mono<Void> removePersistedSession(String uploadId) {
        // 会话与分片状态是两个 key，一起删
        return reactiveRedisTemplate.delete(partStateKey(uploadId))
                .onErrorResume(error -> {
                    log.warn("分片状态从 Redis 删除失败: uploadId={}", uploadId, error);
                    return Mono.empty();
                })
                .then(reactiveRedisTemplate.delete(uploadId))
                .doOnError(error -> log.warn("分片会话从 Redis 删除失败: uploadId={}", uploadId, error))
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    /**
     * 解析分片会话
     *
     * <p>先看 JVM 里的会话；没有就去 Redis 找（进程重启 / 多实例部署的情况）。
     *
     * <p>Redis 的 key 就是 {@code uploadId} 本身 —— 它已经带 {@code multipart:} 前缀了。
     * 早先这里又拼了一次前缀，key 变成 {@code multipart:multipart:<uuid>}，
     * 而写入时用的是 {@code multipart:<uuid>}，于是恢复分支<b>永远</b>走不到。
     *
     * @param uploadId   应用层上传ID
     * @param uploaderId 当前操作者
     * @return 会话信息
     */
    private Mono<MultipartUploadInfo> resolveSession(String uploadId, Long uploaderId) {
        // Redis 是唯一副本：进程内静态 Map 已经去掉，
        // 否则多实例下 B 节点永远看不到 A 节点建立的会话
        return reactiveRedisTemplate.opsForValue().get(uploadId)
                .cast(MultipartUploadInfo.class)
                .flatMap(info -> checkUploadOwner(info, uploaderId).thenReturn(info))
                .switchIfEmpty(Mono.error(new BusinessException("上传ID不存在或已过期")));
    }

    /**
     * 上传分片
     */
    public Mono<String> uploadPart(String uploadId, int partNumber, byte[] partData, Long uploaderId) {
        log.info("上传分片: uploadId={}, partNumber={}, dataSize={}", uploadId, partNumber, partData.length);

        // 分片号从 1 开始。0 或负数会让下面 parts.set(partNumber - 1, ...) 越界
        if (partNumber < 1) {
            log.warn("非法的分片号: uploadId={}, partNumber={}", uploadId, partNumber);
            return Mono.error(new BusinessException("分片号必须从 1 开始"));
        }

        return resolveSession(uploadId, uploaderId)
                .flatMap(uploadInfo -> {
                    // 累计字节数必须在「把分片交给策略之前」就判掉：
                    // 声明的大小是客户端说了算的，不判就等于没有上限
                    return readPartState(uploadId)
                            .flatMap(state -> {
                                long already = state.sizes().entrySet().stream()
                                        .filter(entry -> entry.getKey() != partNumber)
                                        .mapToLong(Map.Entry::getValue)
                                        .sum();
                                long wouldBeTotal = already + partData.length;
                                long allowedTotal = allowedTotalBytes(uploadInfo);
                                if (wouldBeTotal > allowedTotal) {
                                    log.warn("分片累计字节数超过声明值的容忍上限: uploadId={}, wouldBeTotal={}, allowed={}",
                                            uploadId, wouldBeTotal, allowedTotal);
                                    return Mono.error(BusinessException.fileSizeLimit());
                                }
                                return uploadPartWithStrategy(uploadId, uploadInfo, partNumber, partData);
                            });
                });
    }

    /**
     * 允许的累计字节数 = 声明的文件大小 + 容忍量。
     *
     * <p>容忍量取 {@code max(声明值 10%, 1MB)} —— 分片边界不会刚好落在声明值上，
     * 一分不容忍会让最后一片永远传不上来；容忍太多又等于把上限放开了。
     *
     * @param uploadInfo 分片会话
     * @return 允许的累计字节数；会话没声明大小时不设限
     */
    private long allowedTotalBytes(MultipartUploadInfo uploadInfo) {
        Long declared = uploadInfo.getFileSize();
        if (declared == null || declared <= 0) {
            return Long.MAX_VALUE;
        }
        return declared + Math.max(declared / 10, MULTIPART_MIN_TOLERANCE_BYTES);
    }

    /**
     * 读一份分片状态（Redis Hash）。
     *
     * <p>字段约定：{@code e:<n>} = ETag、{@code s:<n>} = 字节数、{@code h} = 文件头样本。
     * 每个分片只写自己的字段，因此并行上传不会互相覆盖。
     *
     * @param uploadId 应用层上传ID
     * @return 分片状态
     */
    private Mono<PartState> readPartState(String uploadId) {
        return reactiveRedisTemplate.<String, Object>opsForHash()
                .entries(partStateKey(uploadId))
                .collectMap(entry -> String.valueOf(entry.getKey()),
                        entry -> entry.getValue() == null ? "" : String.valueOf(entry.getValue()))
                .map(raw -> {
                    Map<Integer, String> etags = new HashMap<>();
                    Map<Integer, Long> sizes = new HashMap<>();
                    byte[] head = null;

                    for (Map.Entry<String, String> entry : raw.entrySet()) {
                        String field = entry.getKey();
                        String value = entry.getValue();
                        if (FIELD_HEAD.equals(field)) {
                            head = value.isEmpty() ? null : Base64.getDecoder().decode(value);
                        } else if (field.startsWith(FIELD_ETAG)) {
                            etags.put(parsePartNumber(field.substring(FIELD_ETAG.length())), value);
                        } else if (field.startsWith(FIELD_SIZE)) {
                            sizes.put(parsePartNumber(field.substring(FIELD_SIZE.length())), parseSize(value));
                        }
                    }
                    return new PartState(etags, sizes, head);
                });
    }

    private Integer parsePartNumber(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            log.warn("分片状态里有无法解析的分片号: {}", raw);
            return null;
        }
    }

    private long parseSize(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("分片状态里有无法解析的字节数: {}", raw);
            return 0L;
        }
    }

    private String partStateKey(String uploadId) {
        return uploadId + PART_STATE_SUFFIX;
    }

    /**
     * 校验分片上传会话是否属于当前用户
     *
     * <p><b>fail-closed</b>：证明不了归属就拒绝。
     * 早先的写法是 {@code info.userId != null && uploaderId != null && 不等} 才拒绝 ——
     * 任意一侧为空就直接放行（「比不了」被当成了「没问题」）。
     * 目前四个分片端点都是 {@code @SaCheckPermission} + {@code StpUtil.getLoginIdAsLong()}，
     * 正常路径下不会拿到空身份，但这是<b>碰巧安全</b>：只要将来有一条路径传 null
     * （老会话、内部调用、Redis 恢复的会话），任何人都能往别人的上传会话里塞分片、
     * 或者替别人合并落库。
     */
    private Mono<Void> checkUploadOwner(MultipartUploadInfo info, Long uploaderId) {
        if (uploaderId == null) {
            log.warn("分片上传会话的请求者身份为空，拒绝: uploadIdOwner={}", info.getUserId());
            return Mono.error(PermissionDeniedException.accessDenied("无权操作该上传会话"));
        }
        if (info.getUserId() == null) {
            log.warn("分片上传会话没有记录归属，拒绝: requester={}", uploaderId);
            return Mono.error(PermissionDeniedException.accessDenied("无权操作该上传会话"));
        }
        if (!info.getUserId().equals(uploaderId)) {
            log.warn("越权访问分片上传会话: uploadIdOwner={}, requester={}", info.getUserId(), uploaderId);
            return Mono.error(PermissionDeniedException.accessDenied("无权操作该上传会话"));
        }
        return Mono.empty();
    }

    private Mono<String> uploadPartWithStrategy(String uploadId, MultipartUploadInfo uploadInfo, int partNumber, byte[] data) {
        log.debug("上传分片: uploadId={}, partNumber={}, dataSize={}", uploadId, partNumber, data.length);

        // 选择存储策略：用会话创建时钉住的那个
        return resolveStrategy(uploadInfo)
                .flatMap(strategy -> {
                    // 创建数据缓冲区
                    DataBuffer buffer = new DefaultDataBufferFactory().wrap(ByteBuffer.wrap(data));

                    // 上传分片
                    return StorageCapabilities.multipart(strategy)
                            .uploadPart(strategyUploadId(uploadId, uploadInfo), partNumber, Flux.just(buffer))
                            .flatMap(etag -> {
                                // 只写这一片自己的字段（HSET 原子），
                                // 不碰其它分片、也不整体覆盖会话 ——
                                // 并行上传时「读-改-写整份会话」会让后写者吃掉先写者
                                var hashOps = reactiveRedisTemplate.<String, Object>opsForHash();
                                String key = partStateKey(uploadId);

                                Mono<Boolean> writeEtag = hashOps.put(key, FIELD_ETAG + partNumber, etag);
                                Mono<Boolean> writeSize = hashOps.put(key, FIELD_SIZE + partNumber, String.valueOf(data.length));

                                // 文件头只取第 1 片：只有这一个分片号会写这个字段，
                                // 不需要 CAS —— 别的分片根本不碰它
                                Mono<Boolean> writeHead = (partNumber == 1 && data.length > 0)
                                        ? hashOps.put(key, FIELD_HEAD, Base64.getEncoder().encodeToString(
                                                Arrays.copyOf(data, Math.min(data.length, HEAD_SAMPLE_SIZE))))
                                        : Mono.just(false);

                                return writeEtag
                                        .then(writeSize)
                                        .then(writeHead)
                                        .doOnNext(written -> {
                                            if (Boolean.TRUE.equals(written)) {
                                                log.debug("已记录文件头样本: uploadId={}", uploadId);
                                            }
                                        })
                                        .thenReturn(etag);
                            });
                })
                .doOnError(error -> log.error("分片上传失败: uploadId={}, partNumber={}", uploadId, partNumber, error));
    }

    /**
     * 完成分片上传
     *
     * <p>同样刻意不加 {@code @Transactional}：合并分片是存储侧动作，
     * 事务保不住它，只会把一条 R2DBC 连接占在网络 IO 上。
     * 落库失败时补偿删除合并出来的对象。
     */
    public Mono<FileUploadVO> completeMultipartUpload(String uploadId, List<String> partETags, UploadOptionsDTO options, Long uploaderId) {
        log.info("完成分片上传: uploadId={}, partETags={}", uploadId, partETags);

        return resolveSession(uploadId, uploaderId)
                .flatMap(uploadInfo -> readPartState(uploadId).flatMap(state -> {
                    // 合并前复核一次实际总大小：uploadPart 已经拦过，但
                    // Redis 恢复出来的老会话可能没有字节账，别在这条路上漏掉
                    long actualTotal = state.sizes().values().stream().mapToLong(Long::longValue).sum();
                    long allowedTotal = allowedTotalBytes(uploadInfo);
                    if (actualTotal > allowedTotal) {
                        log.warn("分片上传实际总大小超过声明值的容忍上限: uploadId={}, actual={}, allowed={}",
                                uploadId, actualTotal, allowedTotal);
                        return Mono.error(BusinessException.fileSizeLimit());
                    }

                    // 合并上传选项
                    final UploadOptionsDTO finalOptions = mergeUploadOptions(options, uploadInfo);

                    // 服务端记录的分片 ETag 才是权威：客户端传来的列表可能缺项或乱序
                    List<String> orderedETags = state.etags().isEmpty()
                            ? partETags
                            : state.etags().entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .map(Map.Entry::getValue)
                                    .toList();

                    // 选择存储策略：用会话创建时钉住的那个
                    return resolveStrategy(uploadInfo)
                            .flatMap(strategy ->
                                    // 合并分片
                                    StorageCapabilities.multipart(strategy)
                                            .completeMultipartUpload(strategyUploadId(uploadId, uploadInfo), orderedETags)
                                            .flatMap(filePath ->
                                                    // 落库前按内容再判一次：分片路径原先既不做主动内容判定，
                                                    // 也从来没设置过 mimeType / fileType
                                                    resolveMultipartMimeType(uploadInfo, state.head())
                                                            .flatMap(mimeType -> {
                                                                if (FileContentTypePolicy.isActiveContent(mimeType)) {
                                                                    log.warn("分片上传内容被识别为主动内容，拒绝: fileName={}, mimeType={}",
                                                                            uploadInfo.getFileName(), mimeType);
                                                                    return discardUploaded(strategy, filePath)
                                                                            .then(Mono.error(BusinessException.fileTypeNotAllowed()));
                                                                }

                                                                // 创建文件记录
                                                                File file = createFileEntity(
                                                                        // 旧会话可能是安全化之前创建的，落库前再兜底一次
                                                                        FileUtils.sanitizeFileName(uploadInfo.getFileName()),
                                                                        filePath,
                                                                        // 落库必须是**实际收到**的字节数：
                                                                        // 声明值来自客户端，下载接口会按它下发 Content-Length ——
                                                                        // 存了声明值，一个「声明 2MB、实传 100 字节」的上传
                                                                        // 会让每次下载都挂在那里等 2MB
                                                                        actualTotal,
                                                                        "", // 分片上传不计算校验和
                                                                        finalOptions,
                                                                        // 落库的 storage_type 必须是「真正存这个文件的策略」
                                                                        strategy.getStrategyKey(),
                                                                        uploaderId
                                                                );
                                                                file.setMimeType(mimeType);
                                                                file.setFileType(FileUtils.getFileType(mimeType));

                                                                // 保存文件记录
                                                                return fileRepository.save(file)
                                                                        // 落库失败 → 删掉刚合并出来的对象，别留孤儿
                                                                        .onErrorResume(error ->
                                                                                discardUploaded(strategy, filePath).then(Mono.error(error)))
                                                                        .flatMap(this::buildFileUploadVO)
                                                                        .flatMap(result -> removePersistedSession(uploadId)
                                                                                .thenReturn(result))
                                                                        .doOnSuccess(result -> log.info(
                                                                                "分片上传完成: fileId={}, fileName={}",
                                                                                result.getFileId(), result.getFileName()));
                                                            })
                                            )
                            );
                }))
                .doOnError(error -> log.error("完成分片上传失败: uploadId={}", uploadId, error));
    }

    /**
     * 按第 1 个分片的头部字节嗅探 MIME，拿不到样本时退化为扩展名判定。
     *
     * @param uploadInfo 分片会话
     * @return MIME 类型
     */
    private Mono<String> resolveMultipartMimeType(MultipartUploadInfo uploadInfo, byte[] headSample) {
        String fileName = FileUtils.sanitizeFileName(uploadInfo.getFileName());
        byte[] head = headSample != null ? headSample : new byte[0];

        return Mono.fromCallable(() -> FileUtils.getMimeType(head, fileName))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn("分片上传 MIME 识别失败，降级为扩展名判定: fileName={}", fileName, error);
                    return Mono.just(FileUtils.getContentType(fileName));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> FileUtils.getContentType(fileName)));
    }

    /**
     * 终止分片上传
     */
    public Mono<Boolean> abortMultipartUpload(String uploadId, Long uploaderId) {
        log.info("终止分片上传: uploadId={}", uploadId);

        return resolveSession(uploadId, uploaderId)
                .flatMap(uploadInfo ->
                        // 选择存储策略：用会话创建时钉住的那个
                        resolveStrategy(uploadInfo)
                                .flatMap(strategy ->
                                        // 终止分片上传
                                        StorageCapabilities.multipart(strategy)
                                                .abortMultipartUpload(strategyUploadId(uploadId, uploadInfo))
                                                .flatMap(result -> {
                                                    if (Boolean.TRUE.equals(result)) {
                                                        log.info("分片上传已终止: uploadId={}", uploadId);
                                                        return removePersistedSession(uploadId).thenReturn(true);
                                                    }
                                                    log.warn("终止分片上传失败: uploadId={}", uploadId);
                                                    return Mono.just(false);
                                                })
                                )
                )
                .onErrorResume(error -> {
                    log.error("终止分片上传失败: uploadId={}", uploadId, error);
                    return Mono.just(false);
                });
    }

    /**
     * 取回会话对应的存储策略
     *
     * <p>优先用会话里钉住的 {@code strategyKey}；拿不到（老会话）才回退到当前活跃策略。
     */
    private Mono<FileStorageStrategy> resolveStrategy(MultipartUploadInfo uploadInfo) {
        String strategyKey = uploadInfo.getStrategyKey();
        if (strategyKey != null && !strategyKey.isEmpty()) {
            return strategyRegistry.getStrategy(strategyKey);
        }
        return strategyRegistry.getActiveStrategy();
    }

    /**
     * 存储策略自己的 uploadId —— 调策略的分片方法时必须用它
     *
     * <p>只有「早期会话」（{@code strategyUploadId} 为空）才回退到应用层的 uploadId；
     * 新会话一律用存储侧的真实 id。
     */
    private String strategyUploadId(String uploadId, MultipartUploadInfo uploadInfo) {
        String strategyUploadId = uploadInfo.getStrategyUploadId();
        return strategyUploadId != null && !strategyUploadId.isEmpty()
                ? strategyUploadId
                : uploadId;
    }

    /**
     * 创建文件实体对象
     *
     * @param fileName 文件名
     * @param filePath 文件路径 - 不包含访问前缀，只保存相对路径
     * @param fileSize 文件大小
     * @param checksum 文件校验和
     * @param options 上传选项
     * @param storageType 存储类型
     * @param uploaderId 上传者ID（来自登录态，不是客户端参数）
     * @return 文件实体
     */
    private File createFileEntity(String fileName, String filePath, long fileSize,
                                 String checksum, UploadOptionsDTO options, String storageType,
                                 Long uploaderId) {
        File file = new File();
        file.setFileName(fileName);
        // 确保保存的路径不包含前缀，只保存相对路径
        // 在获取URL时会通过getPublicUrl方法添加前缀
        file.setFilePath(filePath);
        file.setFileSize(fileSize);
        // 原先写死 1L：所有上传文件都归到 admin 名下，
        // 导致归属校验（删除/修改只能操作自己的文件）完全没有意义。
        file.setCreatorId(uploaderId);
        file.setDescription(options != null ? options.getDescription() : null);
        file.setAccessType(options != null && "public".equals(options.getAccess()) ? 0 : 1); // 0-公开, 1-私有
        file.setStatus(1); // 1-激活
        file.setStorageType(storageType);
        file.setChecksum(checksum);
        file.setUploadTime(LocalDateTime.now());
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        file.setIsDeleted(false);

        return file;
    }

    /**
     * 构建文件上传结果VO
     *
     * @param file 文件实体
     * @return 文件上传结果VO
     */
    public Mono<FileUploadVO> buildFileUploadVO(File file) {
        // 使用存储策略获取完整的访问URL
        return strategyRegistry.getStrategy(file.getStorageType())
            .flatMap(strategy -> strategy.getPublicUrl(file.getFilePath()))
            .map(fullUrl -> FileUploadVO.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .fileUrl(fullUrl)  // 使用完整URL而不是相对路径
                    .fileSize(file.getFileSize())
                    .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                    .fileType(file.getFileType())
                    .uploadTime(file.getUploadTime().toString())
                    .build());
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
        if (mergedOptions.getAccess() == null && uploadInfo.getIsPublic() != null) {
            mergedOptions.setAccess(uploadInfo.getIsPublic() ? "public" : "private");
        }

        return mergedOptions;
    }

    /**
     * 处理重复文件
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param checksum 校验和
     * @param strategy 处理策略（reject, replace, rename）
     * @param uploaderId 上传者ID
     * @return 处理结果，如果是reject则返回错误，如果是replace则返回已存在的文件，如果是rename则返回新文件名
     */
    private Mono<?> handleDuplicateFile(String fileName, Long fileSize, String checksum,
                                        String strategy, Long uploaderId) {
        // 如果没有提供校验和，则不进行重复检查
        if (checksum == null || checksum.isEmpty()) {
            return Mono.just(fileName);
        }

        // 只查当前用户自己的文件：
        // 按校验和全局查会把别人的文件名 / 公开 URL 返回给调用方 ——
        // 攻击者只要拿到文件内容（或猜测常见文件的校验和）就能读到跨用户元数据。
        return fileRepository.findFirstByChecksumAndCreatorIdAndIsDeleted(checksum, uploaderId, 0)
                .flatMap(existingFile -> {
                    switch (strategy) {
                        case "reject":
                            // 拒绝上传
                            return Mono.error(new BusinessException("文件已存在: " + existingFile.getFileName()));
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
                            return Mono.error(new BusinessException("文件已存在: " + existingFile.getFileName()));
                    }
                })
                .switchIfEmpty(Mono.just(fileName)); // 如果没有找到重复文件，则返回原文件名
    }
}
