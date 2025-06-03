package com.ryu.blog.utils;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MinIO工具类
 * 封装MinIO操作的通用方法
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
public class MinioUtils {
    
    // 保存分片上传的信息
    private static final Map<String, Map<String, Object>> MULTIPART_UPLOADS = new ConcurrentHashMap<>();

    /**
     * 创建MinIO客户端
     *
     * @param endpoint   MinIO服务器地址
     * @param accessKey  访问密钥
     * @param secretKey  秘密密钥
     * @param secure     是否使用HTTPS
     * @return MinIO客户端对象
     */
    public static MinioClient createMinioClient(String endpoint, String accessKey, String secretKey, boolean secure) {
        try {
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        } catch (Exception e) {
            log.error("创建MinIO客户端失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建MinIO客户端失败", e);
        }
    }

    /**
     * 检查存储桶是否存在，不存在则创建
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @return 操作结果
     */
    public static Mono<Boolean> ensureBucketExists(MinioClient minioClient, String bucketName) {
        return Mono.fromCallable(() -> {
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                    log.info("创建存储桶成功: {}", bucketName);
                }
                return true;
            } catch (Exception e) {
                log.error("检查或创建存储桶失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 上传文件
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param data        文件数据
     * @param contentType 内容类型
     * @return 对象名称
     */
    public static Mono<String> uploadFile(MinioClient minioClient, String bucketName, String objectName, byte[] data, String contentType) {
        return ensureBucketExists(minioClient, bucketName)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new RuntimeException("存储桶不存在且无法创建: " + bucketName));
                    }
                    
                    return Mono.fromCallable(() -> {
                        try {
                            // 使用PutObject上传
                            minioClient.putObject(
                                PutObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .stream(new ByteArrayInputStream(data), data.length, -1)
                                    .contentType(contentType)
                                    .build()
                            );
                            
                            log.debug("文件上传成功: {}/{}", bucketName, objectName);
                            return objectName;
                        } catch (Exception e) {
                            log.error("文件上传失败: {}", e.getMessage(), e);
                            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
                        }
                    });
                });
    }

    /**
     * 下载文件
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @return 文件数据
     */
    public static Mono<byte[]> downloadFile(MinioClient minioClient, String bucketName, String objectName) {
        return Mono.fromCallable(() -> {
            try {
                // 获取对象
                GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
                );
                
                // 读取对象内容
                byte[] data = response.readAllBytes();
                response.close();
                
                log.debug("文件下载成功: {}/{}", bucketName, objectName);
                return data;
            } catch (Exception e) {
                log.error("文件下载失败: {}", e.getMessage(), e);
                throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 删除文件
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @return 是否删除成功
     */
    public static Mono<Boolean> deleteFile(MinioClient minioClient, String bucketName, String objectName) {
        return Mono.fromCallable(() -> {
            try {
                // 删除对象
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
                );
                
                log.debug("文件删除成功: {}/{}", bucketName, objectName);
                return true;
            } catch (Exception e) {
                log.error("文件删除失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 批量删除文件
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectNames 对象名称列表
     * @return 删除结果，键为对象名称，值为是否删除成功
     */
    public static Mono<Map<String, Boolean>> batchDeleteFiles(MinioClient minioClient, String bucketName, List<String> objectNames) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Boolean> results = new HashMap<>();
                
                // 创建删除对象列表
                List<DeleteObject> objects = objectNames.stream()
                        .map(DeleteObject::new)
                        .collect(Collectors.toList());
                
                // 执行批量删除
                Iterable<io.minio.Result<DeleteError>> deleteResults = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                        .bucket(bucketName)
                        .objects(objects)
                        .build()
                );
                
                // 初始化所有为成功
                for (String objectName : objectNames) {
                    results.put(objectName, true);
                }
                
                // 记录失败的对象
                for (io.minio.Result<DeleteError> result : deleteResults) {
                    try {
                        DeleteError error = result.get();
                        results.put(error.objectName(), false);
                        log.error("删除对象失败: {}, 错误: {}", error.objectName(), error.message());
                    } catch (Exception e) {
                        log.error("获取删除结果失败: {}", e.getMessage(), e);
                    }
                }
                
                return results;
            } catch (Exception e) {
                log.error("批量删除文件失败: {}", e.getMessage(), e);
                // 所有对象标记为删除失败
                Map<String, Boolean> results = new HashMap<>();
                for (String objectName : objectNames) {
                    results.put(objectName, false);
                }
                return results;
            }
        });
    }

    /**
     * 检查文件是否存在
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @return 是否存在
     */
    public static Mono<Boolean> fileExists(MinioClient minioClient, String bucketName, String objectName) {
        return Mono.fromCallable(() -> {
            try {
                // 尝试获取对象状态
                minioClient.statObject(
                    StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
                );
                
                log.debug("文件存在: {}/{}", bucketName, objectName);
                return true;
            } catch (Exception e) {
                if (e instanceof ErrorResponseException) {
                    ErrorResponseException ere = (ErrorResponseException) e;
                    if (ere.errorResponse().code().equals("NoSuchKey") || 
                        ere.errorResponse().code().equals("NoSuchBucket")) {
                        log.debug("文件不存在: {}/{}", bucketName, objectName);
                        return false;
                    }
                }
                log.error("检查文件是否存在失败: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 获取文件元数据
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @return 元数据信息
     */
    public static Mono<Map<String, Object>> getMetadata(MinioClient minioClient, String bucketName, String objectName) {
        return Mono.fromCallable(() -> {
            try {
                // 获取对象状态
                StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
                );
                
                // 构建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("size", stat.size());
                metadata.put("contentType", stat.contentType());
                metadata.put("lastModified", System.currentTimeMillis()); // 简化处理，使用当前时间
                metadata.put("etag", stat.etag());
                metadata.put("headers", stat.headers());
                
                log.debug("获取文件元数据成功: {}/{}", bucketName, objectName);
                return metadata;
            } catch (Exception e) {
                log.error("获取文件元数据失败: {}", e.getMessage(), e);
                throw new RuntimeException("获取文件元数据失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 生成文件预览URL
     *
     * @param minioClient   MinIO客户端
     * @param bucketName    存储桶名称
     * @param objectName    对象名称
     * @param expireSeconds 过期时间(秒)
     * @return 预览URL
     */
    public static Mono<String> generatePreviewUrl(MinioClient minioClient, String bucketName, String objectName, long expireSeconds) {
        return Mono.fromCallable(() -> {
            try {
                // 生成预签名URL
                String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry((int) expireSeconds, TimeUnit.SECONDS)
                        .build()
                );
                
                log.debug("生成文件预览URL成功: {}", url);
                return url;
            } catch (Exception e) {
                log.error("生成文件预览URL失败: {}", e.getMessage(), e);
                throw new RuntimeException("生成文件预览URL失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 生成文件下载URL
     *
     * @param minioClient   MinIO客户端
     * @param bucketName    存储桶名称
     * @param objectName    对象名称
     * @param expireSeconds 过期时间(秒)
     * @return 下载URL
     */
    public static Mono<String> generateDownloadUrl(MinioClient minioClient, String bucketName, String objectName, long expireSeconds) {
        return Mono.fromCallable(() -> {
            try {
                // 设置响应头，使浏览器下载而不是预览
                Map<String, String> reqParams = new HashMap<>();
                reqParams.put("response-content-disposition", "attachment; filename=\"" + objectName + "\"");
                
                // 生成预签名URL
                String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry((int) expireSeconds, TimeUnit.SECONDS)
                        .extraQueryParams(reqParams)
                        .build()
                );
                
                log.debug("生成文件下载URL成功: {}", url);
                return url;
            } catch (Exception e) {
                log.error("生成文件下载URL失败: {}", e.getMessage(), e);
                throw new RuntimeException("生成文件下载URL失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 初始化分片上传
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param contentType 内容类型
     * @return 上传ID
     */
    public static Mono<String> initiateMultipartUpload(MinioClient minioClient, String bucketName, String objectName, String contentType) {
        return ensureBucketExists(minioClient, bucketName)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new RuntimeException("存储桶不存在且无法创建: " + bucketName));
                    }
                    
                    return Mono.fromCallable(() -> {
                        try {
                            // 使用PutObject方式上传，MinIO的Java SDK不直接支持创建分片上传请求
                            // 这里使用一个唯一ID作为uploadId，实际使用时可能需要根据具体实现调整
                            String uploadId = java.util.UUID.randomUUID().toString();
                            
                            log.debug("初始化分片上传成功: {}/{}, uploadId={}", bucketName, objectName, uploadId);
                            return uploadId;
                        } catch (Exception e) {
                            log.error("初始化分片上传失败: {}", e.getMessage(), e);
                            throw new RuntimeException("初始化分片上传失败: " + e.getMessage(), e);
                        }
                    });
                });
    }

    /**
     * 上传分片
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param uploadId    上传ID
     * @param partNumber  分片序号
     * @param data        分片数据
     * @return 分片ETag
     */
    public static Mono<String> uploadPart(MinioClient minioClient, String bucketName, String objectName, String uploadId, int partNumber, byte[] data) {
        return Mono.fromCallable(() -> {
            try {
                // MinIO Java SDK不直接支持简单的分片上传API
                // 这里模拟实现，实际使用时需根据MinIO具体版本调整实现
                String partObjectName = String.format("%s.part.%d", objectName, partNumber);
                
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(partObjectName)
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .build()
                );
                
                // 计算分片的MD5哈希值作为ETag
                String etag = FileUtils.calculateMD5(data);
                
                log.debug("上传分片成功: {}/{}, uploadId={}, partNumber={}, etag={}", bucketName, objectName, uploadId, partNumber, etag);
                return etag;
            } catch (Exception e) {
                log.error("上传分片失败: {}", e.getMessage(), e);
                throw new RuntimeException("上传分片失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 完成分片上传
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param uploadId    上传ID
     * @param parts       分片信息，键为分片序号，值为分片ETag
     * @return 对象名称
     */
    public static Mono<String> completeMultipartUpload(MinioClient minioClient, String bucketName, String objectName, String uploadId, Map<Integer, String> parts) {
        return Mono.fromCallable(() -> {
            try {
                // MinIO Java SDK不直接支持简单的完成分片上传API
                // 这里模拟实现，实际使用时需要根据MinIO具体版本调整实现
                
                // 1. 读取所有分片
                List<byte[]> partDataList = new ArrayList<>();
                for (int i = 1; i <= parts.size(); i++) {
                    String partObjectName = String.format("%s.part.%d", objectName, i);
                    GetObjectResponse response = minioClient.getObject(
                        GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(partObjectName)
                            .build()
                    );
                    partDataList.add(response.readAllBytes());
                    response.close();
                }
                
                // 2. 合并所有分片
                int totalSize = partDataList.stream().mapToInt(data -> data.length).sum();
                byte[] completeData = new byte[totalSize];
                int currentPos = 0;
                for (byte[] partData : partDataList) {
                    System.arraycopy(partData, 0, completeData, currentPos, partData.length);
                    currentPos += partData.length;
                }
                
                // 3. 上传合并后的文件
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(completeData), completeData.length, -1)
                        .build()
                );
                
                // 4. 删除所有分片
                for (int i = 1; i <= parts.size(); i++) {
                    String partObjectName = String.format("%s.part.%d", objectName, i);
                    minioClient.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(partObjectName)
                            .build()
                    );
                }
                
                log.debug("完成分片上传成功: {}/{}, uploadId={}, parts={}", bucketName, objectName, uploadId, parts.size());
                return objectName;
            } catch (Exception e) {
                log.error("完成分片上传失败: {}", e.getMessage(), e);
                throw new RuntimeException("完成分片上传失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 终止分片上传
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param uploadId    上传ID
     * @return 是否成功
     */
    public static Mono<Boolean> abortMultipartUpload(MinioClient minioClient, String bucketName, String objectName, String uploadId) {
        return Mono.fromCallable(() -> {
            try {
                // MinIO Java SDK不直接支持简单的终止分片上传API
                // 这里模拟实现，删除所有已上传的分片
                
                // 列出所有可能的分片（实际应用中可能需要从MULTIPART_UPLOADS中获取）
                for (int i = 1; i <= 10000; i++) { // 假设最多10000个分片
                    String partObjectName = String.format("%s.part.%d", objectName, i);
                    try {
                        // 检查分片是否存在
                        minioClient.statObject(
                            StatObjectArgs.builder()
                                .bucket(bucketName)
                                .object(partObjectName)
                                .build()
                        );
                        
                        // 如果存在，则删除
                        minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(partObjectName)
                                .build()
                        );
                    } catch (Exception e) {
                        // 如果分片不存在，则停止检查
                        if (e instanceof ErrorResponseException) {
                            ErrorResponseException ere = (ErrorResponseException) e;
                            if (ere.errorResponse().code().equals("NoSuchKey")) {
                                break;
                            }
                        }
                    }
                }
                
                log.debug("终止分片上传成功: {}/{}, uploadId={}", bucketName, objectName, uploadId);
                return true;
            } catch (Exception e) {
                log.error("终止分片上传失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }
} 