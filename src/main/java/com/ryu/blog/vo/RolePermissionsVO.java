package com.ryu.blog.vo;

import com.ryu.blog.entity.Permissions;
import com.ryu.blog.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 角色权限视图对象
 * 用于返回角色及其拥有的权限信息
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "角色权限视图对象")
public class RolePermissionsVO {
    
    @Schema(description = "角色信息")
    private Role role;
    
    @Schema(description = "权限列表")
    private List<Permissions> permissions;
}