package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 *
 * <p><b>为什么不能直接绑定 {@code User} 实体</b>：
 * {@code User} 继承 {@code BaseEntity}，其中的 {@code id} 会被 JSON 反序列化进来。
 * 而 Spring Data R2DBC 的 {@code save()} 在 {@code id != null} 时走 <b>UPDATE</b> 分支，
 * 于是
 * <pre>{@code POST /auth/register {"id":1,"username":"attacker","password":"x"}}</pre>
 * 会直接把 {@code t_users} 中 id=1 那一行（admin）的用户名与密码一起改写 ——
 * <b>未认证即可接管任意账号</b>。
 *
 * <p>专用 DTO 从契约上就不接受 {@code id} / {@code status} / {@code isDeleted} 这类
 * 服务端掌控的字段，mass-assignment 的口子一并堵上。
 *
 * @author ryu
 * @since 1.0.0
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度须在 3-50 之间")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须在 6-64 之间")
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    @Schema(description = "邮箱")
    private String email;

    @Size(max = 50, message = "昵称长度不能超过 50")
    @Schema(description = "昵称")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Size(max = 255, message = "头像地址过长")
    @Schema(description = "头像")
    private String avatar;

    @Size(max = 500, message = "个人简介过长")
    @Schema(description = "个人简介")
    private String bio;
}
