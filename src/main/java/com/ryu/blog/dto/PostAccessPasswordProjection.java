package com.ryu.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章访问密码投影
 *
 * <p>只取 {@code id} 与 {@code password} 两列 —— 迁移脚本要把历史明文洗成哈希，
 * 但**不需要**把其它列也拉出来（正文、SEO 元数据这些字段完全用不上）。
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostAccessPasswordProjection {
    /** 文章ID */
    private Long id;
    /** 存下来的访问密码（可能是明文，也可能是 BCrypt 哈希） */
    private String password;
}
