package com.ryu.blog.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 把上传流「抄」到临时文件，供只能整块上传的客户端（如对象存储 SDK）使用。
 *
 * <p><b>为什么需要它</b>：上传路径已经改成流式（{@code FileUploadService} 把
 * {@code Flux<DataBuffer>} 直接交给策略），但对象存储的客户端大多要求
 * 「一个 InputStream + 长度」。早先的写法是 {@code DataBufferUtils.join(整个流)} ——
 * 等于把流式又退化成「整个文件进堆」，批量上传 4 并发的堆占用是 4 × 文件大小。
 *
 * <p>这里改成落到临时文件：堆占用只与缓冲区大小有关，与文件大小无关。
 * 临时文件由调用方在用完后 {@link SpooledFile#close()} 删除。
 *
 * @author ryu
 */
@Slf4j
public final class StreamSpool {

    private StreamSpool() {
    }

    /**
     * 落盘后的临时文件。
     *
     * @param path 临时文件路径
     * @param size 实际字节数
     */
    public record SpooledFile(Path path, long size) {

        /**
         * 删除临时文件；失败只记日志。
         */
        public void close() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("删除上传临时文件失败: path={}", path, e);
            }
        }
    }

    /**
     * 把内容流写入临时文件。
     *
     * <p>失败时临时文件会被删掉 —— 否则每次失败都留一个 GB 级垃圾。
     *
     * @param content 内容流
     * @return 临时文件信息
     */
    public static Mono<SpooledFile> spool(Flux<DataBuffer> content) {
        return Mono.fromCallable(() -> Files.createTempFile("ryu-upload-", ".spool"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> writeTo(path, content));
    }

    private static Mono<SpooledFile> writeTo(Path path, Flux<DataBuffer> content) {
        return Mono.fromCallable(() -> Files.newOutputStream(path,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(out -> DataBufferUtils.write(content, out)
                        // 写的是普通 OutputStream，onNext 发生在订阅线程上，
                        // 必须切到弹性线程池，不能占用事件循环
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(Mono.fromCallable(() -> {
                            out.close();
                            return new SpooledFile(path, Files.size(path));
                        }).subscribeOn(Schedulers.boundedElastic()))
                        // 成功路径已经关过；这里保证失败路径也关掉 ——
                        // Windows 上删一个还开着的文件会失败，临时文件就留下了
                        .doFinally(signal -> closeQuietly(out)))
                .onErrorResume(error -> {
                    log.warn("上传内容落盘失败，删除临时文件: path={}", path, error);
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException cleanupError) {
                        log.warn("删除上传临时文件失败: path={}", path, cleanupError);
                    }
                    return Mono.error(error);
                });
    }

    /**
     * 关闭输出流（异常路径用）。
     */
    static void closeQuietly(OutputStream out) {
        try {
            out.close();
        } catch (IOException e) {
            log.warn("关闭上传临时文件输出流失败", e);
        }
    }
}
