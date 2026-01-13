package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 任务优先级枚举
 * 
 * @author ryu
 */
@Getter
public enum TaskPriority {
    /**
     * 高优先级
     */
    HIGH(1),
    
    /**
     * 普通优先级
     */
    NORMAL(2),
    
    /**
     * 低优先级
     */
    LOW(3);
    
    private final int level;
    
    TaskPriority(int level) {
        this.level = level;
    }
}
