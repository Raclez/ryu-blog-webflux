package com.ryu.blog.strategy;

import reactor.core.publisher.Mono;

/**
 * 可选能力：计算文件校验和（用于按内容去重）。
 *
 * @author ryu
 */
public interface ChecksumCapable {

    /**
     * 计算文件校验和
     *
     * <p><b>算不出来时必须如实返回空串</b> —— 返回随机值会让去重静默失效，
     * 而且随机值会被当成真实校验和落库，事后无从分辨。
     *
     * @param path 文件路径
     * @return 校验和；不支持时为空串
     */
    Mono<String> calculateFileChecksum(String path);
}
