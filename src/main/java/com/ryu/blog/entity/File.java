package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件实体类，用于存储文件的基本信息
 * 
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file")
public class File implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @Id
    private Long id;

    /**
     * 文件名
     */
    @Column("file_name")
    private String fileName;

    /**
     * 文件路径
     */
    @Column("file_path")
    private String filePath;

    /**
     * 文件大小，单位字节
     */
    @Column("file_size")
    private Long fileSize;

    /**
     * 文件类型（如：image、video、text等）
     */
    @Column("file_type")
    private String fileType;

    /**
     * 文件的 MIME 类型
     */
    @Column("mime_type")
    private String mimeType;

    /**
     * 上传时间
     */
    @Column("upload_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;

    /**
     * 上传用户ID
     */
    @Column("creator_id")
    private Long creatorId;

    /**
     * 文件状态，0: 未激活, 1: 激活, 2: 已删除
     */
    @Column("status")
    private Integer status;

    /**
     * 文件存储类型（如：local、oss、minio等）
     */
    @Column("storage_type")
    private String storageType;

    /**
     * 文件URL
     */
    @Column("cdn_url")
    private String cdnUrl;

    /**
     * 文件校验码（MD5）
     */
    @Column("checksum")
    private String checksum;

    /**
     * 文件描述
     */
    @Column("description")
    private String description;

    /**
     * 文件访问权限类型：0-公开(所有人可读), 1-私有(仅创建者), 2-自定义(指定用户)，3-链接访问
     */
    @Column("access_type")
    private Integer accessType;

    /**
     * 分享访问秘钥，用于链接分享
     */
    @Column("share_key")
    private String shareKey;

    /**
     * 分享访问过期时间
     */
    @Column("share_expire_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shareExpireTime;

    /**
     * 是否有缩略图
     */
    @Column("has_thumbnail")
    private Boolean hasThumbnail;

    /**
     * 缩略图路径
     */
    @Column("thumbnail_path")
    private String thumbnailPath;

    /**
     * 创建时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;
} 