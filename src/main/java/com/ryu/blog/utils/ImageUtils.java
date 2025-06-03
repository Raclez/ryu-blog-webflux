package com.ryu.blog.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 图片处理工具类，支持生成缩略图
 * 依赖：Thumbnailator
 *
 * @author ryu
 */
@Slf4j
public class ImageUtils {
    /**
     * 生成图片缩略图
     * @param imageData 原始图片字节数组
     * @param width 缩略图宽度
     * @param height 缩略图高度
     * @return 缩略图字节数组，失败返回null
     */
    public static byte[] generateThumbnail(byte[] imageData, int width, int height) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(imageData);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.of(in)
                    .size(width, height)
                    .outputQuality(0.85f)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("生成缩略图失败", e);
            return null;
        }
    }
} 