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
 * 系统字典项实体类
 *
 * @author ryu 475118582@qq.com
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
@NoArgsConstructor
@Table("t_sys_dict_items")
public class SysDictItem implements Serializable {

    private static final long serialVersionUID = 1L;
    
    /**
     * 字典项唯一标识
     */
    @Id
    private Long id;
    
    /**
     * 所属字典类型ID
     */
    @Column("dict_type_id")
    private Long dictTypeId;

    /**
     * 字典项键
     */
    @Column("dict_item_key")
    private String dictItemKey;
    
    /**
     * 字典项值
     */
    @Column("dict_item_value")
    private String dictItemValue;
    
    /**
     * 排序字段
     */
    private Integer sort;
    
    /**
     * 状态：1 启用, 0 禁用
     */
    private Integer status;
    
    /**
     * 语言标识
     */
    private String lang;
    
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