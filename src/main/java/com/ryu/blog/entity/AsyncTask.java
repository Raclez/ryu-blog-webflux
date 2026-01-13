package com.ryu.blog.entity;

import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 异步任务实体
 * 
 * @author ryu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table("t_async_tasks")
public class AsyncTask extends BaseEntity {
    
    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;
    
    /**
     * 任务类型
     */
    @Column("task_type")
    private TaskType taskType;
    
    /**
     * 任务状态
     */
    @Column("status")
    private TaskStatus status;
    
    /**
     * 任务优先级
     */
    @Column("priority")
    private TaskPriority priority;
    
    /**
     * 请求参数（JSON）
     */
    @Column("request_json")
    private String requestJson;
    
    /**
     * 任务结果（JSON）
     */
    @Column("result_json")
    private String resultJson;
    
    /**
     * 错误信息
     */
    @Column("error_message")
    private String errorMessage;
    
    /**
     * 进度百分比（0-100）
     */
    @Column("progress")
    private Integer progress;
    
    /**
     * 提交时间
     */
    @Column("submit_time")
    private LocalDateTime submitTime;
    
    /**
     * 开始执行时间
     */
    @Column("start_time")
    private LocalDateTime startTime;
    
    /**
     * 完成时间
     */
    @Column("complete_time")
    private LocalDateTime completeTime;
    
    /**
     * 重试次数
     */
    @Column("retry_count")
    private Integer retryCount;
    
    /**
     * 最大重试次数
     */
    @Column("max_retries")
    private Integer maxRetries;
    
    /**
     * 获取任务ID（使用主键ID）
     * 
     * @return 任务ID
     */
    public String getTaskId() {
        return getId() != null ? getId().toString() : null;
    }
}
