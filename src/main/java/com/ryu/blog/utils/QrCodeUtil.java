package com.ryu.blog.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具类
 * 
 * @author ryu 475118582@qq.com
 */
@Slf4j
public class QrCodeUtil {
    
    private static final String DEFAULT_FORMAT = "PNG";
    private static final String BASE64_PREFIX = "data:image/png;base64,";
    private static final int DEFAULT_SIZE = 200;
    
    /**
     * 生成二维码
     * 
     * @param content 二维码内容
     * @param width 宽度
     * @param height 高度
     * @return BitMatrix对象
     * @throws WriterException 生成异常
     */
    private static BitMatrix generateQrCodeMatrix(String content, int width, int height) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);
        
        return new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
    }
    
    /**
     * 生成二维码并保存到文件
     * 
     * @param content 二维码内容
     * @param filePath 保存路径
     * @param width 宽度
     * @param height 高度
     * @return 是否成功
     */
    public static boolean generateToFile(String content, String filePath, int width, int height) {
        try {
            BitMatrix bitMatrix = generateQrCodeMatrix(content, width, height);
            Path path = FileSystems.getDefault().getPath(filePath);
            MatrixToImageWriter.writeToPath(bitMatrix, DEFAULT_FORMAT, path);
            return true;
        } catch (Exception e) {
            log.error("生成二维码文件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 生成二维码并保存到文件（使用默认尺寸）
     * 
     * @param content 二维码内容
     * @param filePath 保存路径
     * @return 是否成功
     */
    public static boolean generateToFile(String content, String filePath) {
        return generateToFile(content, filePath, DEFAULT_SIZE, DEFAULT_SIZE);
    }
    
    /**
     * 生成二维码返回BufferedImage对象
     * 
     * @param content 二维码内容
     * @param width 宽度
     * @param height 高度
     * @return BufferedImage对象
     */
    public static BufferedImage generateToImage(String content, int width, int height) {
        try {
            BitMatrix bitMatrix = generateQrCodeMatrix(content, width, height);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (WriterException e) {
            log.error("生成二维码图片失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成二维码返回BufferedImage对象（使用默认尺寸）
     * 
     * @param content 二维码内容
     * @return BufferedImage对象
     */
    public static BufferedImage generateToImage(String content) {
        return generateToImage(content, DEFAULT_SIZE, DEFAULT_SIZE);
    }
    
    /**
     * 生成二维码并返回Base64编码
     * 
     * @param content 二维码内容
     * @param width 宽度
     * @param height 高度
     * @return Base64编码的二维码图片
     */
    public static String generateToBase64(String content, int width, int height) {
        BufferedImage image = generateToImage(content, width, height);
        if (image == null) {
            return null;
        }
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, DEFAULT_FORMAT, outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return BASE64_PREFIX + base64;
        } catch (IOException e) {
            log.error("二维码转Base64失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成二维码并返回Base64编码（使用默认尺寸）
     * 
     * @param content 二维码内容
     * @return Base64编码的二维码图片
     */
    public static String generateToBase64(String content) {
        return generateToBase64(content, DEFAULT_SIZE, DEFAULT_SIZE);
    }
    
    /**
     * 生成带Logo的二维码
     * 
     * @param content 二维码内容
     * @param logoPath Logo图片路径
     * @param width 宽度
     * @param height 高度
     * @return 带Logo的BufferedImage对象
     */
    public static BufferedImage generateWithLogo(String content, String logoPath, int width, int height) {
        try {
            // 生成二维码
            BufferedImage qrImage = generateToImage(content, width, height);
            if (qrImage == null) {
                return null;
            }
            
            // 读取Logo图片
            File logoFile = new File(logoPath);
            if (!logoFile.exists()) {
                log.warn("Logo文件不存在: {}", logoPath);
                return qrImage;
            }
            
            // 设置Logo大小，不超过二维码尺寸的1/5
            BufferedImage logoImage = ImageIO.read(logoFile);
            int logoWidth = qrImage.getWidth() / 5;
            int logoHeight = qrImage.getHeight() / 5;
            
            // 计算Logo放置位置，居中
            int x = (qrImage.getWidth() - logoWidth) / 2;
            int y = (qrImage.getHeight() - logoHeight) / 2;
            
            // 绘制Logo
            Graphics2D graphics = qrImage.createGraphics();
            graphics.setComposite(AlphaComposite.SrcAtop);
            graphics.drawImage(logoImage, x, y, logoWidth, logoHeight, null);
            graphics.dispose();
            
            return qrImage;
        } catch (Exception e) {
            log.error("生成带Logo的二维码失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成带Logo的二维码并返回Base64编码
     * 
     * @param content 二维码内容
     * @param logoPath Logo图片路径
     * @param width 宽度
     * @param height 高度
     * @return Base64编码的带Logo二维码图片
     */
    public static String generateWithLogoToBase64(String content, String logoPath, int width, int height) {
        BufferedImage image = generateWithLogo(content, logoPath, width, height);
        if (image == null) {
            return null;
        }
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, DEFAULT_FORMAT, outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return BASE64_PREFIX + base64;
        } catch (IOException e) {
            log.error("带Logo二维码转Base64失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成带Logo的二维码并返回Base64编码（使用默认尺寸）
     * 
     * @param content 二维码内容
     * @param logoPath Logo图片路径
     * @return Base64编码的带Logo二维码图片
     */
    public static String generateWithLogoToBase64(String content, String logoPath) {
        return generateWithLogoToBase64(content, logoPath, DEFAULT_SIZE, DEFAULT_SIZE);
    }
} 