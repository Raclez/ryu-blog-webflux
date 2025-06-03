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
 * 文件元数据表，存储文件的扩展信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_metadata")
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 元数据记录的唯一标识
     */
    @Id
    private Long id;

    /**
     * 对应的文件ID
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 元数据类型（例如：image, document, audio, video等）
     */
    @Column("metadata_type")
    private String metadataType;

    /**
     * 元数据键
     */
    @Column("metadata_key")
    private String metadataKey;

    /**
     * 元数据值
     */
    @Column("metadata_value")
    private String metadataValue;

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