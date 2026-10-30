package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 角色更新 DTO
 *
 * <p><b>类型统一</b>：{@code isActive} 原先是 {@code Integer}（0/1），
 * 而实体 {@code Role.isActive} 是 {@code Boolean} —— Service 里靠
 * {@code getIsActive() == 1} 手工转换，语义混乱（传 2 会被当成 false）。
 * 现在统一为 {@code Boolean}。
 *
 * @author ryu
 * @since 1.0.0
 */
@Data
@Schema(description = "角色更新DTO")
public class RoleUpdateDTO {

    /**
     * 角色ID
     */
    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    private String name;

    /**
     * 角色编码
     */
    @Schema(description = "角色编码")
    private String code;

    /**
     * 角色描述
     */
    @Schema(description = "角色描述")
    private String description;

    /**
     * 角色排序
     */
    @Schema(description = "角色排序")
    private Integer sort;

    /**
     * 是否激活
     */
    @Schema(description = "是否激活")
    private Boolean isActive;
}
