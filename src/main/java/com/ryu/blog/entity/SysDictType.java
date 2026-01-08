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
 * 系统字典类型实体类
 * 
 * 数据库索引：
 * - uk_dict_type: 唯一索引 (dict_type)
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, of = {"id"})
@Table("t_sys_dict_types")
public class SysDictType extends BaseEntity {

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
} 