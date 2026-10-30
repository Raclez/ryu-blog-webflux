package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.FileUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.service.FileService;
import com.ryu.blog.utils.FileUtils;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileUploadVO;
import com.ryu.blog.vo.FileVersionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 文件上传下载接口
 *
 * <p><b>三层鉴权</b>（与文章、评论模块同构）：
 * <ol>
 *   <li><b>能力校验</b> —— {@code @SaCheckPermission}：
 *       上传要 {@code system:file:upload}，查看要 {@code system:file:view}，
 *       改元数据要 {@code system:file:edit}，删除要 {@code system:file:remove}；</li>
 *   <li><b>归属校验</b> —— Service 层 {@code FileAccessPolicy}：
 *       改 / 删只有创建者本人（或管理员）可以；</li>
 *   <li><b>可见性</b> —— 公开文件谁都能读，私有文件只有主人与管理员能读。</li>
 * </ol>
 *
 * <p>早先这 18 个端点<b>一个能力校验都没有</b>，只有「必须登录」挡着 ——
 * 于是任何注册用户都能删掉别人的文件、读别人的私有文件。
 *
 * @author ryu 475118582@qq.com
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "文件管理接口", description = "提供文件的上传、下载、删除等操作")
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件
     * @param filePart 文件对象
     * @param options 上传选项
     * @return 文件信息
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(PermissionConstants.FILE_UPLOAD)
    @Operation(summary = "上传单个文件", description = "上传单个文件并返回文件信息")
    public Mono<Result<FileUploadVO>> upload(
            @RequestPart("file") FilePart filePart,
            UploadOptionsDTO options) {

        log.info("上传文件: {}, 选项: {}", filePart.filename(), options);

        // 上传者身份只能从登录态取；不能放进 UploadOptionsDTO（表单字段客户端可改）
        Long uploaderId = StpUtil.getLoginIdAsLong();
        return fileService.handleFileUpload(filePart, options, uploaderId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("文件上传成功: {}", filePart.filename()));
    }

    /**
     * 批量上传文件
     * @param fileParts 文件对象
     * @param options 上传选项
     * @return 文件信息列表
     */
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(PermissionConstants.FILE_UPLOAD)
    @Operation(summary = "批量上传文件", description = "批量上传多个文件并返回文件信息列表")
    public Mono<Result<List<FileUploadVO>>> batchUpload(
            @RequestPart("files") Flux<FilePart> fileParts,
            UploadOptionsDTO options) {

        log.info("批量上传文件, 选项: {}", options);

        Long uploaderId = StpUtil.getLoginIdAsLong();
        return fileService.handleBatchFileUpload(fileParts, options, uploaderId)
                .collectList()
                .map(Result::success)
                .doOnSuccess(result -> log.info("批量文件上传成功: 数量={}", result.getData().size()));
    }

    /**
     * 初始化分片上传
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param options 上传选项
     * @return 上传信息
     */
    @PostMapping("/upload/multipart/init")
    @SaCheckPermission(PermissionConstants.FILE_UPLOAD)
    @Operation(summary = "初始化分片上传", description = "初始化大文件分片上传")
    public Mono<Result<Map<String, String>>> initiateMultipartUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") long fileSize,
            UploadOptionsDTO options) {

        log.info("初始化分片上传: fileName={}, fileSize={}, options={}", fileName, fileSize, options);

        Long uploaderId = StpUtil.getLoginIdAsLong();
        return fileService.initiateMultipartUpload(fileName, fileSize, options, uploaderId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("初始化分片上传成功: fileName={}", fileName));
    }

    /**
     * 上传分片
     * @param uploadId 上传ID
     * @param partNumber 分片号
     * @param partData 分片数据
     * @return 分片标识
     */
    @PostMapping(value = "/upload/multipart/part", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @SaCheckPermission(PermissionConstants.FILE_UPLOAD)
    @Operation(summary = "上传文件分片", description = "上传分片数据")
    public Mono<Result<String>> uploadPart(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestBody byte[] partData) {

        log.info("上传分片: uploadId={}, partNumber={}, size={}", uploadId, partNumber, partData.length);

        Long uploaderId = StpUtil.getLoginIdAsLong();
        return fileService.uploadPart(uploadId, partNumber, partData, uploaderId)
                .map(Result::success)
                .doOnSuccess(result -> log.debug("上传分片成功: uploadId={}, partNumber={}", uploadId, partNumber));
    }

    /**
     * 完成分片上传
     * @param uploadId 上传ID
     * @param partETags 分片标识列表
     * @param options 上传选项
     * @return 上传结果
     */
    @PostMapping("/upload/multipart/complete")
    @SaCheckPermission(PermissionConstants.FILE_UPLOAD)
    @Operation(summary = "完成分片上传", description = "完成分片上传并合并文件")
    public Mono<Result<FileUploadVO>> completeMultipartUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestBody List<String> partETags,
            UploadOptionsDTO options) {

        log.info("完成分片上传: uploadId={}, partCount={}", uploadId, partETags.size());

        Long uploaderId = StpUtil.getLoginIdAsLong();
        return fileService.completeMultipartUpload(uploadId, partETags, options, uploaderId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("完成分片上传成功: uploadId={}", uploadId));
    }

    /**
     * 终止分片上传
     * @param uploadId 上传ID
     * @return 是否成功
     */
    @DeleteMapping("/upload/multipart/{uploadId}")
    @SaCheckPermission(PermissionConstants.FILE_UPLOAD)
    @Operation(summary = "终止分片上传", description = "终止分片上传并清理临时文件")
    public Mono<Result<Boolean>> abortMultipartUpload(@PathVariable("uploadId") String uploadId) {
        log.info("终止分片上传: uploadId={}", uploadId);

        Long uploaderId = StpUtil.getLoginIdAsLong();
        return fileService.abortMultipartUpload(uploadId, uploaderId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("终止分片上传成功: uploadId={}", uploadId));
    }

    /**
     * 删除文件
     * @param id 文件ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    @SaCheckPermission(PermissionConstants.FILE_REMOVE)
    @Operation(summary = "删除文件", description = "根据文件ID删除文件")
    public Mono<Result<Void>> delete(@PathVariable("id") Long id) {
        log.info("删除文件: id={}", id);

        return fileService.handleFileDelete(id, OperatorContext.current())
                .then(Mono.just(Result.<Void>success()))
                .doOnSuccess(result -> log.info("文件删除成功: id={}", id));
    }

    /**
     * 批量删除文件
     * @param ids 文件ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    @SaCheckPermission(PermissionConstants.FILE_REMOVE)
    @Operation(summary = "批量删除文件", description = "批量删除多个文件")
    public Mono<Result<Map<Long, Boolean>>> batchDelete(@RequestBody List<Long> ids) {
        log.info("批量删除文件: ids={}", ids);

        return fileService.batchDeleteFiles(ids, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.info("批量删除文件成功: 数量={}", ids.size()));
    }

    /**
     * 下载文件
     * @param id 文件ID
     * @return 文件资源
     */
    @GetMapping("/download/{id}")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "下载文件", description = "根据文件ID下载文件")
    public Mono<ResponseEntity<Flux<DataBuffer>>> download(@PathVariable("id") Long id) {
        log.info("下载文件: id={}", id);
        return fileService.handleFileDownload(id, OperatorContext.current())
                .map(download -> ResponseEntity.ok()
                        .contentType(resolveMediaType(download.getContentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                contentDisposition("attachment", download.getFileName()))
                        // 禁止浏览器按内容猜测 MIME：上传内容不能被当作文档执行
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_LENGTH,
                                download.getContentLength() != null ? download.getContentLength().toString() : "0")
                        .body(download.getContent()));
    }

    /**
     * 预览文件
     * @param id 文件ID
     * @return 文件资源
     */
    @GetMapping("/preview/{id}")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "预览文件", description = "根据文件ID预览文件，支持图片直接查看")
    public Mono<ResponseEntity<Flux<DataBuffer>>> preview(@PathVariable("id") Long id) {
        log.info("预览文件: id={}", id);

        return fileService.handleFileDownload(id, OperatorContext.current())
                .map(download -> ResponseEntity.ok()
                        .contentType(resolveMediaType(download.getContentType()))
                        // 修改Content-Disposition为inline，使浏览器尝试直接预览
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        // 预览是唯一「内联渲染上传内容」的入口，必须假设内容不可信：
                        // nosniff 禁止 MIME 嗅探；CSP sandbox 让内联 HTML/SVG 无法执行脚本、
                        // 无法访问同源 Cookie/存储，即使以后放开 html/svg 也不会变成存储型 XSS。
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "sandbox")
                        .header(HttpHeaders.CONTENT_LENGTH,
                                download.getContentLength() != null ? download.getContentLength().toString() : "0")
                        .body(download.getContent()));
    }

    /**
     * 解析媒体类型，数据库里的 MIME 可能是脏数据，解析失败时退回二进制流
     *
     * @param contentType MIME 类型
     * @return 媒体类型
     */
    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            log.warn("非法的 MIME 类型，回退为二进制流: {}", contentType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 构造 {@code Content-Disposition} 响应头
     *
     * <p>文件名来自数据库，而文件名可以被客户端通过「编辑文件信息」接口改 ——
     * 如果原样拼进响应头，一个带引号或换行的文件名就构成
     * <b>响应头注入</b>（{@code Content-Disposition: attachment; filename="x"\r\nX-Evil: 1}）。
     *
     * <p>这里做两件事：
     * <ol>
     *   <li>文件名先消毒（去分隔符、控制字符、截断长度）；</li>
     *   <li>按 RFC 5987 附上 {@code filename*=UTF-8''<percent-encoded>}，
     *       非 ASCII 文件名才不会乱码。</li>
     * </ol>
     *
     * @param disposition {@code attachment} 或 {@code inline}
     * @param fileName    文件名
     * @return Content-Disposition 头的值
     */
    private String contentDisposition(String disposition, String fileName) {
        String safeName = FileUtils.sanitizeFileName(fileName);
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + "; filename=\"" + safeName + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 获取文件预览URL
     * @param id 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 预览URL
     */
    @GetMapping("/url/{id}")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "获取文件预览URL", description = "获取文件的预览URL")
    public Mono<Result<String>> getPreviewUrl(
            @PathVariable("id") Long id,
            @RequestParam(value = "expires", defaultValue = "3600") long expireSeconds) {

        log.info("获取文件预览URL: id={}, expires={}s", id, expireSeconds);

        return fileService.generatePreviewUrl(id, expireSeconds, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.debug("获取预览URL成功: id={}", id));
    }

    /**
     * 获取文件下载URL
     * @param id 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 下载URL
     */
    @GetMapping("/download/url/{id}")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "获取文件下载URL", description = "获取文件的下载URL")
    public Mono<Result<String>> getDownloadUrl(
            @PathVariable("id") Long id,
            @RequestParam(value = "expires", defaultValue = "3600") long expireSeconds) {

        log.info("获取文件下载URL: id={}, expires={}s", id, expireSeconds);

        return fileService.generateDownloadUrl(id, expireSeconds, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.debug("获取下载URL成功: id={}", id));
    }

    /**
     * 获取文件详情
     * @param id 文件ID
     * @return 文件详情
     */
    @GetMapping("/detail/{id}")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "获取文件详情", description = "获取文件的详细信息")
    public Mono<Result<FileInfoVO>> getFileInfo(@PathVariable("id") Long id) {
        log.info("获取文件详情: id={}", id);

        return fileService.getFileInfo(id, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.debug("获取文件详情成功: id={}", id));
    }

    /**
     * 更新文件信息
     * @param id 文件ID
     * @param filesDTO 文件信息
     * @return 更新后的文件信息
     */
    @PutMapping("/edit/{id}")
    @SaCheckPermission(PermissionConstants.FILE_EDIT)
    @Operation(summary = "更新文件信息", description = "更新文件的基本信息")
    public Mono<Result<FileInfoVO>> updateFileInfo(
            @PathVariable("id") Long id,
            @Valid @RequestBody FileUpdateDTO fileUpdateDTO) {

        log.info("更新文件信息: id={}, data={}", id, fileUpdateDTO);

        return fileService.updateFileInfo(id, fileUpdateDTO, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.info("更新文件信息成功: id={}", id));
    }

    /**
     * 获取文件版本历史
     * @param id 文件ID
     * @return 版本历史列表
     */
    @GetMapping("/{id}/versions")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "获取文件版本历史", description = "获取文件的所有版本历史")
    public Mono<Result<List<FileVersionVO>>> getFileVersions(@PathVariable("id") Long id) {
        log.info("获取文件版本历史: id={}", id);

        return fileService.getFileVersions(id, OperatorContext.current())
                .collectList()
                .map(Result::success)
                .doOnSuccess(result -> log.debug("获取文件版本历史成功: id={}, 版本数={}", id, result.getData().size()));
    }

    /**
     * 搜索文件
     * @param searchDTO 搜索参数
     * @return 搜索结果
     */
    @GetMapping("/search")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "搜索文件", description = "根据条件搜索文件")
    public Mono<Result<Map<String, Object>>> searchFiles(FileSearchDTO searchDTO) {
        log.info("搜索文件: {}", searchDTO);

        return fileService.searchFiles(searchDTO, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.debug("搜索文件成功"));
    }

    /**
     * 公开文件直链
     *
     * <p><b>为什么需要这条路由</b>：{@code <img src>} 带不了 {@code Authorization} 头，
     * 而按 ID 的路由（{@code /files/preview/{id}}）每次访问都要
     * {@code system:file:view} 权限 + 请求头 —— 博客正文里的图根本渲染不出来。
     * 存储策略 {@code getPublicUrl} 在没有配置对象存储访问地址时（本地存储），
     * 兜底地址就指向这里。
     *
     * <p><b>它是匿名的，但应用内必须再判一次可见性</b>：路由白名单只保证
     * 「不用登录」，不保证「这个文件可以公开看」。判定在
     * {@code FileDownloadService#downloadPublicByPath} 里 ——
     * {@code access_type != 0} 一律 403。
     *
     * <p>响应头与预览接口一致：{@code nosniff} 禁止 MIME 嗅探，
     * {@code CSP: sandbox} 让内联 HTML/SVG 无法执行脚本。
     *
     * @param path 存储路径
     * @return 文件资源
     */
    @GetMapping("/public/{*path}")
    @Operation(summary = "公开文件访问", description = "按存储路径访问公开文件（仅限 access_type=0）")
    public Mono<ResponseEntity<Flux<DataBuffer>>> publicFile(@PathVariable("path") String path) {
        log.info("访问公开文件: path={}", path);

        return fileService.handlePublicFileAccess(path)
                .map(download -> ResponseEntity.ok()
                        .contentType(resolveMediaType(download.getContentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "sandbox")
                        .header(HttpHeaders.CONTENT_LENGTH,
                                download.getContentLength() != null ? download.getContentLength().toString() : "0")
                        .body(download.getContent()));
    }

    /**
     * 批量获取文件URL
     * @param fileIds 文件ID列表
     * @return 文件URL映射
     */
    @PostMapping("/batch/urls")
    @SaCheckPermission(PermissionConstants.FILE_VIEW)
    @Operation(summary = "批量获取文件URL", description = "根据文件ID列表批量获取文件URL")
    public Mono<Result<Map<Long, String>>> getBatchUrls(@RequestBody List<Long> fileIds) {
        log.info("批量获取文件URL: fileIds={}", fileIds);

        return fileService.getBatchFileUrls(fileIds, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.debug("批量获取文件URL成功: 数量={}", fileIds.size()));
    }

}
