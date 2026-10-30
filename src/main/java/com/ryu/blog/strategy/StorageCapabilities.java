package com.ryu.blog.strategy;

import com.ryu.blog.exception.BusinessException;

/**
 * 取得策略的可选能力，拿不到就<b>在调用前</b>拒绝。
 *
 * <p><b>为什么要有这一层</b>：早先所有能力都挂在 {@link FileStorageStrategy} 上，
 * 不支持的策略靠「返回空流 / 返回 false / 抛 {@code UnsupportedOperationException}」
 * 来表达，三种写法三种语义，调用方只能运行到一半才发现问题。
 * 现在能力是独立的接口，取不到就直接抛 {@link BusinessException} ——
 * 错误在进入业务逻辑之前就发生，不会留下半条已执行的链路。
 *
 * @author ryu
 */
public final class StorageCapabilities {

    private StorageCapabilities() {
    }

    /**
     * 取分片上传能力
     *
     * @param strategy 存储策略
     * @return 分片能力
     * @throws BusinessException 策略不支持分片上传
     */
    public static MultipartCapable multipart(FileStorageStrategy strategy) {
        return require(strategy, MultipartCapable.class, "分片上传");
    }

    /**
     * 取校验和计算能力
     *
     * @param strategy 存储策略
     * @return 校验和能力
     * @throws BusinessException 策略不支持计算校验和
     */
    public static ChecksumCapable checksum(FileStorageStrategy strategy) {
        return require(strategy, ChecksumCapable.class, "计算校验和");
    }

    /**
     * 取元数据能力
     *
     * @param strategy 存储策略
     * @return 元数据能力
     * @throws BusinessException 策略不支持读元数据
     */
    public static MetadataCapable metadata(FileStorageStrategy strategy) {
        return require(strategy, MetadataCapable.class, "读取文件元数据");
    }

    /**
     * 取签名 URL 能力
     *
     * @param strategy 存储策略
     * @return 签名 URL 能力
     * @throws BusinessException 策略不支持生成签名 URL
     */
    public static PresignCapable presign(FileStorageStrategy strategy) {
        return require(strategy, PresignCapable.class, "生成签名 URL");
    }

    /**
     * 取缩略图能力
     *
     * @param strategy 存储策略
     * @return 缩略图能力
     * @throws BusinessException 策略不支持生成缩略图
     */
    public static ThumbnailCapable thumbnail(FileStorageStrategy strategy) {
        return require(strategy, ThumbnailCapable.class, "生成缩略图");
    }

    /**
     * 取批量删除能力
     *
     * @param strategy 存储策略
     * @return 批量删除能力
     * @throws BusinessException 策略不支持批量删除
     */
    public static BatchDeleteCapable batchDelete(FileStorageStrategy strategy) {
        return require(strategy, BatchDeleteCapable.class, "批量删除");
    }

    private static <T> T require(FileStorageStrategy strategy, Class<T> capability, String description) {
        if (strategy == null) {
            throw new BusinessException("没有可用的存储策略，无法" + description);
        }
        if (!capability.isInstance(strategy)) {
            throw new BusinessException(
                    "当前存储策略（" + strategy.getStrategyKey() + "）不支持" + description);
        }
        return capability.cast(strategy);
    }
}
