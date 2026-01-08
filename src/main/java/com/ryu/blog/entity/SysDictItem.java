package com.ryu.blog.entity;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, of = {"id"})
@Table("t_sys_dict_items")
public class SysDictItem extends BaseEntity {
    
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
} 