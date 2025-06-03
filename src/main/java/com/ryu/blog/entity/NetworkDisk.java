package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网络磁盘表，用于用户的网盘存储
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_network_disk")
public class NetworkDisk implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录唯一标识
     */
    @Id
    private Long id;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 文件路径，相对于用户网盘根目录
     */
    @Column("file_path")
    private String filePath;

    /**
     * 文件类型(文件夹-folder, 文件-file)
     */
    @Column("file_type")
    private String fileType;

    /**
     * 文件大小(字节)，文件夹为0
     */
    @Column("file_size")
    private Long fileSize;

    /**
     * 文件MIME类型
     */
    @Column("mime_type")
    private String mimeType;

    /**
     * 父目录ID，根目录为NULL
     */
    @Column("parent_id")
    private Long parentId;

    /**
     * 实际存储文件的ID，文件夹为NULL
     */
    @Column("storage_file_id")
    private Long storageFileId;

    /**
     * 是否为收藏(0-否, 1-是)
     */
    @Column("is_favorite")
    private Integer isFavorite;

    /**
     * 文件状态(1-正常, 0-已删除, 2-回收站)
     */
    private Integer status;

    /**
     * 文件备注
     */
    private String remark;

    /**
     * 删除时间，用于回收站清理
     */
    @Column("delete_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deleteTime;

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
} 