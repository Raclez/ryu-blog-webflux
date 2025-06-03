package com.ryu.blog.entity;

/**
 * 权限类型枚举
 * 
 * @author ryu 475118582@qq.com
 */
public enum PermissionType {
    /**
     * 读权限
     */
    READ(1, "读取"),
    
    /**
     * 写权限（包含读权限）
     */
    WRITE(3, "修改"),
    
    /**
     * 删除权限（包含读写权限）
     */
    DELETE(7, "删除"),
    
    /**
     * 分享权限
     */
    SHARE(8, "分享"),
    
    /**
     * 所有权限
     */
    ALL(15, "所有权限");
    
    private final int mask;
    private final String description;
    
    PermissionType(int mask, String description) {
        this.mask = mask;
        this.description = description;
    }
    
    /**
     * 获取权限掩码值
     * 
     * @return 权限掩码
     */
    public int getMask() {
        return mask;
    }
    
    /**
     * 获取权限描述
     * 
     * @return 权限描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据掩码获取权限类型
     * 
     * @param mask 权限掩码
     * @return 权限类型，如果没有匹配则返回null
     */
    public static PermissionType fromMask(int mask) {
        for (PermissionType type : values()) {
            if (type.getMask() == mask) {
                return type;
            }
        }
        return null;
    }
} 