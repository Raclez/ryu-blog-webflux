package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体类基类
 * 提供通用的审计字段和生命周期管理
 * 
 * @author ryu 475118582@qq.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @Id
    private Long id;
    
    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;
    
    /**
     * 删除标记：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Boolean isDeleted;
    
    /**
     * 创建前回调
     * 设置创建时间、更新时间和删除标记的默认值
     */
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createTime == null) {
            this.createTime = now;
        }
        if (this.updateTime == null) {
            this.updateTime = now;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }
    
    /**
     * 更新前回调
     * 更新更新时间
     */
    public void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
