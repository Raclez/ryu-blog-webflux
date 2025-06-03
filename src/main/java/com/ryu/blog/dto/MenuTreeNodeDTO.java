package com.ryu.blog.dto;

import lombok.Data;

import java.util.List;

@Data

public class MenuTreeNodeDTO {
    private Long id;
    private Long parentId;
    private String name;
    private String url;
    private String icon;
    private Integer menuType;
    private Integer isActive;
    private Integer sort;
    private String component;
    // 其他基本属性...

    private List<Long> permissionIds; // 直接包含权限ID列表
    private List<MenuTreeNodeDTO> children; // 子菜单
}