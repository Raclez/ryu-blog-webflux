package com.ryu.blog.service;

import com.ryu.blog.dto.*;
import com.ryu.blog.entity.File;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileUploadVO;
import com.ryu.blog.vo.FileVersionVO;
import com.ryu.blog.vo.PageResult;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件服务接口
 *
 * @author ryu 475118582@qq.com
 */
public interface FileService {

    /**
     * 处理文件上传的业务逻辑
     *
     * @param filePart 上传的文件
     * @param options 上传选项
     * @return 文件上传结果
     */
    Mono<FileUploadVO> handleFileUpload(FilePart filePart, UploadOptionsDTO options);

    /**
     * 处理批量文件上传
     * 
     * @param fileParts 上传的文件列表
     * @param options 上传选项
     * @return 上传结果列表
     */
    Flux<FileUploadVO> handleBatchFileUpload(Flux<FilePart> fileParts, UploadOptionsDTO options);
    
    /**
     * 处理大文件分片上传
     * 
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param options 上传选项
     * @return 上传ID和上传URL
     */
    Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, UploadOptionsDTO options);
    
    /**
     * 上传文件分片
     * 
     * @param uploadId 上传ID
     * @param partNumber 分片号
     * @param partData 分片数据
     * @return 分片标识
     */
    Mono<String> uploadPart(String uploadId, int partNumber, byte[] partData);
    
    /**
     * 完成分片上传
     * 
     * @param uploadId 上传ID
     * @param partETags 分片标识列表
     * @param options 上传选项
     * @return 上传结果
     */
    Mono<FileUploadVO> completeMultipartUpload(String uploadId, List<String> partETags, UploadOptionsDTO options);

    /**
     * 终止分片上传
     * 
     * @param uploadId 上传ID
     * @return 是否成功
     */
    Mono<Boolean> abortMultipartUpload(String uploadId);

    /**
     * 处理文件下载的业务逻辑
     * @param fileId 文件ID
     * @return ResponseEntity包含文件资源
     */
    Mono<ResponseEntity<Resource>> handleFileDownload(Long fileId);
    
    /**
     * 处理文件下载的业务逻辑
     * @param path 文件路径
     * @return 文件字节流
     */
    Mono<byte[]> handleFileDownload(String path);

    /**
     * 获取文件流
     * @param fileId 文件ID
     * @return 文件输入流
     */
    Mono<InputStream> getFileStream(Long fileId);

    /**
     * 处理文件删除的业务逻辑
     * @param fileId 文件的 ID
     */
    Mono<Void> handleFileDelete(Long fileId);
    
    /**
     * 批量删除文件
     * @param fileIds 文件ID列表
     * @return 删除结果，key为fileId，value为是否成功
     */
    Mono<Map<Long, Boolean>> batchDeleteFiles(List<Long> fileIds);

    /**
     * 获取文件分组
     * @param groupFileQueryDTO 查询参数
     * @return 分页结果
     */
    Mono<PageResult<File>> getGroupFiles(ResourceGroupQueryDTO groupFileQueryDTO);
    
    /**
     * 获取文件详细信息
     * @param fileId 文件ID
     * @return 文件详细信息
     */
    Mono<FileInfoVO> getFileInfo(Long fileId);
    
    /**
     * 更新文件信息
     * @param fileId 文件ID
     * @param filesDTO 文件信息
     * @return 更新后的文件信息
     */
    Mono<FileInfoVO> updateFileInfo(Long fileId, FilesDTO filesDTO);
    
    /**
     * 获取文件版本历史
     * @param fileId 文件ID
     * @return 版本历史列表
     */
    Flux<FileVersionVO> getFileVersions(Long fileId);
    
    /**
     * 搜索文件
     * @param searchDTO 搜索参数
     * @return 搜索结果
     */
    Mono<Map<String, Object>> searchFiles(FileSearchDTO searchDTO);
    
    /**
     * 生成文件预览URL
     * @param fileId 文件ID
     * @param expireSeconds 过期时间(秒)
     * @return 预览URL
     */
    Mono<String> generatePreviewUrl(Long fileId, long expireSeconds);
    
    /**
     * 生成文件下载URL
     * @param fileId 文件ID
     * @param expireSeconds 过期时间(秒)
     * @return 下载URL
     */
    Mono<String> generateDownloadUrl(Long fileId, long expireSeconds);

    /**
     * 批量获取文件详细信息
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到文件详情的映射
     */
    Mono<Map<Long, FileInfoVO>> getBatchFileInfos(List<Long> fileIds);

    /**
     * 批量获取文件URL
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到URL的映射
     */
    Mono<Map<Long, String>> getBatchFileUrls(List<Long> fileIds);
    
    /**
     * 获取用户文件列表
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    Flux<FileInfoVO> getUserFiles(Long userId);
    
    /**
     * 分页获取文件列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 文件列表和分页信息
     */
    Mono<Map<String, Object>> getFileList(int page, int size);
    
    /**
     * 根据类型获取文件列表
     *
     * @param type 文件类型
     * @return 文件列表
     */
    Flux<FileInfoVO> getFilesByType(String type);
    
    /**
     * 检查文件是否允许上传
     *
     * @param filename 文件名
     * @param size 文件大小
     * @return 是否允许上传
     */
    Mono<Boolean> isFileAllowed(String filename, long size);

    /**
     * 批量获取文件永久访问URL（适用于博客图片等公开内容）
     * 
     * @param fileIds 文件ID列表
     * @return 文件ID到永久URL的映射
     */
    Mono<Map<Long, String>> getBatchFilePermanentUrls(List<Long> fileIds);
} 