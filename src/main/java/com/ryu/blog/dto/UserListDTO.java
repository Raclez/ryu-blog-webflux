package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户列表查询参数
 *
 * <p>分页参数补上上下界：原先 {@code size=0} 会让
 * {@code PageRequest.of(page, 0)} 抛异常 → 500，而没有上界则能一次拉全表。
 *
 * <p>字段名沿用 {@code current} / {@code size}（前端已经在用），
 * 与其它 DTO 的 {@code currentPage} / {@code pageSize} 不一致，属于已知的命名分歧。
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "用户列表查询参数")
public class UserListDTO {

    @Min(value = 1, message = "页码最小为1")
    @Max(value = 10000, message = "页码最大为10000")
    @Schema(description = "当前页码")
    private Integer current = 1;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量")
    private Integer size = 10;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "状态")
    private Integer status;
} 