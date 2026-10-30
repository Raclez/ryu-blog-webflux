package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 角色列表查询条件
 *
 * <p>原先没有任何校验注解，于是：
 * <ul>
 *   <li>{@code currentPage = 0} → Service 里算出负的 {@code skip}
 *       → {@code Stream.skip(负数)} 抛 {@code IllegalArgumentException} → 500；</li>
 *   <li>{@code pageSize} 无上限 → 一次拉全表进内存。</li>
 * </ul>
 *
 * @author ryu
 * @since 1.0.0
 */
@Data
@Schema(description = "角色列表查询条件")
public class RoleListDTO {

    @Min(value = 1, message = "页码不能小于 1")
    @Max(value = 10000, message = "页码不能超过 10000")
    @Schema(description = "当前页码，从 1 开始")
    private Long currentPage = 1L;

    @Min(value = 1, message = "每页大小不能小于 1")
    @Max(value = 100, message = "每页大小不能超过 100")
    @Schema(description = "每页大小")
    private Long pageSize = 10L;

    @Schema(description = "角色名称，模糊匹配")
    private String name;
}
