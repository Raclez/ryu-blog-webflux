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
 * 系统字典项实体类
 * 
 * 数据库索引：
 * - uk_dict_type_key: 联合唯一索引 (dict_type_id, dict_item_key)
 * - idx_dict_type_id: 普通索引 (dict_type_id)
 * - idx_status: 普通索引 (status)
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"id"})
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
     * 数据库应有索引：idx_dict_type_id
     * 数据库应有联合唯一索引：uk_dict_type_key (dict_type_id, dict_item_key)
     */
    @Column("dict_type_id")
    private Long dictTypeId;

    /**
     * 字典项键
     */
    @Column("dict_item_key")
    @Size(max = 100)
    private String dictItemKey;
    
    /**
     * 字典项值
     */
    @Column("dict_item_value")
    @Size(max = 200)
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