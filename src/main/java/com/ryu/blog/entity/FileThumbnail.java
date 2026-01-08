package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 文件缩略图表，存储文件的缩略图信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_thumbnails")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class FileThumbnail extends BaseEntity {

    /**
     * 对应的文件ID
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 缩略图大小 (宽度x高度)
     */
    private String size;

    /**
     * 缩略图宽度
     */
    private Integer width;

    /**
     * 缩略图高度
     */
    private Integer height;

    /**
     * 缩略图文件路径
     */
    @Column("file_path")
    private String filePath;

    /**
     * 缩略图文件大小(字节)
     */
    @Column("file_size")
    private Long fileSize;

    /**
     * 缩略图存储类型(local, minio, oss等)
     */
    @Column("storage_type")
    private String storageType;

    /**
     * 缩略图格式(例如 jpg, png)
     */
    private String format;
} 