package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.FileUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件元数据服务
 *
 * <p>承载文件详情、更新、版本历史、搜索与批量信息查询。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataService {

    /**
     * 单页最多返回多少条。
     *
     * <p>{@code size} 来自客户端，不夹取的话 {@code size = 0} 会让
     * 「总页数 = (total + size - 1) / size」变成<b>除零</b>，
     * {@code size = -1} 则会生成非法的 {@code LIMIT -1}。
     */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageStrategyRegistry strategyRegistry;
    private final FileDownloadService fileDownloadService;

    /**
     * 获取文件详情
     *
     * @param fileId 文件ID
     * @param ctx    操作者上下文 —— 用于可见性判定
     * @return 文件详情
     */
    public Mono<FileInfoVO> getFileInfo(Long fileId, OperatorContext ctx) {
        log.info("获取文件详情: fileId={}", fileId);

        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMap(this::buildFileInfoVO)
                .flatMap(fileInfoVO -> enrichWithCreatorAndUrls(fileInfoVO, fileId, ctx))
                .doOnSuccess(fileInfo -> log.info("获取文件详情成功: fileId={}", fileId))
                .doOnError(error -> log.error("获取文件详情失败: fileId={}", fileId, error));
    }

    /**
     * 更新文件信息
     *
     * @param fileId        文件ID
     * @param fileUpdateDTO 文件信息
     * @param ctx           操作者上下文 —— 只有创建者与管理员能改
     * @return 更新后的文件信息
     */
    @Transactional
    public Mono<FileInfoVO> updateFileInfo(Long fileId, FileUpdateDTO fileUpdateDTO, OperatorContext ctx) {
        log.info("更新文件信息: fileId={}, fileUpdateDTO={}", fileId, fileUpdateDTO);

        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMap(file -> {
                    FileAccessPolicy.requireCanModify(file, ctx);
                    // 更新文件信息
                    if (fileUpdateDTO.getFileName() != null && !fileUpdateDTO.getFileName().isEmpty()) {
                        // 文件名会原样出现在下载响应的 Content-Disposition 里，
                        // 必须先消毒：否则客户端可以用带引号/换行的文件名做响应头注入。
                        file.setFileName(FileUtils.sanitizeFileName(fileUpdateDTO.getFileName()));
                    }
                    if (fileUpdateDTO.getDescription() != null) {
                        file.setDescription(fileUpdateDTO.getDescription());
                    }
                    if (fileUpdateDTO.getStatus() != null) {
                        file.setStatus(fileUpdateDTO.getStatus());
                    }

                    file.setUpdateTime(LocalDateTime.now());

                    // 保存更新后的文件信息
                    return fileRepository.save(file)
                            .flatMap(this::buildFileInfoVO)
                            .flatMap(fileInfoVO -> enrichWithCreatorAndUrls(fileInfoVO, fileId, ctx));
                })
                .doOnSuccess(fileInfo -> log.info("更新文件信息成功: fileId={}", fileId))
                .doOnError(error -> log.error("更新文件信息失败: fileId={}", fileId, error));
    }

    /**
     * 获取文件版本历史
     *
     * @param fileId 文件ID
     * @param ctx    操作者上下文 —— 用于可见性判定
     * @return 版本历史
     */
    public Flux<FileVersionVO> getFileVersions(Long fileId, OperatorContext ctx) {
        log.info("获取文件版本历史: fileId={}", fileId);

        // 先查询文件信息，确保文件存在
        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMapMany(file -> {
                    // 注意：file 必须留在这层 lambda 里 ——
                    // 后面再 flatMap 一次的话，file 已经出了作用域，
                    // 只能拿到 strategy 和 url，拿不到大小/创建者这些字段。
                    return strategyRegistry.getStrategy(file.getStorageType())
                            .flatMap(strategy -> strategy.getPublicUrl(file.getFilePath()))
                            .map(fullUrl -> {
                                // TODO: 替换为实际的版本查询逻辑
                                // 当前版本库（t_file_versions）没有对应的实体与仓储，
                                // 因此这里只返回当前版本作为占位。
                                return FileVersionVO.builder()
                                        .id(1L) // 版本ID通常不同于文件ID
                                        .fileId(fileId)
                                        .versionNumber(1)
                                        .versionTag("v1")
                                        .filePath(fullUrl) // 使用完整URL
                                        .fileSize(file.getFileSize())
                                        .formattedSize(FileUtils.formatFileSize(file.getFileSize()))
                                        .creatorId(file.getCreatorId())
                                        .isCurrent(true)
                                        .createTime(Date.from(file.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()))
                                        .build();
                            });
                })
                .doOnComplete(() -> log.info("获取文件版本历史完成: fileId={}", fileId))
                .doOnError(error -> log.error("获取文件版本历史失败: fileId={}", fileId, error));
    }

    /**
     * 搜索文件
     *
     * <p><b>范围收敛</b>：非管理员只能搜自己的文件 ——
     * {@code creatorId} 由服务端强制覆盖为当前登录用户，客户端传什么都不算数。
     * 管理员才可以按 {@code creatorId} 查别人。
     *
     * @param searchDTO 搜索参数
     * @param ctx       操作者上下文
     * @return 搜索结果
     */
    public Mono<Map<String, Object>> searchFiles(FileSearchDTO searchDTO, OperatorContext ctx) {
        log.info("搜索文件: searchDTO={}", searchDTO);

        if (ctx == null || ctx.userId() == null) {
            // 没有登录态就什么都搜不到 —— 不能落到「不限制 creator = 全站可见」
            return Mono.just(emptySearchResult(searchDTO));
        }
        if (!ctx.canManageOthers()) {
            // 强制收敛到自己的文件：客户端传 creatorId=别人 也没用
            searchDTO.setCreatorId(ctx.userId());
        }

        // 分页参数夹取：size=0 会让下面的 totalPages 计算除零，size<0 会生成非法 LIMIT
        int size = clampPageSize(searchDTO.getSize());
        int current = Math.max(1, searchDTO.getCurrent() == null ? 1 : searchDTO.getCurrent());
        int skip = (current - 1) * size;

        // 执行查询（动态条件 / 动态排序 / 分页都在 Repository 片段里）
        return fileRepository.searchFiles(searchDTO, size, skip)
                // 复用 buildFileInfoVO：filePath「存储相对路径 → 可直接访问地址」的转换
                // 只在那一处做。早先这里是手搓的另一个 builder，漏掉了 getPublicUrl ——
                // 于是 /files/search 返回裸路径、/files/detail 返回完整 URL，
                // 同一个字段两个接口两种东西。
                .concatMap(this::buildFileInfoVO)
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
                    fileDownloadService.generatePreviewUrl(fileInfoVO.getId(), 3600, ctx)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(fileDownloadService.generateDownloadUrl(fileInfoVO.getId(), 3600, ctx))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .collectList()
                .zipWith(fileRepository.countFiles(searchDTO))
                .map(tuple -> {
                    List<FileInfoVO> files = tuple.getT1();
                    Long total = tuple.getT2();

                    Map<String, Object> result = new HashMap<>();
                    result.put("content", files);
                    result.put("page", current);
                    result.put("size", size);
                    result.put("total", total);
                    result.put("totalPages", size > 0 ? (total + size - 1) / size : 0);

                    return result;
                })
                .doOnSuccess(result -> log.info("搜索文件成功: 总数={}", result.get("total")))
                .doOnError(error -> log.error("搜索文件失败", error));
    }

    /**
     * 批量获取文件详细信息
     *
     * @param fileIds 文件ID列表
     * @param ctx     操作者上下文 —— 不可见的文件被直接剔除
     * @return 文件ID到文件详情的映射
     */
    public Mono<Map<Long, FileInfoVO>> getBatchFileInfos(List<Long> fileIds, OperatorContext ctx) {
        log.info("批量获取文件详细信息: fileIds={}", fileIds);

        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        // 创建结果Map
        Map<Long, FileInfoVO> result = new ConcurrentHashMap<>();

        // 批量查询文件信息
        return fileRepository.findAllById(fileIds)
                // 只处理未删除的文件。
                // is_deleted 是 Boolean，直接和 int 常量比较会恒为 false，
                // 必须用 Boolean.FALSE.equals(...) 判断。
                .filter(file -> !Boolean.TRUE.equals(file.getIsDeleted()))
                .flatMap(file -> buildFileInfoVO(file)
                        .doOnNext(fileInfoVO -> result.put(file.getId(), fileInfoVO))
                        .thenReturn(file.getId())
                )
                .collectList()
                .flatMap(ids -> {
                    // 批量生成预览和下载链接
                    List<Mono<Void>> urlGenerationTasks = new ArrayList<>();

                    for (Long fileId : ids) {
                        FileInfoVO fileInfoVO = result.get(fileId);
                        if (fileInfoVO != null) {
                            Mono<Void> task = fileDownloadService.generatePreviewUrl(fileId, 3600, ctx)
                                    .doOnNext(fileInfoVO::setPreviewUrl)
                                    .then(fileDownloadService.generateDownloadUrl(fileId, 3600, ctx))
                                    .doOnNext(fileInfoVO::setDownloadUrl)
                                    .then();
                            urlGenerationTasks.add(task);
                        }
                    }

                    return Flux.merge(urlGenerationTasks).then(Mono.just(result));
                })
                .doOnSuccess(map -> log.info("批量获取文件详情成功: 总数={}", map.size()))
                .doOnError(error -> log.error("批量获取文件详情失败", error));
    }

    /**
     * 批量获取文件永久URL
     *
     * <p>这是<b>公开内容的直链</b>（对象存储的公开地址，或应用内
     * {@code /files/public/**} 路由），用于文章里的图片渲染。
     * 路径里带随机 UUID，不可枚举。
     *
     * <p>这个 URL 天生是<b>不带任何身份凭证</b>的 —— 拿到它的人就能直接取到内容
     * （{@code <img src>} 带不了 {@code Authorization} 头）。
     * 文件可见性下线后，所有文件都按公开处理，这里不再过滤。
     *
     * <p>因此本方法的结果<b>与操作者无关</b>（只由「是否公开」决定），
     * 缓存 key 里不需要带操作者身份。
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到永久URL的映射（只含公开文件）
     */
    public Mono<Map<Long, String>> getBatchFilePermanentUrls(List<Long> fileIds) {
        log.info("批量获取文件永久URL: fileIds={}", fileIds);

        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        // 创建结果Map
        Map<Long, String> result = new ConcurrentHashMap<>();

        // 批量获取文件信息并生成永久URL
        return Flux.fromIterable(fileIds)
                .flatMap(fileId ->
                    fileRepository.findByIdAndIsDeleted(fileId, 0)
                        .flatMap(file -> {
                            // 根据存储策略生成永久URL
                            String storageType = file.getStorageType();
                            return strategyRegistry.getStrategy(storageType)
                                .flatMap(strategy ->
                                        // 公开文件，使用直接访问路径
                                        strategy.getPublicUrl(file.getFilePath())
                                            .doOnNext(url -> result.put(fileId, url))
                                            .thenReturn(fileId)
                                );
                        })
                        .onErrorResume(e -> {
                            log.warn("获取文件永久URL失败: fileId={}", fileId, e);
                            return Mono.empty();
                        })
                )
                .then(Mono.just(result))
                .doOnSuccess(map -> log.info("批量获取文件永久URL成功: 总数={}", map.size()))
                .doOnError(error -> log.error("批量获取文件永久URL失败", error));
    }

    /**
     * 批量获取文件URL
     *
     * @param fileIds 文件ID列表
     * @param ctx     操作者上下文 —— 不可见的文件被直接剔除
     * @return 文件ID到URL的映射
     */
    public Mono<Map<Long, String>> getBatchFileUrls(List<Long> fileIds, OperatorContext ctx) {
        log.info("批量获取文件URL: fileIds={}", fileIds);

        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        Map<Long, String> result = new ConcurrentHashMap<>();

        // 批量生成下载URL
        return Flux.fromIterable(fileIds)
                .flatMap(fileId ->
                    fileRepository.findByIdAndIsDeleted(fileId, 0)
                        .flatMap(file -> fileDownloadService.generateDownloadUrl(fileId, 3600, ctx)
                                .doOnNext(url -> result.put(fileId, url)))
                        .onErrorResume(e -> {
                            log.warn("获取文件URL失败: fileId={}", fileId, e);
                            return Mono.empty();
                        })
                )
                .then(Mono.just(result))
                .doOnSuccess(map -> log.info("批量获取文件URL成功: 总数={}", map.size()))
                .doOnError(error -> log.error("批量获取文件URL失败", error));
    }

    /**
     * 创建FileInfoVO并设置完整URL
     * @param file 文件实体
     * @return 包含完整URL的FileInfoVO的Mono
     */
    public Mono<FileInfoVO> buildFileInfoVO(File file) {
        // 创建基本的FileInfoVO
        FileInfoVO fileInfoVO = FileInfoVO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                // 暂时使用相对路径，后面会更新为完整URL
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
                .createTime(file.getCreateTime())
                .build();

        // 获取完整URL并更新filePath
        return strategyRegistry.getStrategy(file.getStorageType())
                .flatMap(strategy -> strategy.getPublicUrl(file.getFilePath()))
                .doOnNext(fileInfoVO::setFilePath)
                .then(Mono.just(fileInfoVO))
                .switchIfEmpty(Mono.just(fileInfoVO));
    }

    /**
     * 补齐创建者名称与预览 / 下载链接
     */
    private Mono<FileInfoVO> enrichWithCreatorAndUrls(FileInfoVO fileInfoVO, Long fileId, OperatorContext ctx) {
        Mono<FileInfoVO> userInfoMono;
        if (fileInfoVO.getCreatorId() != null) {
            userInfoMono = userRepository.findById(fileInfoVO.getCreatorId())
                    .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                    .thenReturn(fileInfoVO)
                    .switchIfEmpty(Mono.just(fileInfoVO));
        } else {
            userInfoMono = Mono.just(fileInfoVO);
        }

        return userInfoMono
                .flatMap(vo -> fileDownloadService.generatePreviewUrl(fileId, 3600, ctx)
                        .doOnNext(vo::setPreviewUrl)
                        .then(fileDownloadService.generateDownloadUrl(fileId, 3600, ctx))
                        .doOnNext(vo::setDownloadUrl)
                        .thenReturn(vo));
    }

    /**
     * 夹取分页大小
     *
     * @param size 客户端传入的每页条数
     * @return 落在 [1, MAX_PAGE_SIZE] 的条数
     */
    private int clampPageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 构造空搜索结果（无登录态时用）
     */
    private Map<String, Object> emptySearchResult(FileSearchDTO searchDTO) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", Collections.emptyList());
        result.put("page", 1);
        result.put("size", DEFAULT_PAGE_SIZE);
        result.put("total", 0L);
        result.put("totalPages", 0);
        return result;
    }
}
