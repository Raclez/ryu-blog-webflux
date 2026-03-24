package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 文件版本表，记录文件的版本历史信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_versions")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class FileVersion extends BaseEntity {

    /**
     * 文件ID，关联files表
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 版本号
     */
    @Column("version_number")
    private Integer versionNumber;

    /**
     * 版本标签
     */
    @Column("version_tag")
    private String versionTag;

    /**
     * 文件路径
     */
    @Column("file_path")
    private String filePath;

    /**
     * 文件大小(字节)
     */
    @Column("file_size")
    private Long fileSize;

    /**
     * 文件校验码
     */
    private String checksum;

    /**
     * 存储类型(local, minio, oss等)
     */
    @Column("storage_type")
    private String storageType;

    /**
     * 创建者ID
     */
    @Column("creator_id")
    private Long creatorId;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 是否是当前版本(0:否, 1:是)
     */
    @Column("is_current")
    private Boolean isCurrent;

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
     * MIME类型
     */
    @Column("mime_type")
    private String mimeType;
} 