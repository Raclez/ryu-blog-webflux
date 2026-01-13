package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 任务类型枚举
 * 
 * @author ryu
 */
@Getter
public enum TaskType {
    /**
     * AI内容生成
     */
    AI_GENERATION("AI内容生成"),
    
    /**
     * 邮件发送
     */
    EMAIL("邮件发送"),
    
    /**
     * 报表生成
     */
    REPORT("报表生成"),
    
    /**
     * 数据导出
     */
    DATA_EXPORT("数据导出"),
    
    /**
     * 文件处理
     */
    FILE_PROCESS("文件处理");
    
    private final String description;
    
    TaskType(String description) {
        this.description = description;
    }
}
