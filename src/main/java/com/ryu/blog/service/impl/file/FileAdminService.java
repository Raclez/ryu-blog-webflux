package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件管理服务
 *
 * <p>承载文件删除、批量删除，以及分组 / 用户 / 类型 / 全量等列表查询。
 *
 * <p><b>可见性与范围</b>：所有列表接口都按操作者收敛。
 * 非管理员只能看到自己的文件（{@code creator_id} 由服务端强制覆盖），
 * 管理员才能查全站或按 {@code creatorId} 查别人。
 * 判定规则见 {@link FileAccessPolicy}。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAdminService {

    /**
     * 单次列表查询返回的最大条数。
     * <p>
     * 用户文件 / 按类型文件这类查询没有分页参数，
     * 不加限制的话会一次性把整张表拉进内存。
     */
    private static final int MAX_LIST_SIZE = 500;

    /**
     * 分页大小上限。
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageStrategyRegistry strategyRegistry;
    private final FileMetadataService fileMetadataService;
    private final FileDownloadService fileDownloadService;

    /**
     * 删除文件（逻辑删除 + 删除存储对象）
     *
     * <p><b>顺序很关键</b>：先把数据库记录标记为已删除，<b>再</b>去删存储对象。
     * 反过来做（先删对象）的话，一旦数据库写入失败，
     * 就会留下一条「记录还在、文件已经没了」的僵尸记录 ——
     * 用户点开就是 404，而管理员也无从判断它到底该不该存在。
     *
     * <p>反过来，存储对象删除失败时<b>不</b>让整个请求失败：
     * 此时数据库记录已经消失，用户看不到这个文件，
     * 只是在存储桶里留下一个无人引用的孤儿对象（可由运维定期清理）。
     * 「看不见的孤儿」远好于「看得见的坏链接」。
     *
     * @param fileId 文件ID
     */
    @Transactional
    public Mono<Void> deleteFile(Long fileId, OperatorContext ctx) {
        log.info("处理文件删除请求: fileId={}", fileId);

        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMap(file -> {
                    FileAccessPolicy.requireCanDelete(file, ctx);

                    // 1. 先落库：标记为已删除
                    file.setIsDeleted(true);
                    file.setUpdateTime(LocalDateTime.now());
                    return fileRepository.save(file);
                })
                // 2. 再删存储对象。失败就向上抛 —— 这个方法在事务里，
                // 抛出会让第 1 步的「软删」一并回滚：
                //   · 不会出现「记录说删了、对象还在存储里」的假成功；
                //   · 删除保持幂等，用户重试不会撞上 404。
                // 早先这里是「只 warn 然后当成成功」，那正是本任务要消掉的假成功。
                .flatMap(file -> strategyRegistry.getStrategy(file.getStorageType())
                        .flatMap(strategy -> strategy.deleteFile(file.getFilePath()))
                        .flatMap(deleted -> {
                            if (!Boolean.TRUE.equals(deleted)) {
                                log.warn("存储对象删除失败，回滚软删标记: fileId={}, path={}",
                                        fileId, file.getFilePath());
                                return Mono.error(new BusinessException(
                                        "删除存储对象失败: " + file.getFilePath()));
                            }
                            log.info("存储对象删除成功: fileId={}, path={}", fileId, file.getFilePath());
                            return Mono.<Void>empty();
                        }))
                .doOnSuccess(v -> log.info("文件删除成功: fileId={}", fileId))
                .doOnError(error -> log.error("文件删除失败: fileId={}", fileId, error));
    }

    /**
     * 批量删除文件
     *
     * @param fileIds 文件ID列表
     * @return 删除结果，key为fileId，value为是否成功
     */
    @Transactional
    public Mono<Map<Long, Boolean>> batchDeleteFiles(List<Long> fileIds, OperatorContext ctx) {
        log.info("批量删除文件: fileIds={}", fileIds);

        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        Map<Long, Boolean> results = new ConcurrentHashMap<>();

        // 对每个文件ID执行删除操作
        // ⚠️ 必须用 concatMap（串行），不能用 flatMap（并发）：
        // 本方法带 @Transactional，整条链绑定在同一条 R2DBC 连接上；
        // r2dbc-mysql 1.4.0 每条连接只有一个 SPSC 的 RequestQueue 且 submit() 无锁，
        // 并发提交会误判队满并抛 `Request queue is full`，
        // 结果是「删了一部分、剩下的没删掉」。
        return Flux.fromIterable(fileIds)
                .concatMap(fileId ->
                    deleteFile(fileId, ctx)
                    .thenReturn(true)
                    .doOnNext(success -> results.put(fileId, true))
                    .onErrorResume(e -> {
                        log.warn("批量删除中的文件处理失败: fileId={}", fileId, e);
                        results.put(fileId, false);
                        return Mono.just(false);
                    })
                )
                .then(Mono.just(results));
    }

    /**
     * 获取用户的文件列表
     *
     * @param userId 用户ID
     */
    public Flux<FileInfoVO> getUserFiles(Long userId, OperatorContext ctx) {
        // 非管理员只能看自己的文件：请求里传的用户ID一律按当前登录用户处理
        Long targetUserId = userId;
        if (ctx == null || !ctx.canManageOthers()) {
            targetUserId = ctx == null ? null : ctx.userId();
            if (targetUserId == null) {
                return Flux.empty();
            }
        }
        final Long effectiveUserId = targetUserId;

        log.info("获取用户文件列表: userId={}", effectiveUserId);

        // 查询用户的文件列表
        return fileRepository.findByCreatorIdAndIsDeleted(effectiveUserId, 0)
                .take(MAX_LIST_SIZE)
                .flatMap(fileMetadataService::buildFileInfoVO)
                // 获取用户信息
                .flatMap(fileInfoVO ->
                    userRepository.findById(effectiveUserId)
                        .doOnNext(user -> fileInfoVO.setCreatorName(user.getUsername()))
                        .thenReturn(fileInfoVO)
                )
                // 生成预览和下载链接
                .flatMap(fileInfoVO ->
                    fileDownloadService.generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(fileDownloadService.generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .doOnComplete(() -> log.info("获取用户文件列表完成: userId={}", effectiveUserId))
                .doOnError(error -> log.error("获取用户文件列表失败: userId={}", effectiveUserId, error));
    }

    /**
     * 根据类型获取文件列表
     *
     * @param type 文件类型
     */
    public Flux<FileInfoVO> getFilesByType(String type, OperatorContext ctx) {
        log.info("根据类型获取文件列表: type={}", type);

        // 管理员看全部；非管理员只看自己的文件（不管它是不是公开文件）
        final boolean allVisible = ctx != null && ctx.canManageOthers();
        final Long currentUserId = ctx == null ? null : ctx.userId();

        // 查询指定类型的文件列表
        return fileRepository.findByFileTypeAndIsDeleted(type, 0)
                .take(MAX_LIST_SIZE)
                .filter(file -> allVisible
                        || (currentUserId != null && currentUserId.equals(file.getCreatorId())))
                .flatMap(fileMetadataService::buildFileInfoVO)
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
                    fileDownloadService.generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(fileDownloadService.generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .doOnComplete(() -> log.info("根据类型获取文件列表完成: type={}", type))
                .doOnError(error -> log.error("根据类型获取文件列表失败: type={}", type, error));
    }

    /**
     * 分页获取文件列表
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     */
    public Mono<Map<String, Object>> getFileList(int page, int size, OperatorContext ctx) {
        log.info("分页获取文件列表: page={}, size={}", page, size);

        // 计算分页参数（size 来自 Controller，但仍然夹取一次：除零会炸掉总页数计算）
        // final 副本：page/size/offset 会被后面的 lambda 捕获，必须是不可变引用
        final int safePage = Math.max(1, page);
        final int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        final int offset = (safePage - 1) * safeSize;

        // 复用搜索片段：这样「按创建者收敛」的逻辑只有一份实现
        FileSearchDTO query = new FileSearchDTO();
        query.setCurrent(safePage);
        query.setSize(safeSize);
        query.setOrderBy("createTime");
        query.setOrderDirection("desc");
        // 非管理员：强制只看自己的文件
        if (ctx == null || !ctx.canManageOthers()) {
            if (ctx == null || ctx.userId() == null) {
                return Mono.just(emptyFileList(safePage, safeSize));
            }
            query.setCreatorId(ctx.userId());
        }

        // 查询文件列表
        return fileRepository.searchFiles(query, safeSize, offset)
                .flatMap(fileMetadataService::buildFileInfoVO)
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
                    fileDownloadService.generatePreviewUrl(fileInfoVO.getId(), 3600)
                        .doOnNext(fileInfoVO::setPreviewUrl)
                        .then(fileDownloadService.generateDownloadUrl(fileInfoVO.getId(), 3600))
                        .doOnNext(fileInfoVO::setDownloadUrl)
                        .thenReturn(fileInfoVO)
                )
                .collectList()
                .zipWith(fileRepository.countFiles(query))
                .map(tuple -> {
                    List<FileInfoVO> files = tuple.getT1();
                    Long total = tuple.getT2();

                    Map<String, Object> result = new HashMap<>();
                    result.put("content", files);
                    result.put("page", safePage);
                    result.put("size", safeSize);
                    result.put("total", total);
                    result.put("totalPages", (total + safeSize - 1) / safeSize);

                    return result;
                })
                .doOnSuccess(result -> log.info("分页获取文件列表成功: page={}, size={}, total={}",
                        safePage, safeSize, result.get("total")))
                .doOnError(error -> log.error("分页获取文件列表失败: page={}, size={}", safePage, safeSize, error));
    }

    /**
     * 构造空列表结果（无登录态时用）
     */
    private Map<String, Object> emptyFileList(int page, int size) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", Collections.emptyList());
        result.put("page", page);
        result.put("size", size);
        result.put("total", 0L);
        result.put("totalPages", 0);
        return result;
    }
}
