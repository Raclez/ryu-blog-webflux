package com.ryu.blog.strategy;

import reactor.core.publisher.Mono;

/**
 * 可选能力：生成缩略图。
 *
 * @author ryu
 */
public interface ThumbnailCapable {

    /**
     * 生成缩略图
     *
     * @param filePath      原文件路径
     * @param thumbnailPath 缩略图路径
     * @param width         宽度
     * @param height        高度
     * @return 缩略图路径
     */
    Mono<String> generateThumbnail(String filePath, String thumbnailPath, int width, int height);
}
