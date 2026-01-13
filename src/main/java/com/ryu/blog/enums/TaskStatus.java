package com.ryu.blog.enums;

/**
 * 任务状态枚举
 * 
 * @author ryu
 */
public enum TaskStatus {
    /**
     * 等待中
     */
    PENDING,
    
    /**
     * 处理中
     */
    PROCESSING,
    
    /**
     * 已完成
     */
    COMPLETED,
    
    /**
     * 失败
     */
    FAILED,
    
    /**
     * 已取消
     */
    CANCELLED
}
