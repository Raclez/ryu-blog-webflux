package com.ryu.blog.utils;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MinIO工具类
 * 封装MinIO操作的通用方法
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
public final class MinioUtils {

    private MinioUtils() {
    }

    /**
     * 把阻塞的 MinIO SDK 调用包装成非阻塞的 Mono。
     * <p>
     * MinIO Java SDK 是纯阻塞实现（内部走 OkHttp 同步调用），
     * 直接在 Netty event-loop 线程上执行会阻塞整个事件循环，
     * 因此统一调度到 {@link Schedulers#boundedElastic()} 线程池。
     *
     * @param callable 阻塞操作
     * @return 在 boundedElastic 线程上执行的 Mono
     */
    private static <T> Mono<T> blocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

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
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 确认存储桶存在，不存在则创建。
     *
     * <p><b>失败一律抛错，不再返回 {@code false}。</b>
     * 早先的实现把任何 {@code MinioException} 都 catch 成 {@code false}，
     * 调用方于是把「AK 无效（InvalidAccessKeyId）」「权限不足（AccessDenied）」
     * 「连不上服务器（IOException）」统统翻译成「存储桶不存在且无法创建」——
     * 凭据问题看起来像桶的问题，排查方向直接被带偏。
     *
     * <p>{@code bucketExists} 内部已经把 {@code NoSuchBucket} 翻成 {@code false}，
     * 所以它抛出来的异常都是真错误，原样上报即可。
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @return 成功即完成；失败为 error
     */
    public static Mono<Void> ensureBucketExists(MinioClient minioClient, String bucketName) {
        return blocking(() -> {
            boolean exists;
            try {
                exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            } catch (Exception e) {
                throw describe("存储桶检查(" + bucketName + ")", e);
            }

            if (!exists) {
                createBucket(minioClient, bucketName);
            }
            return true;
        }).then();
    }

    /**
     * 创建存储桶；已被别人（并发请求 / 外部脚本）建好也算成功。
     */
    private static void createBucket(MinioClient minioClient, String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("创建存储桶成功: {}", bucketName);
        } catch (Exception e) {
            if (isErrorCode(e, "BucketAlreadyOwnedByYou") || isErrorCode(e, "BucketAlreadyExists")) {
                log.debug("存储桶已存在（并发创建或外部创建）: {}", bucketName);
                return;
            }
            throw describe("存储桶创建(" + bucketName + ")", e);
        }
    }

    /**
     * 执行一次上传，<b>不预先发 HeadBucket</b>。
     *
     * <p>正常路径只有一次请求；只有当服务端明确回 {@code NoSuchBucket} 时，
     * 才建桶并重试一次。这样既省掉每次上传多出来的一次往返，
     * 又能在桶被外部删掉后自愈 —— 不依赖任何进程内缓存。
     *
     * <p>⚠️ {@code operation} 可能被<b>调用两次</b>，它必须在内部重新打开输入流，
     * 不能复用上一次已经被读空的那个。
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param action      动作名称，用于错误消息
     * @param operation   真正的上传动作
     * @return 在 boundedElastic 线程上执行的结果
     */
    private static <T> Mono<T> withBucketAutoCreate(MinioClient minioClient, String bucketName,
                                                    String action, Callable<T> operation) {
        return blocking(() -> {
            try {
                return operation.call();
            } catch (Exception e) {
                if (!isErrorCode(e, "NoSuchBucket")) {
                    throw wrap(action, e);
                }
                log.info("存储桶不存在，创建后重试一次: {}", bucketName);
                createBucket(minioClient, bucketName);
                try {
                    return operation.call();
                } catch (Exception retryFailure) {
                    throw wrap(action, retryFailure);
                }
            }
        });
    }

    /**
     * 判断异常是不是某个 MinIO 错误码（例如 {@code NoSuchBucket}）。
     */
    private static boolean isErrorCode(Exception e, String code) {
        return e instanceof ErrorResponseException
                && ((ErrorResponseException) e).errorResponse() != null
                && code.equals(((ErrorResponseException) e).errorResponse().code());
    }

    /**
     * 已经是运行时异常的（多半是本类自己包的）原样抛，别套两层。
     */
    private static RuntimeException wrap(String action, Exception e) {
        return e instanceof RuntimeException re ? re : describe(action, e);
    }

    /**
     * 把 MinIO SDK 的异常翻译成能看懂的消息。
     *
     * <p>消息里必须带上服务端返回的 <b>error code</b>：
     * {@code InvalidAccessKeyId} 和 {@code AccessDenied} 是两件事，
     * 前者要换凭据，后者要改策略，混成一句「操作失败」等于什么都没说。
     *
     * @param action 动作名称
     * @param e      原始异常
     * @return 带原因的可读异常
     */
    private static RuntimeException describe(String action, Exception e) {
        if (e instanceof ErrorResponseException && ((ErrorResponseException) e).errorResponse() != null) {
            ErrorResponseException ere = (ErrorResponseException) e;
            return new RuntimeException(action + "失败: MinIO 返回 "
                    + ere.errorResponse().code() + " - " + ere.errorResponse().message(), e);
        }
        if (e instanceof IOException) {
            return new RuntimeException(action + "失败: 无法连接 MinIO（"
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + "）", e);
        }
        return new RuntimeException(action + "失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
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
        return withBucketAutoCreate(minioClient, bucketName, "文件上传", () -> {
            // 每次尝试都新建流：重试时上一条已经被 SDK 读空了
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .build());

            log.debug("文件上传成功: {}/{}", bucketName, objectName);
            return objectName;
        });
    }

    /**
     * 从<b>本地文件</b>上传（流式读取，不把内容读进堆）。
     *
     * <p>与 {@link #uploadFile(MinioClient, String, String, byte[], String)} 的区别：
     * 那个版本要求调用方已经持有整份 {@code byte[]}，
     * 大文件会在堆上多出一份完整副本。这里改成边读边传。
     *
     * <p>桶不存在时由 {@code withBucketAutoCreate} 建桶后重试，
     * 重试会<b>重新打开</b>文件流 —— 复用旧流只会传上去一个空对象。
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名
     * @param objectName  对象名称
     * @param file        本地文件
     * @param contentType 内容类型
     * @return 对象名称
     */
    public static Mono<String> uploadFileStreaming(MinioClient minioClient, String bucketName,
                                                   String objectName, java.nio.file.Path file,
                                                   String contentType) {
        return withBucketAutoCreate(minioClient, bucketName, "文件上传", () -> {
            // 每次尝试都重新打开文件：重试时上一条流已经被 SDK 读空了
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                long size = java.nio.file.Files.size(file);
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(in, size, -1)
                                .contentType(contentType)
                                .build());
            }

            log.debug("文件上传成功（流式）: {}/{}", bucketName, objectName);
            return objectName;
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("文件下载失败: {}", e.getMessage(), e);
                throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("文件下载失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("文件删除失败: {}", e.getMessage(), e);
                return false;
            } catch (IOException e) {
                log.error("文件删除失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
                } catch (io.minio.errors.MinioException e) {
                    log.error("获取删除结果失败: {}", e.getMessage(), e);
                }
            }

            return results;
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
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
            } catch (IOException e) {
                log.error("检查文件是否存在失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("获取文件元数据失败: {}", e.getMessage(), e);
                throw new RuntimeException("获取文件元数据失败: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("获取文件元数据失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("生成文件预览URL失败: {}", e.getMessage(), e);
                throw new RuntimeException("生成文件预览URL失败: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("生成文件预览URL失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("生成文件下载URL失败: {}", e.getMessage(), e);
                throw new RuntimeException("生成文件下载URL失败: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("生成文件下载URL失败-IO异常: {}", e.getMessage(), e);
                throw new RuntimeException("生成文件下载URL失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 初始化分片上传
     *
     * <p>本方法自身不访问网络（只是生成一个 uploadId），
     * 所以这里是分片上传<b>唯一</b>能提前暴露配置错误的地方 —— 每个上传会话只查一次，
     * 不是每个分片一次。
     *
     * @param minioClient MinIO客户端
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param contentType 内容类型
     * @return 上传ID
     */
    public static Mono<String> initiateMultipartUpload(MinioClient minioClient, String bucketName, String objectName, String contentType) {
        return ensureBucketExists(minioClient, bucketName)
                .then(blocking(() -> {
                    // 使用PutObject方式上传，MinIO的Java SDK不直接支持创建分片上传请求
                    // 这里使用一个唯一ID作为uploadId，实际使用时可能需要根据具体实现调整
                    String uploadId = java.util.UUID.randomUUID().toString();

                    log.debug("初始化分片上传成功: {}/{}, uploadId={}", bucketName, objectName, uploadId);
                    return uploadId;
                }));
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("上传分片失败: {}", e.getMessage(), e);
                throw new RuntimeException("上传分片失败: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("上传分片失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
            } catch (io.minio.errors.MinioException e) {
                log.error("完成分片上传失败: {}", e.getMessage(), e);
                throw new RuntimeException("完成分片上传失败: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("完成分片上传失败-IO异常: {}", e.getMessage(), e);
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
        return blocking(() -> {
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
                    } catch (io.minio.errors.MinioException e) {
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
            } catch (IOException e) {
                log.error("终止分片上传失败-IO异常: {}", e.getMessage(), e);
                return false;
            }
        });
    }
} 