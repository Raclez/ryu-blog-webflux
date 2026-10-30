package com.ryu.blog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户数据传输对象
 *
 * <p><b>刻意不包含</b>这些服务端掌控的字段：
 * <ul>
 *   <li>{@code status} —— 账号状态（禁用/锁定）走专用的
 *       {@code PUT /users/{id}/status/{status}}，那道门有独立的
 *       {@code system:user:status} 权限。放在这里就等于「改资料」顺手改了状态，
 *       把那道闸门绕过去了（MapStruct 会自动映射同名字段）。</li>
 *   <li>{@code lastLogin} / {@code lastLoginIp} —— 登录痕迹由服务端写，
 *       客户端能填就等于能伪造审计信息。</li>
 *   <li>{@code id} —— 以路径参数为准（{@code PUT /users/edit/{id}}），
 *       请求体里的值一律忽略。</li>
 * </ul>
 *
 * <p>新增用户时状态一律为「正常」（{@code 1}），需要禁用在建号后再调状态接口。
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "用户数据传输对象")
public class UserDTO {

    @Schema(description = "用户ID（以路径参数为准，请求体里的值会被忽略）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "昵称")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "个人简介")
    private String bio;

    @Schema(description = "角色ID列表")
    private List<Long> roleIds;
} 