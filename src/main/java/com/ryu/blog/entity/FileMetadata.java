package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 文件元数据表，存储文件的扩展信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_metadata")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class FileMetadata extends BaseEntity {

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
} 