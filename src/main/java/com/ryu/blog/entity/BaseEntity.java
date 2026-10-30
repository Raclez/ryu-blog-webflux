package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
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
 * <p><b>为什么审计字段带默认值</b>：
 * Spring Data R2DBC 会把实体的 null 属性<b>显式写成 NULL</b>（反编译
 * {@code MappingR2dbcConverter.writeProperties} 确认：值为 null 时走
 * {@code writeNullInternal} → {@code Parameter.empty(type)}），
 * 数据库的 DEFAULT <b>完全不会生效</b>。
 * 而本类原先的 {@code onCreate()} / {@code onUpdate()} 没有 {@code @BeforeConvert}
 * 注解、也没有注册 {@code BeforeConvertCallback} —— <b>从未被执行过</b>。
 * 结果就是：谁忘了手动赋值，插入时就是 NULL。
 *
 * <p>典型后果：{@code t_role_permissions.is_deleted} 是 {@code NOT NULL}，
 * 而关联表的 Service 都没设这个字段 → {@code POST /roles/assign-permissions}
 * 必然报 {@code Column 'is_deleted' cannot be null}。
 *
 * <p>所以这里直接在字段上给默认值：<b>实体一被创建，审计字段就是可用的</b>，
 * 不依赖任何回调机制，也不会写出 NULL。
 *
 * @author ryu
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
     *
     * <p>默认值在实体构造时写入 —— 避免 R2DBC 把它作为 NULL 插入
     * （{@code create_time} 在多数表上是 {@code NOT NULL} 且没有 DEFAULT）。
     *
     * <p>{@code @Builder.Default} 是必须的：Lombok 的 {@code @SuperBuilder}
     * 会忽略字段初始值，不加这个注解时 builder 建出来的实体会是 null。
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

    /**
     * 删除标记：false-未删除，true-已删除
     *
     * <p>默认 false —— 避免插入 NULL 让「未删除」的过滤条件把记录全滤掉。
     */
    @Builder.Default
    @Column("is_deleted")
    private Boolean isDeleted = Boolean.FALSE;

    /**
     * 补齐审计字段（创建前调用）
     *
     * <p><b>注意</b>：这个方法<b>不会</b>被 Spring Data 自动调用 ——
     * 它需要实现 {@code BeforeConvertCallback} 并注册成 Bean 才会生效，
     * 而那要按实体类型逐个注册（{@code BaseEntity} 的子类不共享一个回调）。
     * 现在审计字段靠字段默认值兜底，这个方法保留给需要显式刷新的场景。
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
            this.isDeleted = Boolean.FALSE;
        }
    }

    /**
     * 更新更新时间（更新前调用）
     */
    public void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
