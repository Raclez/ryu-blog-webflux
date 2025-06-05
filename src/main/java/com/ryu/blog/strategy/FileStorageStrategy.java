package com.ryu.blog.strategy;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 文件存储策略接口
 * 定义不同存储策略（本地、MinIO、阿里云OSS、腾讯云COS等）的通用操作方法
 *
 * @author ryu 475118582@qq.com
 */
public interface FileStorageStrategy {
    
    /**
     * 获取策略标识
     * @return 策略标识
     */
    String getStrategyKey();
    
    /**
     * 上传文件
     * @param filePart 文件部分
     * @return 文件路径
     */
    Mono<String> uploadFile(FilePart filePart);
    
    /**
     * 上传文件
     * @param dataBufferFlux 数据缓冲流
     * @param fileName 文件名
     * @param size 文件大小
     * @return 文件路径
     */
    Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size);
    
    /**
     * 下载文件
     * @param path 文件路径
     * @return 数据缓冲流
     */
    Mono<Flux<DataBuffer>> downloadFile(String path);
    
    /**
     * 删除文件
     * @param path 文件路径
     * @return 是否成功
     */
    Mono<Boolean> deleteFile(String path);
    
    /**
     * 批量删除文件
     * @param paths 文件路径列表
     * @return 删除结果，键为文件路径，值为是否成功
     */
    Mono<Map<String, Boolean>> batchDeleteFiles(List<String> paths);
    
    /**
     * 检查文件是否存在
     * @param path 文件路径
     * @return 是否存在
     */
    Mono<Boolean> fileExists(String path);
    
    /**
     * 获取文件元数据
     * @param path 文件路径
     * @return 元数据信息
     */
    Mono<Map<String, Object>> getMetadata(String path);
    
    /**
     * 生成文件预览URL
     * @param path 文件路径
     * @param expireSeconds 过期时间(秒)
     * @return 预览URL
     */
    Mono<String> generatePreviewUrl(String path, long expireSeconds);
    
    /**
     * 生成文件下载URL
     * @param path 文件路径
     * @param expireSeconds 过期时间(秒)
     * @return 下载URL
     */
    Mono<String> generateDownloadUrl(String path, long expireSeconds);
    
    /**
     * 计算文件校验和
     * @param path 文件路径
     * @return 校验和
     */
    Mono<String> calculateFileChecksum(String path);
    
    /**
     * 生成缩略图
     * @param filePath 原文件路径
     * @param thumbnailPath 缩略图路径
     * @param width 宽度
     * @param height 高度
     * @return 缩略图路径
     */
    Mono<String> generateThumbnail(String filePath, String thumbnailPath, int width, int height);
    
    /**
     * 初始化分片上传
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param storageParams 存储参数
     * @return 上传ID和对象名
     */
    Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize, Map<String, String> storageParams);
    
    /**
     * 上传分片
     * @param uploadId 上传ID
     * @param partNumber 分片序号
     * @param content 分片内容
     * @return 分片ETag
     */
    Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content);
    
    /**
     * 完成分片上传
     * @param uploadId 上传ID
     * @param partETags 分片ETag列表
     * @return 文件路径
     */
    Mono<String> completeMultipartUpload(String uploadId, List<String> partETags);
    
    /**
     * 终止分片上传
     * @param uploadId 上传ID
     * @return 是否成功
     */
    Mono<Boolean> abortMultipartUpload(String uploadId);
    
    /**
     * 获取文件的公共永久URL（不需要身份验证，适用于博客图片等公开内容）
     * @param path 文件路径
     * @return 公共永久URL
     */
    Mono<String> getPublicUrl(String path);
} 