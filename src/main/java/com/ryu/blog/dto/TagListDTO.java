package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 标签分页查询条件
 *
 * <p>和 {@link CategoryListDTO} 同样的问题：{@code int} 字段既没有默认值也没有
 * 校验注解，客户端漏传 {@code pageSize} 就绑定成 0，
 * {@code PageRequest.of(page, 0)} 抛异常 → 500。现在补默认值与上下界。
 */
@Data
@Schema(description = "标签列表查询")
public class TagListDTO {

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
