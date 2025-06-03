package com.ryu.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupFileQueryDTO {

    private Long groupId;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Long currentPage;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    private Long pageSize;
} 