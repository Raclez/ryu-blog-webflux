package com.ryu.blog.strategy;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件存储策略的<b>核心</b>能力。
 *
 * <p><b>为什么只剩 6 个方法</b>：这里只放「任何一个能用的策略都必须做到」的事 ——
 * 上传、下载、删除、拿到公开 URL、报出自己是谁、以及诚实地说自己有没有实现。
 * 其余能力（缩略图 / 签名 URL / 校验和 / 元数据 / 分片 / 批量删除）都是<b>可选</b>的，
 * 各自一个接口（{@link ThumbnailCapable}、{@link PresignCapable}、
 * {@link ChecksumCapable}、{@link MetadataCapable}、{@link MultipartCapable}、
 * {@link BatchDeleteCapable}），调用方用 {@link StorageCapabilities} 取，
 * 取不到就在调用前抛 {@code BusinessException}。
 *
 * <p>早先这 17 个方法混在一起，「不支持」有三种表达（空流 / false /
 * {@code UnsupportedOperationException}），调用方在编译期什么都看不出来。
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
     * 这个策略<b>是否真的实现了</b>
     *
     * <p>占位实现最糟糕的地方不是不能用，而是<b>看起来能用</b>：
     * 上传返回一个路径、删除返回成功、文件永远「存在」，用户要等到下载时才发现问题。
     * 所以未实现的策略必须自报 {@code false}，由注册表把它们从可选列表里剔掉，
     * 并在「启用 / 创建配置」时被拒绝。
     *
     * <p>默认 {@code true} —— 真实实现的策略不需要额外声明。
     *
     * @return 已实现返回 true
     */
    default boolean isImplemented() {
        return true;
    }

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
     * 获取文件的公共永久URL（不需要身份验证，适用于博客图片等公开内容）
     * @param path 文件路径
     * @return 公共永久URL
     */
    Mono<String> getPublicUrl(String path);
}
