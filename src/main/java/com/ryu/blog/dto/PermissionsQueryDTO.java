package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 权限分页查询条件
 *
 * <p>原先 {@code currentPage} / {@code pageSize} 是没有校验注解也没有上限的
 * {@code Long}：Service 里虽然给 null 兜了默认值（1 / 10），但客户端显式传
 * {@code pageSize=0} 时 {@code PageRequest.of(page, 0)} 会抛
 * {@code IllegalArgumentException} → 500；而 {@code pageSize=999999} 则会把整张权限表
 * 拉进内存。现在加上上下界，并在 Controller 上补 {@code @Valid} 让约束真正生效。
 */
@Data
@Schema(description = "权限分页查询条件")
public class PermissionsQueryDTO {

    @Min(value = 1, message = "页码最小为1")
    @Max(value = 10000, message = "页码最大为10000")
    @Schema(description = "当前页码", example = "1")
    private Long currentPage = 1L;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    @Schema(description = "每页条数", example = "10")
    private Long pageSize = 10L;

    @Schema(description = "权限名称，模糊匹配")
    private String name;

    @Schema(description = "权限标识，模糊匹配")
    private String identity;

    /**
     * 模块前缀，用于按模块查询权限
     */
    @Schema(description = "模块前缀", example = "user")
    private String modulePrefix;
}
