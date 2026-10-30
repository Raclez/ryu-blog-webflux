package com.ryu.blog.utils;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioUtils} 的失败语义。
 *
 * <p><b>它守的是什么</b>：{@code ensureBucketExists} 早先把任何异常都 catch 成
 * {@code false}，于是「AK 无效」「权限不足」「连不上服务器」在下游全被翻译成
 * 「存储桶不存在且无法创建」。线上真实的排查现场就是这样：桶好好地在，
 * 报错却指着桶，人跑去 MinIO 控制台翻了半天，真正的问题（凭据被轮换）
 * 只躺在日志里一行 {@code InvalidAccessKeyId}。
 *
 * <p>契约只有一条：<b>失败要把真实原因带出来，不能换个说法</b>。
 *
 * @author ryu
 */
class MinioUtilsTest {

    private static final String BUCKET = "ryu-blog";

    private final MinioClient client = mock(MinioClient.class);

    private static ErrorResponseException minioError(String code, String message) {
        ErrorResponse errorResponse = new ErrorResponse(code, message, BUCKET, null, null, null, null);
        // 真实的 SDK 异常一定带着 okhttp 响应；这里给一个空壳，
        // 免得 toString()/printStackTrace() 在测试里 NPE
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://minio.example.com/" + BUCKET).build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message(code)
                .build();
        return new ErrorResponseException(errorResponse, response, null);
    }

    @Test
    @DisplayName("凭据无效：错误消息里要有 InvalidAccessKeyId，不能说成「桶不存在」")
    void ensureBucketExists_propagatesCredentialErrors() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(minioError("InvalidAccessKeyId",
                        "The Access Key Id you provided does not exist in our records."));

        StepVerifier.create(MinioUtils.ensureBucketExists(client, BUCKET))
                .expectErrorSatisfies(e -> {
                    assertTrue(e.getMessage().contains("InvalidAccessKeyId"), e.getMessage());
                    assertFalse(e.getMessage().contains("不存在"), e.getMessage());
                })
                .verify();

        verify(client, never()).makeBucket(any());
    }

    @Test
    @DisplayName("桶确实不存在：bucketExists 返回 false 才去建桶")
    void ensureBucketExists_createsBucketWhenMissing() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        StepVerifier.create(MinioUtils.ensureBucketExists(client, BUCKET)).verifyComplete();

        verify(client).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("建桶撞车（BucketAlreadyOwnedByYou）不算失败")
    void ensureBucketExists_toleratesConcurrentCreation() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        // makeBucket 返回 void，用不了 when(...) —— 必须 doThrow(...).when(mock)
        doThrow(minioError("BucketAlreadyOwnedByYou",
                "Your previous request to create the named bucket succeeded and you already own it."))
                .when(client).makeBucket(any(MakeBucketArgs.class));

        StepVerifier.create(MinioUtils.ensureBucketExists(client, BUCKET)).verifyComplete();
    }

    @Test
    @DisplayName("网络异常要报错，不能退化成 false")
    void ensureBucketExists_propagatesIoErrors() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IOException("Connection refused"));

        StepVerifier.create(MinioUtils.ensureBucketExists(client, BUCKET))
                .expectErrorSatisfies(e ->
                        assertTrue(e.getMessage().contains("无法连接 MinIO"), e.getMessage()))
                .verify();
    }

    @Test
    @DisplayName("上传正常路径不发 HeadBucket —— 只有一次 putObject")
    void uploadFile_skipsHeadBucket() throws Exception {
        when(client.putObject(any())).thenAnswer(inv -> null);

        StepVerifier.create(MinioUtils.uploadFile(client, BUCKET, "2026/10/a.png",
                        new byte[]{1, 2, 3}, "image/png"))
                .expectNext("2026/10/a.png")
                .verifyComplete();

        verify(client, never()).bucketExists(any());
        verify(client, times(1)).putObject(any());
    }

    @Test
    @DisplayName("putObject 回 NoSuchBucket：建桶后重试一次就成功")
    void uploadFile_createsBucketAndRetries() throws Exception {
        when(client.putObject(any()))
                .thenThrow(minioError("NoSuchBucket", "The specified bucket does not exist."))
                .thenAnswer(inv -> null);

        StepVerifier.create(MinioUtils.uploadFile(client, BUCKET, "2026/10/a.png",
                        new byte[]{1, 2, 3}, "image/png"))
                .expectNext("2026/10/a.png")
                .verifyComplete();

        verify(client).makeBucket(any(MakeBucketArgs.class));
        verify(client, times(2)).putObject(any());
        verify(client, never()).bucketExists(any());
    }

    @Test
    @DisplayName("重试仍失败时，抛的是真实原因而不是「桶不存在且无法创建」")
    void uploadFile_retryFailureKeepsRealCause() throws Exception {
        when(client.putObject(any()))
                .thenThrow(minioError("NoSuchBucket", "The specified bucket does not exist."))
                .thenThrow(minioError("AccessDenied", "Access Denied."));

        StepVerifier.create(MinioUtils.uploadFile(client, BUCKET, "2026/10/a.png",
                        new byte[]{1, 2, 3}, "image/png"))
                .expectErrorSatisfies(e ->
                        assertTrue(e.getMessage().contains("AccessDenied"), e.getMessage()))
                .verify();
    }
}
