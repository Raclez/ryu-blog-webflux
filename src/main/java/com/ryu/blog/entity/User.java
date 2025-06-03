package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储用户的详细信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_users")
public class User implements Serializable {
    private static final long serialVersionUID = -4407973270614999668L;
    
    /**
     * 用户的唯一标识
     */
    @Id
    private Long id;

    /**
     * 用户名，必须唯一
     */
    private String username;

    /**
     * 用户的电子邮件地址，必须唯一
     */
    private String email;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户手机号
     */
    private String phone;

    /**
     * 用户密码的哈希值
     */
    private String password;

    /**
     * 用户的简介或个人说明
     */
    private String bio;

    /**
     * 用户头像的URL
     */
    private String avatar;

    /**
     * 用户状态 0-禁用 1-正常 2-锁定
     */
    private Integer status;

    /**
     * 用户创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 用户信息的最后更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 最后登录时间
     */
    @Column("last_login_time")
    private LocalDateTime lastLoginTime ;
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;


    private String  lastLoginIp;
}