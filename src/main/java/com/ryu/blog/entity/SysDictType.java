package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统字典类型实体类
 *
 * @author ryu 475118582@qq.com
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
@NoArgsConstructor
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
     */
    @Column("dict_type")
    private String dictType;
    
    /**
     * 字典名称
     */
    @Column("type_name")
    private String typeName;

    /**
     * 状态：1 启用, 0 禁用
     */
    private Integer status;
    
    /**
     * 备注
     */
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