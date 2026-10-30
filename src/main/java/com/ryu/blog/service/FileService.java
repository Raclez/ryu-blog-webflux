package com.ryu.blog.service;

import com.ryu.blog.dto.*;
import com.ryu.blog.entity.File;
import com.ryu.blog.vo.FileDownloadVO;
import com.ryu.blog.vo.FileInfoVO;
import com.ryu.blog.vo.FileUploadVO;
import com.ryu.blog.vo.FileVersionVO;
import com.ryu.blog.vo.PageResult;
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
     * @param uploaderId 上传者ID（由 Controller 从登录态传入，不能由客户端提供）
     * @return 文件上传结果
     */
    Mono<FileUploadVO> handleFileUpload(FilePart filePart, UploadOptionsDTO options, Long uploaderId);

    /**
     * 处理批量文件上传
     * 
     * @param fileParts 上传的文件列表
     * @param options 上传选项
     * @param uploaderId 上传者ID
     * @return 上传结果列表
     */
    Flux<FileUploadVO> handleBatchFileUpload(Flux<FilePart> fileParts, UploadOptionsDTO options, Long uploaderId);
    
    /**
     * 处理大文件分片上传
     * 
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param options 上传选项
     * @param uploaderId 上传者ID
     * @return 上传ID和上传URL
     */
    Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, UploadOptionsDTO options, Long uploaderId);

    /**
     * 上传文件分片
     *
     * @param uploadId 上传ID
     * @param partNumber 分片号
     * @param partData 分片数据
     * @param uploaderId 上传者ID（必须与该上传会话的创建者一致）
     * @return 分片标识
     */
    Mono<String> uploadPart(String uploadId, int partNumber, byte[] partData, Long uploaderId);

    /**
     * 完成分片上传
     *
     * @param uploadId 上传ID
     * @param partETags 分片标识列表
     * @param options 上传选项
     * @param uploaderId 上传者ID
     * @return 上传结果
     */
    Mono<FileUploadVO> completeMultipartUpload(String uploadId, List<String> partETags, UploadOptionsDTO options, Long uploaderId);

    /**
     * 终止分片上传
     *
     * @param uploadId 上传ID
     * @param uploaderId 上传者ID
     * @return 是否成功
     */
    Mono<Boolean> abortMultipartUpload(String uploadId, Long uploaderId);

    /**
     * 处理文件下载的业务逻辑
     *
     * <p>返回内容流而非完整字节数组，文件不会整体驻留内存；
     * HTTP 响应由 Controller 组装。
     *
     * @param fileId 文件ID
     * @param ctx   操作者上下文 —— 用于文件可见性 / 归属判定
     * @return 文件下载结果（内容流 + 描述信息）
     */
    Mono<FileDownloadVO> handleFileDownload(Long fileId, OperatorContext ctx);



    /**
     * 处理文件删除的业务逻辑
     *
     * @param fileId 文件的 ID
     * @param ctx    操作者上下文 —— 只有创建者与管理员能删
     */
    Mono<Void> handleFileDelete(Long fileId, OperatorContext ctx);

    /**
     * 批量删除文件
     * @param fileIds 文件ID列表
     * @param ctx     操作者上下文 —— 只有创建者与管理员能删
     * @return 删除结果，key为fileId，value为是否成功
     */
    Mono<Map<Long, Boolean>> batchDeleteFiles(List<Long> fileIds, OperatorContext ctx);

    /**
     * 获取文件详细信息
     * @param fileId 文件ID
     * @param ctx   操作者上下文 —— 用于可见性判定
     * @return 文件详细信息
     */
    Mono<FileInfoVO> getFileInfo(Long fileId, OperatorContext ctx);

    /**
     * 更新文件信息
     * @param fileId 文件ID
     * @param fileUpdateDTO 文件信息
     * @param ctx 操作者上下文 —— 只有创建者与管理员能改
     * @return 更新后的文件信息
     */
    Mono<FileInfoVO> updateFileInfo(Long fileId, FileUpdateDTO fileUpdateDTO, OperatorContext ctx);

    /**
     * 获取文件版本历史
     * @param fileId 文件ID
     * @param ctx   操作者上下文 —— 用于可见性判定
     * @return 版本历史列表
     */
    Flux<FileVersionVO> getFileVersions(Long fileId, OperatorContext ctx);

    /**
     * 搜索文件
     * @param searchDTO 搜索参数
     * @param ctx       操作者上下文 —— 非管理员只能搜自己的文件
     * @return 搜索结果
     */
    Mono<Map<String, Object>> searchFiles(FileSearchDTO searchDTO, OperatorContext ctx);

    /**
     * 生成文件预览URL
     * @param fileId 文件ID
     * @param expireSeconds 过期时间(秒)
     * @param ctx 操作者上下文 —— 用于可见性判定
     * @return 预览URL
     */
    Mono<String> generatePreviewUrl(Long fileId, long expireSeconds, OperatorContext ctx);

    /**
     * 生成文件下载URL
     * @param fileId 文件ID
     * @param expireSeconds 过期时间(秒)
     * @param ctx 操作者上下文 —— 用于可见性判定
     * @return 下载URL
     */
    Mono<String> generateDownloadUrl(Long fileId, long expireSeconds, OperatorContext ctx);

    /**
     * 批量获取文件详细信息
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到文件详情的映射
     */
    Mono<Map<Long, FileInfoVO>> getBatchFileInfos(List<Long> fileIds, OperatorContext ctx);

    /**
     * 批量获取文件URL
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到URL的映射
     */
    Mono<Map<Long, String>> getBatchFileUrls(List<Long> fileIds, OperatorContext ctx);
    
    /**
     * 获取用户文件列表
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    Flux<FileInfoVO> getUserFiles(Long userId, OperatorContext ctx);
    
    /**
     * 分页获取文件列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 文件列表和分页信息
     */
    Mono<Map<String, Object>> getFileList(int page, int size, OperatorContext ctx);
    
    /**
     * 根据类型获取文件列表
     *
     * @param type 文件类型
     * @return 文件列表
     */
    Flux<FileInfoVO> getFilesByType(String type, OperatorContext ctx);
    
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
     * <p><b>只返回公开文件</b>（{@code access_type = 0}）——
     * 这个 URL 不带任何身份凭证，能拿到就能取到内容。
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到永久URL的映射
     */
    Mono<Map<Long, String>> getBatchFilePermanentUrls(List<Long> fileIds);

    /**
     * 按存储路径取公开文件
     *
     * <p>公开直链 {@code GET /files/public/{path}} 的后端实现。
     * 只服务 {@code access_type = 0} 的文件；私有文件一律 403。
     *
     * @param path 存储路径
     * @return 文件内容
     */
    Mono<FileDownloadVO> handlePublicFileAccess(String path);
} 