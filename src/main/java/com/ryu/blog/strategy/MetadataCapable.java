package com.ryu.blog.strategy;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 可选能力：读取文件元数据 / 判断文件是否存在。
 *
 * @author ryu
 */
public interface MetadataCapable {

    /**
     * 检查文件是否存在
     *
     * @param path 文件路径
     * @return 是否存在
     */
    Mono<Boolean> fileExists(String path);

    /**
     * 获取文件元数据
     *
     * @param path 文件路径
     * @return 元数据信息
     */
    Mono<Map<String, Object>> getMetadata(String path);
}
