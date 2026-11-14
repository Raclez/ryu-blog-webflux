package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.FileUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.service.FileService;
import com.ryu.blog.service.impl.file.FileAdminService;
import com.ryu.blog.service.impl.file.FileDownloadService;
import com.ryu.blog.service.impl.file.FileMetadataService;
import com.ryu.blog.service.impl.file.FileUploadService;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.utils.SaTokenUtils;
import com.ryu.blog.vo.FileDownloadVO;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileUploadVO;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件服务门面 —— 对外的唯一入口，内部按职责拆成四个服务：
 *
 * <ul>
 *   <li>{@link FileUploadService} —— 单文件 / 批量 / 分片上传与重复文件处理</li>
 *   <li>{@link FileDownloadService} —— 下载、预览、公开直链</li>
 *   <li>{@link FileMetadataService} —— 元数据、版本、搜索、批量 URL</li>
 *   <li>{@link FileAdminService} —— 后台列表、删除、分组</li>
 * </ul>
 *
 * <p>缓存注解留在这一层：门面负责失效，内层只关心业务逻辑。
 * 缓存键里必须带操作者身份（{@code currentUserIdOrNull} + {@code currentCanManageOthers}），
 * 否则 A 用户会读到 B 用户的缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileUploadService fileUploadService;
    private final FileDownloadService fileDownloadService;
    private final FileMetadataService fileMetadataService;
    private final FileAdminService fileAdminService;

    // ------------------------------------------------------------------
    // 上传
    // ------------------------------------------------------------------

    @Override
    public Mono<Boolean> isFileAllowed(String filename, long size) {
        return fileUploadService.isFileAllowed(filename, size);
    }

    @Override
    public Mono<FileUploadVO> handleFileUpload(FilePart filePart, UploadOptionsDTO options, Long uploaderId) {
        return fileUploadService.handleFileUpload(filePart, options, uploaderId);
    }

    @Override
    public Flux<FileUploadVO> handleBatchFileUpload(Flux<FilePart> fileParts, UploadOptionsDTO options, Long uploaderId) {
        return fileUploadService.handleBatchFileUpload(fileParts, options, uploaderId);
    }

    @Override
    public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, UploadOptionsDTO options, Long uploaderId) {
        return fileUploadService.initiateMultipartUpload(fileName, fileSize, options, uploaderId);
    }

    @Override
    public Mono<String> uploadPart(String uploadId, int partNumber, byte[] partData, Long uploaderId) {
        return fileUploadService.uploadPart(uploadId, partNumber, partData, uploaderId);
    }

    @Override
    public Mono<FileUploadVO> completeMultipartUpload(String uploadId, List<String> partETags, UploadOptionsDTO options, Long uploaderId) {
        return fileUploadService.completeMultipartUpload(uploadId, partETags, options, uploaderId);
    }

    @Override
    public Mono<Boolean> abortMultipartUpload(String uploadId, Long uploaderId) {
        return fileUploadService.abortMultipartUpload(uploadId, uploaderId);
    }

    // ------------------------------------------------------------------
    // 下载
    // ------------------------------------------------------------------

    @Override
    public Mono<FileDownloadVO> handleFileDownload(Long fileId) {
        return fileDownloadService.downloadById(fileId);
    }

    @Override
    public Mono<FileDownloadVO> handlePublicFileAccess(String path) {
        return fileDownloadService.downloadPublicByPath(path);
    }


    // 注意：这两个方法**不能缓存**。
    // 缓存键里没有 expireSeconds，而生成出来的 URL 是带过期时间的签名令牌；
    // FILE_CACHE 的 TTL 一旦长于 URL 的 expireSeconds，缓存里就会留下已失效的 URL，
    // 前端表现为「间歇性 403」。批量方法内部固定用 3600 秒，可以安全缓存。
    @Override
    public Mono<String> generatePreviewUrl(Long fileId, long expireSeconds) {
        return fileDownloadService.generatePreviewUrl(fileId, expireSeconds);
    }

    @Override
    public Mono<String> generateDownloadUrl(Long fileId, long expireSeconds) {
        return fileDownloadService.generateDownloadUrl(fileId, expireSeconds);
    }

    // ------------------------------------------------------------------
    // 元数据
    // ------------------------------------------------------------------

    @Override
    @Cacheable(cacheNames = CacheConstants.FILE_CACHE,
            key = "'" + CacheConstants.FILE_INFO_KEY + "' + #fileId"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull()"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result == null")
    public Mono<FileInfoVO> getFileInfo(Long fileId) {
        return fileMetadataService.getFileInfo(fileId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.FILE_CACHE, allEntries = true)
    public Mono<FileInfoVO> updateFileInfo(Long fileId, FileUpdateDTO fileUpdateDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return fileMetadataService.updateFileInfo(fileId, fileUpdateDTO, ctx);
        });
    }

    @Override
    public Mono<Map<String, Object>> searchFiles(FileSearchDTO searchDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return fileMetadataService.searchFiles(searchDTO, ctx);
        });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.FILE_CACHE,
            key = "'" + CacheConstants.FILE_INFOS_KEY + "' + T(com.ryu.blog.utils.CacheKeyUtils).ofIds(#fileIds)"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull()"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result.isEmpty()")
    public Mono<Map<Long, FileInfoVO>> getBatchFileInfos(List<Long> fileIds) {
        return fileMetadataService.getBatchFileInfos(fileIds);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.FILE_CACHE,
            key = "'" + CacheConstants.FILE_PERMANENT_URLS_KEY + "' + T(com.ryu.blog.utils.CacheKeyUtils).ofIds(#fileIds)",
            unless = "#result.isEmpty()")
    public Mono<Map<Long, String>> getBatchFilePermanentUrls(List<Long> fileIds) {
        return fileMetadataService.getBatchFilePermanentUrls(fileIds);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.FILE_CACHE,
            key = "'" + CacheConstants.FILE_URLS_KEY + "' + T(com.ryu.blog.utils.CacheKeyUtils).ofIds(#fileIds)"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull()"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result.isEmpty()")
    public Mono<Map<Long, String>> getBatchFileUrls(List<Long> fileIds) {
        return fileMetadataService.getBatchFileUrls(fileIds);
    }

    // ------------------------------------------------------------------
    // 管理与列表
    // ------------------------------------------------------------------

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.FILE_CACHE, allEntries = true)
    public Mono<Void> handleFileDelete(Long fileId) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return fileAdminService.deleteFile(fileId, ctx);
        });
    }

    @Override
    @Transactional
    public Mono<Map<Long, Boolean>> batchDeleteFiles(List<Long> fileIds) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return fileAdminService.batchDeleteFiles(fileIds, ctx);
        });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.FILE_CACHE,
            key = "'" + CacheConstants.FILE_USER_KEY + "' + #userId"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull()"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result == null")
    public Flux<FileInfoVO> getUserFiles(Long userId) {
        // 身份在组装期取一次：缓存 key 也是在组装期算的（SpEL 只能同步取值），
        // 两者必须同源同时刻，否则就是「A 的可见性判定配 B 的缓存 key」。
        OperatorContext ctx = SaTokenUtils.currentOperatorContext();
        return Flux.defer(() -> fileAdminService.getUserFiles(userId, ctx));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.FILE_CACHE,
            key = "'" + CacheConstants.FILE_TYPE_KEY + "' + #type"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull()"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result == null")
    public Flux<FileInfoVO> getFilesByType(String type) {
        // 同 getUserFiles：判定与缓存 key 必须同源。
        OperatorContext ctx = SaTokenUtils.currentOperatorContext();
        return Flux.defer(() -> fileAdminService.getFilesByType(type, ctx));
    }

    @Override
    public Mono<Map<String, Object>> getFileList(int page, int size) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return fileAdminService.getFileList(page, size, ctx);
        });
    }
}
