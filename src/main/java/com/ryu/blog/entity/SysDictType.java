package com.ryu.blog.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统字典类型实体类
 * 
 * 数据库索引：
 * - uk_dict_type: 唯一索引 (dict_type)
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"id"})
@Table("t_sys_dict_types")
public class SysDictType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 字典类型ID
     */
    @Id
    private Long id;

    /**
     * 字典编码，唯一
     * 数据库应有唯一索引：uk_dict_type
     */
    @Column("dict_type")
    @Size(max = 100)
    private String dictType;
    
    /**
     * 字典名称
     */
    @Column("type_name")
    @Size(max = 100)
    private String typeName;

    /**
     * 状态：1 启用, 0 禁用
     */
    private Integer status;
    
    /**
     * 备注
     */
    @Size(max = 500)
    private String remark;
    
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
     * 是否删除：1 已删除，0 未删除
     */
    @Column("is_deleted")
    private Integer isDeleted;
} 