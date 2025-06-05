package com.ryu.blog.controller;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.FilesDTO;
import com.ryu.blog.dto.ResourceGroupQueryDTO;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.service.FileService;
import com.ryu.blog.service.ResourceGroupService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileUploadVO;
import com.ryu.blog.vo.FileVersionVO;
import com.ryu.blog.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 文件上传下载接口
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
    private final ResourceGroupService resourceGroupService;

    /**
     * 上传文件
     * @param filePart 文件对象
     * @param options 上传选项
     * @return 文件信息
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传单个文件", description = "上传单个文件并返回文件信息")
    public Mono<Result<FileUploadVO>> upload(
            @RequestPart("file") FilePart filePart,
            UploadOptionsDTO options) {
        
        log.info("上传文件: {}, 选项: {}", filePart.filename(), options);
        
        return fileService.handleFileUpload(filePart, options)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("文件上传失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 批量上传文件
     * @param fileParts 文件对象
     * @param options 上传选项
     * @return 文件信息列表
     */
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量上传文件", description = "批量上传多个文件并返回文件信息列表")
    public Mono<Result<List<FileUploadVO>>> batchUpload(
            @RequestPart("files") Flux<FilePart> fileParts,
            UploadOptionsDTO options) {
        
        log.info("批量上传文件, 选项: {}", options);
        
        return fileService.handleBatchFileUpload(fileParts, options)
                .collectList()
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("批量文件上传失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 初始化分片上传
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param options 上传选项
     * @return 上传信息
     */
    @PostMapping("/upload/multipart/init")
    @Operation(summary = "初始化分片上传", description = "初始化大文件分片上传")
    public Mono<Result<Map<String, String>>> initiateMultipartUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") long fileSize,
            UploadOptionsDTO options) {
        
        log.info("初始化分片上传: fileName={}, fileSize={}, options={}", fileName, fileSize, options);
        
        return fileService.initiateMultipartUpload(fileName, fileSize, options)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("初始化分片上传失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 上传分片
     * @param uploadId 上传ID
     * @param partNumber 分片号
     * @param partData 分片数据
     * @return 分片标识
     */
    @PostMapping(value = "/upload/multipart/part", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "上传文件分片", description = "上传分片数据")
    public Mono<Result<String>> uploadPart(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestBody byte[] partData) {
        
        log.info("上传分片: uploadId={}, partNumber={}, size={}", uploadId, partNumber, partData.length);
        
        return fileService.uploadPart(uploadId, partNumber, partData)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("上传分片失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 完成分片上传
     * @param uploadId 上传ID
     * @param partETags 分片标识列表
     * @param options 上传选项
     * @return 上传结果
     */
    @PostMapping("/upload/multipart/complete")
    @Operation(summary = "完成分片上传", description = "完成分片上传并合并文件")
    public Mono<Result<FileUploadVO>> completeMultipartUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestBody List<String> partETags,
            UploadOptionsDTO options) {
        
        log.info("完成分片上传: uploadId={}, partCount={}", uploadId, partETags.size());
        
        return fileService.completeMultipartUpload(uploadId, partETags, options)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("完成分片上传失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 终止分片上传
     * @param uploadId 上传ID
     * @return 是否成功
     */
    @DeleteMapping("/upload/multipart/{uploadId}")
    @Operation(summary = "终止分片上传", description = "终止分片上传并清理临时文件")
    public Mono<Result<Boolean>> abortMultipartUpload(@PathVariable("uploadId") String uploadId) {
        log.info("终止分片上传: uploadId={}", uploadId);
        
        return fileService.abortMultipartUpload(uploadId)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("终止分片上传失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 删除文件
     * @param id 文件ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除文件", description = "根据文件ID删除文件")
    public Mono<Result<Void>> delete(@PathVariable("id") Long id) {
        log.info("删除文件: id={}", id);
        
        return fileService.handleFileDelete(id)
                .then(Mono.just(Result.<Void>success()))
                .onErrorResume(e -> {
                    log.error("文件删除失败", e);
                    return Mono.just(Result.<Void>error(e.getMessage()));
                });
    }
    
    /**
     * 批量删除文件
     * @param ids 文件ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除文件", description = "批量删除多个文件")
    public Mono<Result<Map<Long, Boolean>>> batchDelete(@RequestBody List<Long> ids) {
        log.info("批量删除文件: ids={}", ids);
        
        return fileService.batchDeleteFiles(ids)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("批量删除文件失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 下载文件
     * @param id 文件ID
     * @return 文件资源
     */
    @GetMapping("/download/{id}")
    @Operation(summary = "下载文件", description = "根据文件ID下载文件")
    public Mono<ResponseEntity<Resource>> download(@PathVariable("id") Long id) {
        log.info("下载文件: id={}", id);
        
        return fileService.handleFileDownload(id)
                .onErrorResume(e -> {
                    log.error("文件下载失败", e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
    
    /**
     * 预览文件
     * @param id 文件ID
     * @return 文件资源
     */
    @GetMapping("/preview/{id}")
    @Operation(summary = "预览文件", description = "根据文件ID预览文件，支持图片直接查看")
    public Mono<ResponseEntity<Resource>> preview(@PathVariable("id") Long id) {
        log.info("预览文件: id={}", id);
        
        return fileService.handleFileDownload(id)
                .map(response -> {
                    // 修改Content-Disposition为inline，使浏览器尝试直接预览
                    return ResponseEntity.ok()
                            .contentType(response.getHeaders().getContentType())
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                            .body(response.getBody());
                })
                .onErrorResume(e -> {
                    log.error("文件预览失败", e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
    
    /**
     * 获取文件预览URL
     * @param id 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 预览URL
     */
    @GetMapping("/url/{id}")
    @Operation(summary = "获取文件预览URL", description = "获取文件的预览URL")
    public Mono<Result<String>> getPreviewUrl(
            @PathVariable("id") Long id,
            @RequestParam(value = "expires", defaultValue = "3600") long expireSeconds) {
        
        log.info("获取文件预览URL: id={}, expires={}s", id, expireSeconds);
        
        return fileService.generatePreviewUrl(id, expireSeconds)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取预览URL失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 获取文件下载URL
     * @param id 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 下载URL
     */
    @GetMapping("/download/url/{id}")
    @Operation(summary = "获取文件下载URL", description = "获取文件的下载URL")
    public Mono<Result<String>> getDownloadUrl(
            @PathVariable("id") Long id,
            @RequestParam(value = "expires", defaultValue = "3600") long expireSeconds) {
        
        log.info("获取文件下载URL: id={}, expires={}s", id, expireSeconds);
        
        return fileService.generateDownloadUrl(id, expireSeconds)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取下载URL失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 获取文件详情
     * @param id 文件ID
     * @return 文件详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "获取文件详情", description = "获取文件的详细信息")
    public Mono<Result<FileInfoVO>> getFileInfo(@PathVariable("id") Long id) {
        log.info("获取文件详情: id={}", id);
        
        return fileService.getFileInfo(id)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取文件详情失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 更新文件信息
     * @param id 文件ID
     * @param filesDTO 文件信息
     * @return 更新后的文件信息
     */
    @PutMapping("/edit/{id}")
    @Operation(summary = "更新文件信息", description = "更新文件的基本信息")
    public Mono<Result<FileInfoVO>> updateFileInfo(
            @PathVariable("id") Long id,
            @RequestBody FilesDTO filesDTO) {
        
        log.info("更新文件信息: id={}, data={}", id, filesDTO);
        
        return fileService.updateFileInfo(id, filesDTO)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("更新文件信息失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 获取文件版本历史
     * @param id 文件ID
     * @return 版本历史列表
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "获取文件版本历史", description = "获取文件的所有版本历史")
    public Mono<Result<List<FileVersionVO>>> getFileVersions(@PathVariable("id") Long id) {
        log.info("获取文件版本历史: id={}", id);
        
        return fileService.getFileVersions(id)
                .collectList()
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取文件版本历史失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    /**
     * 获取资源组中的文件
     *
     * @param queryDTO 查询参数
     * @return 文件列表分页结果
     */
    @GetMapping("/group")
    @Operation(summary = "获取资源组文件列表", description = "分页获取资源组中的文件ID列表")
    public Mono<Result<PageResult<File>>> getGroupFiles( @ParameterObject @Valid ResourceGroupQueryDTO queryDTO) {
        return fileService.getGroupFiles(queryDTO)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取资源组文件列表失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    /**
     * 搜索文件
     * @param searchDTO 搜索参数
     * @return 搜索结果
     */
    @GetMapping("/search")
    @Operation(summary = "搜索文件", description = "根据条件搜索文件")
    public Mono<Result<Map<String, Object>>> searchFiles(FileSearchDTO searchDTO) {
        log.info("搜索文件: {}", searchDTO);
        
        return fileService.searchFiles(searchDTO)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("搜索文件失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }
    
    /**
     * 批量获取文件URL
     * @param fileIds 文件ID列表
     * @return 文件URL映射
     */
    @PostMapping("/batch/urls")
    @Operation(summary = "批量获取文件URL", description = "根据文件ID列表批量获取文件URL")
    public Mono<Result<Map<Long, String>>> getBatchUrls(@RequestBody List<Long> fileIds) {
        log.info("批量获取文件URL: fileIds={}", fileIds);
        
        return fileService.getBatchFileUrls(fileIds)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("批量获取文件URL失败", e);
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

} 