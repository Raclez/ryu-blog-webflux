package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

/**
 * 文件详细信息视图对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileInfoVO {
    
    /**
     * 文件ID
     */
    private Long id;
    
    /**
     * 文件名称
     */
    private String fileName;
    
    /**
     * 文件的可直接访问地址
     *
     * <p>= {@code strategy.getPublicUrl(存储路径)}：对象存储是
     * {@code {endpoint}/{bucket}/{object}}，本地存储 / 未配 accessUrl 时兜底成
     * 应用内公开路由 {@code /files/public/{path}}。
     *
     * <p><b>这是唯一能直接塞进 {@code <img src>} / {@code <video src>} 的字段</b>
     * —— {@link #previewUrl} / {@link #downloadUrl} 都要 {@code Authorization} 头。
     */
    private String filePath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 格式化后的文件大小
     */
    private String formattedSize;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * MIME类型
     */
    private String mimeType;
    
    /**
     * 上传时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    
    /**
     * 上传用户ID
     */
    private Long creatorId;
    
    /**
     * 上传用户名称
     */
    private String creatorName;
    
    /**
     * 文件状态
     */
    private Integer status;
    
    /**
     * 存储类型
     */
    private String storageType;
    
    /**
     * 文件校验码
     */
    private String checksum;
    
    /**
     * 文件描述
     */
    private String description;
    
    
    /**
     * 文件预览链接（应用内按 ID 的路由 {@code /files/preview/{id}}）
     *
     * <p>需要 {@code Authorization} 头 + {@code system:file:view} 权限，
     * 响应带 {@code nosniff} / {@code CSP: sandbox}。
     * <b>不能</b>当图片地址用 —— {@code <img>} 带不了请求头。要展示图片用 {@link #filePath}。
     */
    private String previewUrl;
    
    /**
     * 文件下载链接（{@code /files/download/{id}}），鉴权要求同 {@link #previewUrl}，
     * 响应多一个 {@code Content-Disposition: attachment}
     */
    private String downloadUrl;
    
    /**
     * 文件元数据
     */
    private Map<String, Object> metadata;
    
   
    /**
     * 文件图标
     */
    private String fileIcon;
    
    /**
     * 缩略图URL
     */
    private String thumbnailUrl;
    
    
    /**
     * 创建时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
} 