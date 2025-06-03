package com.ryu.blog.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.dto.UserPasswordDTO;
import com.ryu.blog.entity.Role;
import com.ryu.blog.entity.User;
import com.ryu.blog.entity.UserRole;
import com.ryu.blog.mapper.UserMapper;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.UserService;
import com.ryu.blog.utils.CryptoUtil;
import com.ryu.blog.vo.UserInfoVO;
import com.ryu.blog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 用户服务实现类
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final UserMapper userMapper;
    
    private static final String USER_CACHE_KEY = "user:";
    private static final String USER_LIST_CACHE_KEY = "user:list:";
    private static final String USER_COUNT_CACHE_KEY = "user:count";

    @Override
    @Transactional
    public Mono<User> createUser(User user) {
        // 检查用户名是否存在
        return checkUsernameExists(user.getUsername())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new RuntimeException("用户名已存在"));
                    }
                    
                    // 检查邮箱是否存在（如果有）
                    if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                        return checkEmailExists(user.getEmail())
                                .flatMap(emailExists -> {
                                    if (Boolean.TRUE.equals(emailExists)) {
                                        return Mono.error(new RuntimeException("邮箱已存在"));
                                    }
                                    return createUserInternal(user);
                                });
                    }
                    
                    return createUserInternal(user);
                });
    }

    private Mono<User> createUserInternal(User user) {
        // 设置默认值
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setIsDeleted(0);
        
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        
        // 使用HuTool的SecureUtil加密密码
        user.setPassword(SecureUtil.md5(user.getPassword()));
        
        return userRepository.save(user)
                .doOnSuccess(savedUser -> {
                    // 清除缓存
                    clearUserCache(savedUser.getId());
                    reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).subscribe();
                });
    }

    @Override
    @Transactional
    public Mono<User> updateUser(User user) {
        return userRepository.findById(user.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("用户不存在")))
                .flatMap(existingUser -> {
                    // 如果用户名有变化，需要检查是否已存在
                    if (user.getUsername() != null && !user.getUsername().equals(existingUser.getUsername())) {
                        return checkUsernameExists(user.getUsername())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(new RuntimeException("用户名已存在"));
                                    }
                                    return updateUserFields(existingUser, user);
                                });
                    }
                    
                    // 如果邮箱有变化，需要检查是否已存在
                    if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
                        return checkEmailExists(user.getEmail())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(new RuntimeException("邮箱已存在"));
                                    }
                                    return updateUserFields(existingUser, user);
                                });
                    }
                    
                    return updateUserFields(existingUser, user);
                });
    }

    private Mono<User> updateUserFields(User existingUser, User user) {
        // 更新基本信息
        if (user.getUsername() != null) {
            existingUser.setUsername(user.getUsername());
        }
        if (user.getNickname() != null) {
            existingUser.setNickname(user.getNickname());
        }
        if (user.getAvatar() != null) {
            existingUser.setAvatar(user.getAvatar());
        }
        if (user.getEmail() != null) {
            existingUser.setEmail(user.getEmail());
        }
        if (user.getPhone() != null) {
            existingUser.setPhone(user.getPhone());
        }
        if (user.getBio() != null) {
            existingUser.setBio(user.getBio());
        }
        if (user.getStatus() != null) {
            existingUser.setStatus(user.getStatus());
        }
        
        // 如果有新密码，使用HuTool的SecureUtil加密后更新
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(SecureUtil.md5(user.getPassword()));
        }
        
        existingUser.setUpdateTime(LocalDateTime.now());
        
        return userRepository.save(existingUser)
                .doOnSuccess(savedUser -> {
                    // 清除缓存
                    clearUserCache(savedUser.getId());
                });
    }

    @Override
    public Mono<User> getUserById(Long id) {
        // 先尝试从缓存中获取
        String key = USER_CACHE_KEY + id;
        return reactiveRedisTemplate.opsForValue().get(key)
                .cast(User.class)
                .switchIfEmpty(
                        userRepository.findById(id)
                                .switchIfEmpty(Mono.error(new RuntimeException("用户不存在")))
                                .flatMap(user -> {
                                    // 更新缓存
                                    return reactiveRedisTemplate.opsForValue().set(key, user, Duration.ofHours(1))
                                            .thenReturn(user);
                                })
                );
    }

    @Override
    public Mono<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new RuntimeException("用户不存在")));
    }

    @Override
    @Transactional
    public Mono<Void> deleteUser(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("用户不存在")))
                .flatMap(user -> {
                    // 逻辑删除
                    user.setIsDeleted(1);
                    user.setUpdateTime(LocalDateTime.now());
                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // 删除用户角色关联
                                return userRoleRepository.deleteByUserId(savedUser.getId())
                                        .then();
                            })
                            .doOnSuccess(v -> {
                                // 清除缓存
                                clearUserCache(user.getId());
                                reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).subscribe();
                            });
                });
    }

    @Override
    public Mono<Long> countUsers() {
        // 先尝试从缓存中获取
        return reactiveRedisTemplate.opsForValue().get(USER_COUNT_CACHE_KEY)
                .cast(Long.class)
                .switchIfEmpty(
                        userRepository.countAllUsers()
                                .flatMap(count -> {
                                    // 更新缓存
                                    return reactiveRedisTemplate.opsForValue().set(USER_COUNT_CACHE_KEY, count, Duration.ofHours(1))
                                            .thenReturn(count);
                                })
                );
    }

    @Override
    public Mono<Integer> updateUserStatus(Long id, Integer status) {
        return userRepository.updateStatus(id, status)
                .doOnSuccess(result -> {
                    // 清除缓存
                    clearUserCache(id);
                });
    }

    @Override
    @Transactional
    public Mono<Void> updateUserRoles(Long userId, List<Long> roleIds) {
        // 先删除原有的用户角色关联
        return userRoleRepository.deleteByUserId(userId)
                .then(Flux.fromIterable(roleIds)
                        .flatMap(roleId -> {
                            UserRole userRole = new UserRole();
                            userRole.setUserId(userId);
                            userRole.setRoleId(roleId);
                            userRole.setCreateTime(LocalDateTime.now());
                            userRole.setUpdateTime(LocalDateTime.now());
                            return userRoleRepository.save(userRole);
                        })
                        .then()
                        .doOnSuccess(v -> {
                            // 清除缓存
                            clearUserCache(userId);
                        }));
    }

    @Override
    public Mono<Boolean> checkUsernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public Mono<Boolean> checkEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Mono<Boolean> checkPhoneExists(String phone) {
        return userRepository.existsByPhone(phone);
    }

    @Override
    public Mono<Integer> updateLastLogin(Long id, String ip) {
        return userRepository.updateLastLogin(id, ip)
                .doOnSuccess(result -> {
                    // 清除缓存
                    clearUserCache(id);
                });
    }
    
    @Override
    @Transactional
    public Mono<Void> batchDeleteUsers(List<Long> ids) {
        return Flux.fromIterable(ids)
                .flatMap(id -> userRoleRepository.deleteByUserId(id)
                        .then(userRepository.deleteById(id))
                        .doOnSuccess(v -> clearUserCache(id)))
                .then();
    }
    
    @Override
    public Mono<Map<String, Object>> getUserPage(int page, int size, String username, String email, Integer status) {
        // 构建查询条件
        Map<String, Object> params = new HashMap<>();
        if (username != null && !username.isEmpty()) {
            params.put("username", username);
        }
        if (email != null && !email.isEmpty()) {
            params.put("email", email);
        }
        if (status != null) {
            params.put("status", status);
        }
        
        // 分页查询用户
        Flux<User> userFlux = userRepository.findByCondition(params, PageRequest.of(page, size));
        
        // 查询符合条件的总记录数
        Mono<Long> countMono = userRepository.countByCondition(params);
        
        return Mono.zip(userFlux.collectList(), countMono)
                .map(tuple -> {
                    List<User> users = tuple.getT1();
                    Long total = tuple.getT2();
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("records", users);
                    result.put("total", total);
                    result.put("size", size);
                    result.put("current", page + 1);
                    result.put("pages", (total + size - 1) / size);
                    
                    return result;
                });
    }
    
    @Override
    public Mono<String> resetPassword(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("用户不存在")))
                .flatMap(user -> {
                    // 生成随机密码
                    String newPassword = generateRandomPassword();
                    // 加密密码 - 使用与其他方法相同的加密方式
                    String encryptedPassword = SecureUtil.md5(newPassword);
                    user.setPassword(encryptedPassword);
                    return userRepository.save(user)
                            .map(savedUser -> newPassword)
                            .doOnSuccess(password -> clearUserCache(id));
                });
    }
    
    /**
     * 生成随机密码
     * @return 随机密码
     */
    private String generateRandomPassword() {
        // 生成8位随机密码
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 清除用户相关缓存
     * @param userId 用户ID
     */
    private void clearUserCache(Long userId) {
        String userKey = USER_CACHE_KEY + userId;
        reactiveRedisTemplate.delete(userKey).subscribe();
        
        // 清除用户列表缓存
        String userListPattern = USER_LIST_CACHE_KEY + "*";
        reactiveRedisTemplate.keys(userListPattern).flatMap(reactiveRedisTemplate::delete).subscribe();
    }

    @Override
    public Flux<User> listUsers(int page, int size) {
        return userRepository.findAllUsers(PageRequest.of(page, size));
    }

    @Override
    @Transactional
    public Mono<User> register(User user) {
        return createUser(user);
    }

    @Override
    public Mono<String> login(String username, String password) {
        return getUserByUsername(username)
                .flatMap(user -> {
                    // 检查用户状态
                    if (user.getStatus() != null && user.getStatus() == 0) {
                        return Mono.error(new RuntimeException("账号已被禁用"));
                    }
                    
                    // 验证密码
                    String encryptedPassword = SecureUtil.md5(password);
                    if (!encryptedPassword.equals(user.getPassword())) {
                        return Mono.error(new RuntimeException("用户名或密码错误"));
                    }
                    
                    // 更新最后登录时间和IP（这里IP为空，实际应用中可从请求中获取）
                    return updateLastLogin(user.getId(), null)
                            .thenReturn("登录成功");
                });
    }
    
    @Override
    public Mono<UserInfoVO> getCurrentUserInfo(Long userId) {
        return getUserById(userId)
                .flatMap(user -> roleRepository.findByUserId(userId)
                        .collectList()
                        .map(roles -> userMapper.toUserInfoVO(user, roles)));
    }
    
    @Override
    public Mono<UserInfoVO> getUserDetailById(Long id) {
        return getUserById(id)
                .flatMap(user -> roleRepository.findByUserId(id)
                        .collectList()
                        .map(roles -> userMapper.toUserInfoVO(user, roles)));
    }
    
    @Override
    @Transactional
    public Mono<User> createUserWithRoles(UserDTO userDTO) {
        User user = userMapper.toUser(userDTO);
        
        return createUser(user)
                .flatMap(savedUser -> {
                    if (userDTO.getRoleIds() != null && !userDTO.getRoleIds().isEmpty()) {
                        return updateUserRoles(savedUser.getId(), userDTO.getRoleIds())
                                .thenReturn(savedUser);
                    }
                    return Mono.just(savedUser);
                });
    }
    
    @Override
    @Transactional
    public Mono<User> updateUserWithRoles(UserDTO userDTO) {
        return getUserById(userDTO.getId())
                .flatMap(existingUser -> {
                    User updatedUser = userMapper.updateUserFromDTO(userDTO, existingUser);
                    return userRepository.save(updatedUser)
                            .flatMap(savedUser -> {
                                if (userDTO.getRoleIds() != null) {
                                    return updateUserRoles(savedUser.getId(), userDTO.getRoleIds())
                                            .thenReturn(savedUser);
                                }
                                return Mono.just(savedUser);
                            });
                });
    }
    
    @Override
    public Mono<Boolean> updatePassword(Long userId, UserPasswordDTO passwordDTO) {
        return getUserById(userId)
                .flatMap(user -> {
                    // 验证旧密码
                    String oldEncryptedPassword = SecureUtil.md5(passwordDTO.getOldPassword());
                    if (!oldEncryptedPassword.equals(user.getPassword())) {
                        return Mono.error(new RuntimeException("旧密码不正确"));
                    }
                    
                    // 设置新密码
                    user.setPassword(SecureUtil.md5(passwordDTO.getNewPassword()));
                    user.setUpdateTime(LocalDateTime.now());
                    
                    return userRepository.save(user)
                            .map(savedUser -> true)
                            .doOnSuccess(result -> clearUserCache(userId));
                });
    }
} 