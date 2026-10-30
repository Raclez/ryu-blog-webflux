package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户角色关联实体类
 * 用于实现用户与角色的多对多关联
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"id"})
@Table("t_user_roles")
public class UserRole implements Serializable {
    private static final long serialVersionUID = 1L;

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
     * 角色ID
     */
    @Column("role_id")
    private Long roleId;

    /**
     * 创建时间
     *
     * <p>带默认值：{@code t_user_roles.create_time} 是 {@code NOT NULL} 且没有
     * DEFAULT，而 Spring Data R2DBC 会把 null 属性<b>显式写成 NULL</b>
     * （数据库 DEFAULT 不生效）—— 谁忘了 set 就会插入失败。
     * 本类没有继承 {@code BaseEntity}（那张表没有 is_deleted 列），
     * 所以默认值只能自己给。{@code @Builder.Default} 是必须的：
     * Lombok 的 {@code @Builder} 会忽略字段初始值。
     */
    @Builder.Default
    @Column("create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 更新时间
     */
    @Builder.Default
    @Column("update_time")
    private LocalDateTime updateTime = LocalDateTime.now();
} 