package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分类分页查询条件
 *
 * <p>原先 {@code currentPage} / {@code pageSize} 是没有默认值、也没有校验注解的
 * {@code int}，客户端只要不传 {@code pageSize} 就会绑定成 0，
 * Service 里的 {@code PageRequest.of(page, 0)} 直接抛
 * {@code IllegalArgumentException: Page size must not be less than one} → 500。
 *
 * <p>现在补上默认值（1 / 10）和上下界，越界参数由 {@code @Valid} 挡在 Controller 层，
 * 返回 400 而不是 500。
 */
@Data
@Schema(description = "分类分页查询条件")
public class CategoryListDTO {

    @Min(value = 1, message = "页码最小为1")
    @Max(value = 10000, message = "页码最大为10000")
    @Schema(description = "当前页", example = "1")
    private int currentPage = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    @Schema(description = "每页条数", example = "10")
    private int pageSize = 10;

    @Schema(description = "分类的关键字")
    private String keyword;
}
