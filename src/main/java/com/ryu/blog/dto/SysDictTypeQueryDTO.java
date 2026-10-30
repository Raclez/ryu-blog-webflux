package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 字典类型查询DTO
 * <p>
 * 用于封装字典类型查询条件
 * </p>
 *
 * <p>分页参数补上上下界：原先 {@code pageSize=0} 会让
 * {@code PageRequest.of(page, 0)} 抛异常 → 500。
 *
 * @author Ryu
 * @since 2025-01-24
 */
@Data
@Schema(description = "字典类型查询条件")
public class SysDictTypeQueryDTO {

    /**
     * 当前页码
     */
    @Min(value = 1, message = "页码最小为1")
    @Max(value = 10000, message = "页码最大为10000")
    private Long currentPage = 1L;

    /**
     * 每页记录数
     */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Long pageSize = 10L;

    /**
     * 类型名称
     */
    private String typeName;

    /**
     * 类型编码
     */
    private String typeCode;
}
