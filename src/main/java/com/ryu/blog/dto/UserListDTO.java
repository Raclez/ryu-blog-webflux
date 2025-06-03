package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户列表查询参数
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "用户列表查询参数")
public class UserListDTO {
    
    @Schema(description = "当前页码")
    private Integer current = 1;
    
    @Schema(description = "每页数量")
    private Integer size = 10;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "状态")
    private Integer status;
} 