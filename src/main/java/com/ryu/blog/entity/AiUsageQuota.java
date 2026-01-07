package com.ryu.blog.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * AI使用配额实体类
 * 
 * 数据库索引：
 * - uk_user: 唯一索引 (user_id)
 * - idx_role: 普通索引 (role_id)
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
@Table("t_ai_usage_quota")
public class AiUsageQuota {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 角色ID（可选，用于角色级配额）
     */
    @Column("role_id")
    private Long roleId;

    /**
     * 每小时限制次数
     */
    @Column("hourly_limit")
    private Integer hourlyLimit;

    /**
     * 每日限制次数
     */
    @Column("daily_limit")
    private Integer dailyLimit;

    /**
     * 每月限制次数
     */
    @Column("monthly_limit")
    private Integer monthlyLimit;

    /**
     * 每小时已用次数
     */
    @Column("hourly_used")
    private Integer hourlyUsed;

    /**
     * 每日已用次数
     */
    @Column("daily_used")
    private Integer dailyUsed;

    /**
     * 每月已用次数
     */
    @Column("monthly_used")
    private Integer monthlyUsed;

    /**
     * 上次重置小时时间
     */
    @Column("last_reset_hour")
    private LocalDateTime lastResetHour;

    /**
     * 上次重置日期时间
     */
    @Column("last_reset_day")
    private LocalDateTime lastResetDay;

    /**
     * 上次重置月份时间
     */
    @Column("last_reset_month")
    private LocalDateTime lastResetMonth;

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
}
