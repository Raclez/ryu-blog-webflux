package com.ryu.blog.strategy;

import reactor.core.publisher.Mono;

/**
 * 可选能力：生成带签名的预览 / 下载 URL。
 *
 * @author ryu
 */
public interface PresignCapable {

    /**
     * 生成文件预览URL
     *
     * @param path          文件路径
     * @param expireSeconds 过期时间(秒)
     * @return 预览URL
     */
    Mono<String> generatePreviewUrl(String path, long expireSeconds);

    /**
     * 生成文件下载URL
     *
     * @param path          文件路径
     * @param expireSeconds 过期时间(秒)
     * @return 下载URL
     */
    Mono<String> generateDownloadUrl(String path, long expireSeconds);
}
