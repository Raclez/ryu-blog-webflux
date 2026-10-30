package com.ryu.blog.vo;

import lombok.Builder;
import lombok.Getter;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

/**
 * 文件下载结果
 *
 * <p>Service 层只负责产出内容流与描述信息，HTTP 响应（ResponseEntity / Header）
 * 由 Controller 组装，避免 Web 层类型渗入 Service 契约。
 *
 * <p>内容以 {@link Flux<DataBuffer>} 形式返回，配合 WebFlux 的背压机制，
 * 文件内容不会在内存里整体驻留。
 *
 * @author ryu 475118582@qq.com
 */
@Getter
@Builder
public class FileDownloadVO {

    /**
     * 文件内容流
     */
    private final Flux<DataBuffer> content;

    /**
     * 原始文件名
     */
    private final String fileName;

    /**
     * MIME 类型
     */
    private final String contentType;

    /**
     * 文件大小（字节），未知时为 null
     */
    private final Long contentLength;

    /**
     * Content-Disposition：attachment 表示下载，inline 表示预览
     */
    private final String disposition;
}
