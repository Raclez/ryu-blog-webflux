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
 * 文件日志表，用于记录文件的操作日志
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_logs")
public class FileLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志记录的唯一标识
     */
    @Id
    private Long id;

    /**
     * 对应文件ID
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 文件操作类型（如：upload、delete、download等）
     */
    private String operation;

    /**
     * 执行操作的用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 操作的时间
     */
    @Column("timestamp")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 操作的详细信息（如错误信息、操作参数等）
     */
    private String details;

    /**
     * 操作的描述
     */
    private String description;
} 