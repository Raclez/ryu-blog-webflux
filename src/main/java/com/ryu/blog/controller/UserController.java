package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.dto.UserListDTO;
import com.ryu.blog.dto.UserPasswordDTO;
import com.ryu.blog.service.UserService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.UserInfoVO;
import com.ryu.blog.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户管理控制器
 * @author ryu 475118582@qq.com
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户管理相关接口")
public class UserController {

    private final UserService userService;

    /**
     * 分页查询用户列表
     */
    @Operation(summary = "分页查询用户列表", description = "根据条件获取用户分页列表")
    @GetMapping("/page")
    public Mono<Result<PageResult<UserVO>>> getUserPage(@ParameterObject @Validated UserListDTO userListDTO) {
        return userService.getUserPage(
                userListDTO.getCurrent() - 1, 
                userListDTO.getSize(), 
                userListDTO.getUsername(), 
                userListDTO.getEmail(), 
                userListDTO.getStatus()
            )
            .map(Result::success)
            .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 获取当前登录用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户信息")
    @GetMapping("/info")
    public Mono<Result<UserInfoVO>> getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        return userService.getCurrentUserInfo(userId)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 根据ID获取用户详细信息
     */
    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @GetMapping("/detail/{id}")
    public Mono<Result<UserInfoVO>> getUserDetail(@PathVariable Long id) {
        return userService.getUserDetailById(id)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 创建新用户
     */
    @Operation(summary = "创建新用户", description = "创建新用户并可选地分配角色")
    @PostMapping("/save")
    public Mono<Result<String>> createUser(@Validated @RequestBody UserDTO userDTO) {
        return userService.createUserWithRoles(userDTO)
                .map(u -> Result.success("用户创建成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 更新用户信息
     */
    @Operation(summary = "更新用户信息", description = "更新指定ID用户的信息")
    @PutMapping("/edit/{id}")
    public Mono<Result<String>> updateUserInfo(@PathVariable Long id, @Validated @RequestBody UserDTO userDTO) {
        userDTO.setId(id);
        return userService.updateUserWithRoles(userDTO)
                .map(u -> Result.success("用户信息更新成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 修改密码
     */
    @Operation(summary = "修改密码", description = "修改当前登录用户密码")
    @PutMapping("/password")
    public Mono<Result<?>> updatePassword(@Validated @RequestBody UserPasswordDTO passwordDTO) {
        if (!passwordDTO.getNewPassword().equals(passwordDTO.getConfirmPassword())) {
            return Mono.just(Result.badRequest("新密码和确认密码不一致"));
        }
        
        long userId = StpUtil.getLoginIdAsLong();
        return userService.updatePassword(userId, passwordDTO)
                .map(success -> success ? Result.success("密码修改成功") : Result.error("旧密码错误"))
                .onErrorResume(e -> Mono.just(Result.error("")));
    }
    
    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "删除指定ID的用户及其关联的角色关系")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<String>> deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id)
                .thenReturn(Result.success("用户删除成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
    
    /**
     * 批量删除用户
     */
    @Operation(summary = "批量删除用户", description = "批量删除多个用户及其关联的角色关系")
    @DeleteMapping("/batch")
    public Mono<Result<String>> batchDeleteUsers(@RequestBody List<Long> ids) {
        return userService.batchDeleteUsers(ids)
                .thenReturn(Result.success("批量删除用户成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
    
    /**
     * 重置用户密码
     */
    @Operation(summary = "重置用户密码", description = "管理员重置指定用户的密码")
    @PutMapping("/reset-password/{id}")
    public Mono<Result<String>> resetPassword(@PathVariable Long id) {
        return userService.resetPassword(id)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
    
    /**
     * 更新用户状态
     */
    @Operation(summary = "更新用户状态", description = "更新用户状态")
    @PutMapping("/{id}/status/{status}")
    public Mono<Result<String>> updateUserStatus(
            @PathVariable Long id, 
            @PathVariable Integer status) {
        return userService.updateUserStatus(id, status)
                .map(i -> Result.success("用户状态更新成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
    
    /**
     * 更新用户角色（管理员功能）
     */
    @Operation(summary = "更新用户角色", description = "分配用户角色（管理员功能）")
    @PutMapping("/{id}/roles")
    public Mono<Result<String>> updateUserRoles(
            @PathVariable Long id, 
            @RequestBody List<Long> roleIds) {
        return userService.updateUserRoles(id, roleIds)
                .thenReturn(Result.success("角色更新成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
}