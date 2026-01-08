package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 存储用户的详细信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_users")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class User extends BaseEntity {
    
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
     * 最后登录时间
     */
    @Column("last_login_time")
    private LocalDateTime lastLoginTime;
    
    /**
     * 最后登录IP
     */
    private String lastLoginIp;
}