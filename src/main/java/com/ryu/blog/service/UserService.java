package com.ryu.blog.service;

import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.dto.UserPasswordDTO;
import com.ryu.blog.entity.User;
import com.ryu.blog.vo.UserInfoVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 用户服务接口
 * @author ryu 475118582@qq.com
 */
public interface UserService {

    /**
     * 创建用户
     * @param user 用户信息
     * @return 创建结果
     */
    Mono<User> createUser(User user);
    
    /**
     * 创建用户并分配角色
     * @param userDTO 用户信息
     * @return 创建结果
     */
    Mono<User> createUserWithRoles(UserDTO userDTO);

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户信息
     */
    Mono<User> getUserById(Long id);
    
    /**
     * 获取当前登录用户信息
     * @param userId 用户ID
     * @return 用户信息视图对象
     */
    Mono<UserInfoVO> getCurrentUserInfo(Long userId);
    
    /**
     * 获取用户详细信息
     * @param id 用户ID
     * @return 用户信息视图对象
     */
    Mono<UserInfoVO> getUserDetailById(Long id);

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    Mono<User> getUserByUsername(String username);

    /**
     * 用户注册
     * @param user 用户信息
     * @return 注册结果
     */
    Mono<User> register(User user);

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    Mono<String> login(String username, String password);

    /**
     * 更新用户信息
     * @param user 用户信息
     * @return 更新结果
     */
    Mono<User> updateUser(User user);
    
    /**
     * 更新用户信息及角色
     * @param userDTO 用户信息
     * @return 更新结果
     */
    Mono<User> updateUserWithRoles(UserDTO userDTO);
    
    /**
     * 更新用户密码
     * @param userId 用户ID
     * @param passwordDTO 密码信息
     * @return 更新结果
     */
    Mono<Boolean> updatePassword(Long userId, UserPasswordDTO passwordDTO);

    /**
     * 删除用户
     * @param id 用户ID
     * @return 删除结果
     */
    Mono<Void> deleteUser(Long id);

    /**
     * 批量删除用户
     * @param ids 用户ID列表
     * @return 删除结果
     */
    Mono<Void> batchDeleteUsers(List<Long> ids);

    /**
     * 分页查询用户列表
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表
     */
    Flux<User> listUsers(int page, int size);

    /**
     * 分页条件查询用户
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param username 用户名（可选）
     * @param email 邮箱（可选）
     * @param status 状态（可选）
     * @return 用户分页数据
     */
    Mono<Map<String, Object>> getUserPage(int page, int size, String username, String email, Integer status);

    /**
     * 重置用户密码
     * @param id 用户ID
     * @return 新密码
     */
    Mono<String> resetPassword(Long id);

    /**
     * 查询用户总数
     * @return 用户总数
     */
    Mono<Long> countUsers();

    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return 是否存在
     */
    Mono<Boolean> checkUsernameExists(String username);

    /**
     * 检查邮箱是否存在
     * @param email 邮箱
     * @return 是否存在
     */
    Mono<Boolean> checkEmailExists(String email);
    
    /**
     * 检查手机号是否存在
     * @param phone 手机号
     * @return 是否存在
     */
    Mono<Boolean> checkPhoneExists(String phone);

    /**
     * 更新最后登录信息
     * @param id 用户ID
     * @param ip 登录IP
     * @return 更新结果
     */
    Mono<Integer> updateLastLogin(Long id, String ip);

    /**
     * 更新用户状态
     * @param id 用户ID
     * @param status 状态
     * @return 更新结果
     */
    Mono<Integer> updateUserStatus(Long id, Integer status);

    /**
     * 更新用户角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 更新结果
     */
    Mono<Void> updateUserRoles(Long userId, List<Long> roleIds);
} 