package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.vo.FileDownloadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 文件下载服务
 *
 * <p>承载按 ID / 路径下载、文件流获取，以及预览与下载链接的签发。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final FileRepository fileRepository;
    private final StorageStrategyRegistry strategyRegistry;

    /**
     * 按文件ID下载
     * <p>
     * 内容以流的形式返回，文件不会整体驻留内存。
     *
     * @param fileId 文件ID
     * @return 文件下载结果
     */
    public Mono<FileDownloadVO> downloadById(Long fileId) {
        log.info("处理文件下载请求: fileId={}", fileId);

        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMap(file -> {
                    return strategyRegistry.getStrategy(file.getStorageType())
                            .flatMap(strategy -> strategy.downloadFile(file.getFilePath())
                                    .map(dataBufferFlux -> FileDownloadVO.builder()
                                            .content(dataBufferFlux)
                                            .fileName(file.getFileName())
                                            .contentType(file.getMimeType() != null
                                                    ? file.getMimeType()
                                                    : FileUtils.getContentType(file.getFileName()))
                                            .contentLength(file.getFileSize())
                                            .disposition("attachment")
                                            .build()))
                            ;
                });
    }

    /**
     * 按存储路径取<b>公开文件</b>
     *
     * <p>这是应用内公开直链 {@code GET /files/public/{path}} 的后端实现。
     * 之所以需要它：{@code <img src>} 带不了 {@code Authorization} 头，
     * 而按 ID 的路由（{@code /files/preview/{id}}）每次访问都要
     * {@code system:file:view} 权限 + 请求头 —— 博客正文里的图根本渲染不出来。
     *
     * <p>这条路由匿名可达，且<b>不再做可见性判定</b>：文件可见性已下线
     * （对象存储桶本身匿名可读，那个字段只制造错觉）。它服务的正是
     * {@code <img src>} 这类带不了 {@code Authorization} 头的场景。
     *
     * @param path 存储路径
     * @return 文件内容
     */
    public Mono<FileDownloadVO> downloadPublicByPath(String path) {
        log.info("处理公开文件访问请求: path={}", path);

        // 路由是 GET /files/public/{*path}，而 Spring 的 {*path}（CaptureTheRest）
        // 捕获到的值<b>带前导斜杠</b>：/files/public/images/2026/a.png → /images/2026/a.png。
        // 库里 file_path 存的是 normalizePath 之后的 images/2026/a.png。
        // 不归一化的话公开直链永远查不到文件 —— 文章里的图片全部 404，
        // 而按 ID 的下载/预览是好的，本地验证时很容易漏掉。
        String normalizedPath = FileUtils.normalizePath(path);

        return fileRepository.findByFilePathAndIsDeleted(normalizedPath, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除: " + normalizedPath)))
                .flatMap(file -> {
                    return strategyRegistry.getStrategy(file.getStorageType())
                            .flatMap(strategy -> strategy.downloadFile(file.getFilePath())
                                    .map(dataBufferFlux -> FileDownloadVO.builder()
                                            .content(dataBufferFlux)
                                            .fileName(file.getFileName())
                                            .contentType(file.getMimeType() != null
                                                    ? file.getMimeType()
                                                    : FileUtils.getContentType(file.getFileName()))
                                            .contentLength(file.getFileSize())
                                            .disposition("inline")
                                            .build()));
                })
                .doOnSuccess(download -> log.info("公开文件访问成功: path={}", path))
                .doOnError(error -> log.error("公开文件访问失败: path={}", path, error));
    }

    /**
     * 按存储路径下载（整文件读入内存）
     *
     * <p><b>注意</b>：这是按路径精确查库的内部方法，不做归属判定 ——
     * 调用方必须已经确认过这个路径对应的文件对该操作者可见。
     */
    public Mono<byte[]> downloadByPath(String path) {
        log.info("通过路径处理文件下载请求: path={}", path);

        return findByFilePath(path, 0)
                .flatMap(file -> strategyRegistry.getStrategy(file.getStorageType())
                        .flatMap(strategy -> strategy.downloadFile(file.getFilePath())
                                .flatMap(dataBufferFlux -> DataBufferUtils.join(dataBufferFlux)
                                        .map(dataBuffer -> {
                                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                            dataBuffer.read(bytes);
                                            DataBufferUtils.release(dataBuffer);
                                            return Tuples.of(file, bytes);
                                        })
                                        .map(tuple -> tuple.getT2()))))
                .doOnSuccess(bytes -> log.info("通过路径下载文件成功: path={}, size={}", path, bytes.length))
                .doOnError(error -> log.error("通过路径下载文件失败: path={}", path, error));
    }

    /**
     * 生成预览URL
     *
     * <p><b>返回的是应用内的按 ID 路由</b>（{@code /files/preview/{id}}），
     * 而不是存储层的直链。理由有两条：
     * <ul>
     *   <li>直链绕过了应用层的可见性判定 —— 私有文件一旦发出去就等于公开；</li>
     *   <li>本地存储策略根本没有可直链的公开地址
     *       （{@code getPublicUrl} 兜底到 {@code /api/files/public/**}，那个路由并不存在）。</li>
     * </ul>
     * 按 ID 的路由对所有存储后端都成立，且每次访问都会重新做一次可见性判定。
     *
     * @param fileId        文件ID
     * @param expireSeconds 过期时间（秒）—— 保留入参是为了兼容既有契约，
     *                      应用内路由本身不带过期语义
     * @return 预览URL
     */
    public Mono<String> generatePreviewUrl(Long fileId, long expireSeconds) {
        log.info("生成文件预览URL: fileId={}, expireSeconds={}", fileId, expireSeconds);

        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMap(file -> Mono.just("/files/preview/" + fileId))
                .doOnSuccess(url -> log.info("生成预览URL成功: fileId={}, url={}", fileId, url))
                .doOnError(error -> log.error("生成预览URL失败: fileId={}", fileId, error));
    }

    /**
     * 生成下载URL
     *
     * <p>与 {@link #generatePreviewUrl} 同理，返回应用内按 ID 的路由。
     *
     * @param fileId        文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 下载URL
     */
    public Mono<String> generateDownloadUrl(Long fileId, long expireSeconds) {
        log.info("生成文件下载URL: fileId={}, expireSeconds={}", fileId, expireSeconds);

        return fileRepository.findByIdAndIsDeleted(fileId, 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除")))
                .flatMap(file -> Mono.just("/files/download/" + fileId))
                .doOnSuccess(url -> log.info("生成下载URL成功: fileId={}, url={}", fileId, url))
                .doOnError(error -> log.error("生成下载URL失败: fileId={}", fileId, error));
    }

    /**
     * 根据文件路径查找文件
     *
     * @param path 文件路径
     * @param isDeleted 是否已删除（0-未删除，1-已删除）
     * @return 文件信息
     */
    private Mono<File> findByFilePath(String path, Integer isDeleted) {
        return fileRepository.findByFilePathAndIsDeleted(path, isDeleted)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("文件不存在或已删除: " + path)));
    }
}
