package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 文件权限表，用于存储文件的权限信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_permissions")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class FilePermission extends BaseEntity {

    /**
     * 文件的ID
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 授权权限掩码，使用位掩码存储：1-读, 3-写(含读), 7-删除(含读写), 8-分享
     */
    @Column("permission_mask")
    private Integer permissionMask;

    /**
     * 过期时间，null表示永不过期
     */
    @Column("expire_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    
    /**
     * 授权人ID，谁分配的该权限
     */
    @Column("grantor_id")
    private Long grantorId;
    
    /**
     * 获取对应的权限类型枚举
     * 非数据库字段，仅用于代码中便捷使用
     * 
     * @return 权限类型枚举，如果没有匹配则返回null
     */
    @Transient
    @JsonIgnore
    public PermissionType getPermissionType() {
        if (permissionMask == null) {
            return null;
        }
        
        return PermissionType.fromMask(permissionMask);
    }
    
    /**
     * 检查是否有指定权限
     * 
     * @param requiredMask 需要检查的权限掩码
     * @return 是否拥有权限
     */
    @JsonIgnore
    public boolean hasPermission(Integer requiredMask) {
        if (permissionMask == null || requiredMask == null) {
            return false;
        }
        return (permissionMask & requiredMask) == requiredMask;
    }
    
    /**
     * 检查是否有指定权限类型
     * 
     * @param requiredType 需要检查的权限类型
     * @return 是否拥有权限
     */
    @JsonIgnore
    public boolean hasPermission(PermissionType requiredType) {
        if (permissionMask == null || requiredType == null) {
            return false;
        }
        return (permissionMask & requiredType.getMask()) == requiredType.getMask();
    }
} 