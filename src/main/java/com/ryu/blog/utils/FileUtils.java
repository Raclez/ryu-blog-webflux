package com.ryu.blog.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件工具类 - 提供文件操作的通用方法
 * 
 * 该工具类包括以下功能：
 * 1. 文件类型处理（获取扩展名、Content-Type、文件类型）
 * 2. 文件名处理（生成唯一文件名、规范化路径）
 * 3. 文件校验（计算MD5、校验文件类型和大小）
 * 4. 安全处理（生成令牌）
 * 5. 文件大小格式化
 * 6. 文件类型判断（是否为图片、视频、音频等）
 * 7. 文件预览支持判断
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
public class FileUtils {

    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();
    private static final int BUFFER_SIZE = 8192;

    static {
        // 初始化MIME类型映射
        MIME_TYPE_MAP.put("jpg", "image/jpeg");
        MIME_TYPE_MAP.put("jpeg", "image/jpeg");
        MIME_TYPE_MAP.put("png", "image/png");
        MIME_TYPE_MAP.put("gif", "image/gif");
        MIME_TYPE_MAP.put("webp", "image/webp");
        MIME_TYPE_MAP.put("bmp", "image/bmp");
        MIME_TYPE_MAP.put("svg", "image/svg+xml");
        
        MIME_TYPE_MAP.put("mp4", "video/mp4");
        MIME_TYPE_MAP.put("avi", "video/x-msvideo");
        MIME_TYPE_MAP.put("wmv", "video/x-ms-wmv");
        MIME_TYPE_MAP.put("flv", "video/x-flv");
        MIME_TYPE_MAP.put("mov", "video/quicktime");
        MIME_TYPE_MAP.put("mkv", "video/x-matroska");
        
        MIME_TYPE_MAP.put("mp3", "audio/mpeg");
        MIME_TYPE_MAP.put("wav", "audio/wav");
        MIME_TYPE_MAP.put("ogg", "audio/ogg");
        MIME_TYPE_MAP.put("flac", "audio/flac");
        
        MIME_TYPE_MAP.put("pdf", "application/pdf");
        MIME_TYPE_MAP.put("doc", "application/msword");
        MIME_TYPE_MAP.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPE_MAP.put("xls", "application/vnd.ms-excel");
        MIME_TYPE_MAP.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPE_MAP.put("ppt", "application/vnd.ms-powerpoint");
        MIME_TYPE_MAP.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        
        MIME_TYPE_MAP.put("txt", "text/plain");
        MIME_TYPE_MAP.put("html", "text/html");
        MIME_TYPE_MAP.put("css", "text/css");
        MIME_TYPE_MAP.put("js", "application/javascript");
        MIME_TYPE_MAP.put("json", "application/json");
        MIME_TYPE_MAP.put("xml", "application/xml");
        MIME_TYPE_MAP.put("md", "text/markdown");
        
        MIME_TYPE_MAP.put("zip", "application/zip");
        MIME_TYPE_MAP.put("rar", "application/x-rar-compressed");
        MIME_TYPE_MAP.put("7z", "application/x-7z-compressed");
        MIME_TYPE_MAP.put("tar", "application/x-tar");
        MIME_TYPE_MAP.put("gz", "application/gzip");
    }

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 扩展名（不包含点号）
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 获取文件的Content-Type
     *
     * @param fileName 文件名
     * @return Content-Type
     */
    public static String getContentType(String fileName) {
        String extension = getFileExtension(fileName);
        if (extension.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        return MIME_TYPE_MAP.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * 解析最大文件大小字符串，支持 KB/MB/GB/字节
     * @param maxSizeStr 配置字符串
     * @return 字节数
     */
    public static long getMaxSize(String maxSizeStr) {
        if (maxSizeStr == null || maxSizeStr.isEmpty()) return 100 * 1024 * 1024L;
        String size = maxSizeStr.trim().toLowerCase();
        try {
            if (size.endsWith("kb")) return Long.parseLong(size.replace("kb", "").trim()) * 1024;
            if (size.endsWith("mb")) return Long.parseLong(size.replace("mb", "").trim()) * 1024 * 1024;
            if (size.endsWith("gb")) return Long.parseLong(size.replace("gb", "").trim()) * 1024 * 1024 * 1024;
            return Long.parseLong(size);
        } catch (Exception e) {
            log.warn("解析最大文件大小失败: {}，使用默认100MB", maxSizeStr);
            return 100 * 1024 * 1024L;
        }
    }

    /**
     * 生成唯一文件名（yyyyMMddHHmmssUUID8位.扩展名，无任何分隔符）
     * @param originalFileName 原始文件名
     * @return 唯一文件名
     */
    public static String generateUniqueFileName(String originalFileName) {
        String ext = getFileExtension(originalFileName);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
        return ext.isEmpty() ? (timestamp + uuid) : (timestamp + uuid + "." + ext);
    }

    /**
     * 规范化路径，移除开头和结尾的斜杠
     *
     * @param path 路径
     * @return 规范化后的路径
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        
        // 移除开头的斜杠
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        // 移除结尾的斜杠
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }

    /**
     * 拼接路径，确保路径之间有且仅有一个斜杠
     * @param basePath 基础路径
     * @param paths 子路径
     * @return 完整路径
     */
    public static String joinPath(String basePath, String... paths) {
        StringBuilder result = new StringBuilder(normalizePath(basePath));
        
        for (String path : paths) {
            String normalizedPath = normalizePath(path);
            if (!normalizedPath.isEmpty()) {
                if (result.length() > 0) {
                    result.append("/");
                }
                result.append(normalizedPath);
            }
        }
        
        return result.toString();
    }

    /**
     * 计算文件的MD5校验和
     *
     * @param bytes 文件字节数组
     * @return MD5校验和
     */
    public static String calculateMD5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.error("计算MD5失败", e);
            return "";
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 获取文件类型（如：image、video、document等）
     *
     * @param mimeType MIME类型
     * @return 文件类型
     */
    public static String getFileType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return "unknown";
        }
        
        if (mimeType.startsWith("image/")) {
            return "image";
        } else if (mimeType.startsWith("video/")) {
            return "video";
        } else if (mimeType.startsWith("audio/")) {
            return "audio";
        } else if (mimeType.startsWith("text/")) {
            return "text";
        } else if (mimeType.equals("application/pdf")) {
            return "pdf";
        } else if (mimeType.contains("word") || mimeType.contains("opendocument.text")) {
            return "document";
        } else if (mimeType.contains("excel") || mimeType.contains("spreadsheet")) {
            return "spreadsheet";
        } else if (mimeType.contains("powerpoint") || mimeType.contains("presentation")) {
            return "presentation";
        } else if (mimeType.contains("zip") || mimeType.contains("compressed") || mimeType.contains("archive")) {
            return "archive";
        } else if (mimeType.equals("application/json")) {
            return "json";
        } else if (mimeType.equals("application/xml") || mimeType.equals("text/xml")) {
            return "xml";
        } else if (mimeType.equals("text/markdown")) {
            return "markdown";
        }
        
        return "other";
    }

    /**
     * 使用Apache Tika识别MIME类型，优先内容识别
     * @param data 文件内容
     * @param fileName 文件名
     * @return MIME类型
     */
    public static String getMimeType(byte[] data, String fileName) {
        try (InputStream is = new ByteArrayInputStream(data)) {
            Tika tika = new Tika();
            String mime = tika.detect(is, fileName);
            return mime != null ? mime : getContentType(fileName);
        } catch (Exception e) {
            log.warn("Tika识别MIME失败，降级为扩展名: {}", e.getMessage());
            return getContentType(fileName);
        }
    }

    /**
     * 检查文件扩展名是否允许
     * @param fileName 文件名
     * @param allowed 允许的扩展名列表
     * @return 是否允许
     */
    public static boolean isAllowedExtension(String fileName, List<String> allowed) {
        String ext = getFileExtension(fileName);
        return allowed == null || allowed.isEmpty() || allowed.contains("*") || allowed.contains(ext.toLowerCase());
    }

    /**
     * 文件名安全化，去除特殊字符
     * @param fileName 文件名
     * @return 安全文件名
     */
    public static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 判断是否为图片（基于MIME）
     */
    public static boolean isImage(String mime) { return mime != null && mime.startsWith("image/"); }
    /**
     * 判断是否为视频（基于MIME）
     */
    public static boolean isVideo(String mime) { return mime != null && mime.startsWith("video/"); }
    /**
     * 判断是否为音频（基于MIME）
     */
    public static boolean isAudio(String mime) { return mime != null && mime.startsWith("audio/"); }
    /**
     * 判断是否为文档（基于MIME）
     */
    public static boolean isDocument(String mime) {
        return mime != null && (mime.contains("pdf") || mime.contains("word") || mime.contains("excel") || mime.contains("presentation"));
    }

    /**
     * 生成临时文件路径
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 临时文件路径
     */
    public static String getTempFilePath(String prefix, String suffix) {
        try {
            java.io.File temp = java.io.File.createTempFile(prefix, suffix);
            temp.deleteOnExit();
            return temp.getAbsolutePath();
        } catch (IOException e) {
            log.error("创建临时文件失败", e);
            return null;
        }
    }

    /**
     * 校验文件名是否合法（不包含特殊字符且长度合理）
     * @param fileName 文件名
     * @return 是否合法
     */
    public static boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.length() > 255) return false;
        return !fileName.matches(".*[\\\\/:*?\"<>|].*");
    }

    /**
     * 计算SHA-256哈希
     * @param data 数据
     * @return SHA-256字符串
     */
    public static String calculateSHA256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算SHA-256失败", e);
            return "";
        }
    }

    /**
     * 计算指定算法的哈希值
     * @param data 数据
     * @param algorithm 算法名（如MD5、SHA-256）
     * @return 哈希字符串
     */
    public static String calculateHash(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算哈希失败: {}", algorithm, e);
            return "";
        }
    }

    /**
     * 计算文件校验和
     * 
     * @param data 文件数据
     * @return 校验和（MD5）
     */
    public static String calculateChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算文件校验和失败", e);
            return "";
        }
    }

    /**
     * 格式化文件大小
     *
     * @param size 文件大小(字节)
     * @return 格式化后的文件大小
     */
    public static String formatFileSize(Long size) {
        if (size == null) {
            return "0 B";
        }
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 检查文件类型是否支持预览
     * 
     * @param fileType 文件类型
     * @return 是否支持预览
     */
    public static boolean isFilePreviewable(String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return false;
        }
        
        // 支持预览的文件类型
        List<String> previewableTypes = Arrays.asList(
                "image", "pdf", "text", "video", "audio", "html", "markdown", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
        );
        
        for (String type : previewableTypes) {
            if (fileType.toLowerCase().contains(type)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 生成安全令牌（用于文件预览、下载等）
     * 
     * @param data 令牌数据
     * @return 安全令牌
     */
    public static String generateSecureToken(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("生成令牌失败: {}", e.getMessage());
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
    
    /**
     * 检查文件是否允许上传（基于类型和大小）
     *
     * @param fileName 文件名
     * @param size 文件大小（字节）
     * @param maxSize 最大允许大小（字节）
     * @param allowedExtensions 允许的扩展名列表
     * @return 是否允许上传，如果不允许，第二个参数包含原因
     */
    public static Map.Entry<Boolean, String> isFileAllowed(String fileName, long size, long maxSize, List<String> allowedExtensions) {
        // 检查文件名是否合法
        if (!isValidFileName(fileName)) {
            return Map.entry(false, "文件名不合法");
        }
        
        // 检查文件大小是否超过限制
        if (size > maxSize) {
            return Map.entry(false, "文件大小超过限制: " + formatFileSize(size) + " > " + formatFileSize(maxSize));
        }
        
        // 检查文件扩展名是否允许
        if (!isAllowedExtension(fileName, allowedExtensions)) {
            return Map.entry(false, "不支持的文件类型: " + getFileExtension(fileName));
        }
        
        return Map.entry(true, "");
    }
    
    /**
     * 从路径中提取文件名
     * @param path 文件路径
     * @return 文件名
     */
    public static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        int lastSlashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlashIndex >= 0 && lastSlashIndex < path.length() - 1) {
            return path.substring(lastSlashIndex + 1);
        }
        
        return path;
    }
    
    /**
     * 从路径中提取目录
     * @param path 文件路径
     * @return 目录路径
     */
    public static String extractDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        int lastSlashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlashIndex > 0) {
            return path.substring(0, lastSlashIndex);
        }
        
        return "";
    }
    
    /**
     * 构建完整的URL
     * @param baseUrl 基础URL
     * @param path 路径
     * @return 完整URL
     */
    public static String buildUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return path;
        }
        
        if (path == null || path.isEmpty()) {
            return baseUrl;
        }
        
        // 确保baseUrl以/结尾，path不以/开头
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        
        return normalizedBaseUrl + normalizedPath;
    }
    
    /**
     * 生成随机文件名（保留扩展名）
     * @param originalFileName 原始文件名
     * @return 随机文件名
     */
    public static String generateRandomFileName(String originalFileName) {
        String ext = getFileExtension(originalFileName);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return ext.isEmpty() ? uuid : (uuid + "." + ext);
    }
} 