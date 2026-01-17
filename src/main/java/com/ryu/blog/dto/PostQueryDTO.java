package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.*;

/**
 * 文章查询数据传输对象
 * @author ryu
 */
@Data
@Schema(description = "文章查询数据传输对象")
public class PostQueryDTO {
    
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    @Max(value = 10000, message = "页码不能超过10000")
    @Schema(description = "当前页码", example = "1")
    private Integer currentPage;
    
    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    @Schema(description = "每页数量", example = "10")
    private Integer pageSize;
    
    @Size(max = 200, message = "标题长度不能超过200个字符")
    @Schema(description = "文章标题（模糊查询）", example = "Spring")
    private String title;
    
    @Min(value = 0, message = "状态值只能为0、1或2")
    @Max(value = 2, message = "状态值只能为0、1或2")
    @Schema(description = "文章状态：0-草稿，1-已发布，2-回收站", example = "1")
    private Integer status;

    @Positive(message = "分类ID必须为正数")
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", message = "开始时间格式必须为yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", example = "2024-01-01 00:00:00")
    private String startTime;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", message = "结束时间格式必须为yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间", example = "2024-12-31 23:59:59")
    private String endTime;
} 