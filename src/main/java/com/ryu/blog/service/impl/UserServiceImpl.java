package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.dto.UserPasswordDTO;
import com.ryu.blog.entity.User;
import com.ryu.blog.entity.UserRole;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.UserMapper;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.UserService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.UserInfoVO;
import com.ryu.blog.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * @author ryu
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    private UserService self;

    private static final String USER_CACHE_KEY = "user:";
    private static final String USER_LIST_CACHE_KEY = "user:list:";
    private static final String USER_COUNT_CACHE_KEY = "user:count";

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            ReactiveRedisTemplate<String, Object> reactiveRedisTemplate,
            UserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }
    
    // Setter 注入 self，使用 @Lazy 避免循环依赖
    @Autowired
    @Lazy
    public void setSelf(UserService self) {
        this.self = self;
    }

    @Override
    @Transactional
    public Mono<User> createUser(User user) {
        // 检查用户名是否存在
        return checkUsernameExists(user.getUsername())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(BusinessException.usernameExists());
                    }
                    
                    // 检查邮箱是否存在（如果有）
                    if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                        return checkEmailExists(user.getEmail())
                                .flatMap(emailExists -> {
                                    if (Boolean.TRUE.equals(emailExists)) {
                                        return Mono.error(BusinessException.emailExists());
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
        user.setIsDeleted(false);
        
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        
        // 使用HuTool的SecureUtil加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        return userRepository.save(user)
                .flatMap(savedUser -> clearUserCache(savedUser.getId())
                        .then(reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).then())
                        .thenReturn(savedUser));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #user.id", condition = "#user.id != null")
    public Mono<User> updateUser(User user) {
        return userRepository.findById(user.getId())
                .switchIfEmpty(Mono.error(BusinessException.userNotFound()))
                .flatMap(existingUser -> {
                    // 如果用户名有变化，需要检查是否已存在
                    if (user.getUsername() != null && !user.getUsername().equals(existingUser.getUsername())) {
                        return checkUsernameExists(user.getUsername())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(BusinessException.usernameExists());
                                    }
                                    return updateUserFields(existingUser, user);
                                });
                    }
                    
                    // 如果邮箱有变化，需要检查是否已存在
                    if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
                        return checkEmailExists(user.getEmail())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(BusinessException.emailExists());
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
        
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        existingUser.setUpdateTime(LocalDateTime.now());
        
        return userRepository.save(existingUser)
                .doOnSuccess(savedUser -> {
                    // 清除缓存
                    clearUserCache(savedUser.getId());
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<User> getUserById(Long id) {
        // 先尝试从缓存中获取
        String key = USER_CACHE_KEY + id;
        return reactiveRedisTemplate.opsForValue().get(key)
                .cast(User.class)
                .switchIfEmpty(
                        userRepository.findById(id)
                                .switchIfEmpty(Mono.error(BusinessException.userNotFound()))
                                .flatMap(user -> {
                                    // 更新缓存
                                    return reactiveRedisTemplate.opsForValue().set(key, user, Duration.ofHours(1))
                                            .thenReturn(user);
                                })
                );
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_USERNAME_KEY + "' + #username", unless = "#result == null")
    public Mono<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(BusinessException.userNotFound()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #id")
    public Mono<Void> deleteUser(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.userNotFound()))
                .flatMap(user -> {
                    // 逻辑删除
                    user.setIsDeleted(true);
                    user.setUpdateTime(LocalDateTime.now());
                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                return userRoleRepository.deleteByUserId(savedUser.getId())
                                        .then(clearUserCache(user.getId()))
                                        .then(reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).then());
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
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #id")
    public Mono<Integer> updateUserStatus(Long id, Integer status) {
        return userRepository.updateStatus(id, status)
                .doOnSuccess(result -> {
                    // 清除缓存
                    clearUserCache(id);
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #userId")
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
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #id")
    public Mono<Integer> updateLastLogin(Long id, String ip) {
        return userRepository.updateLastLogin(id, ip)
                .doOnSuccess(result -> {
                    // 清除缓存
                    clearUserCache(id);
                });
    }
    
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<Void> batchDeleteUsers(List<Long> ids) {
        return Flux.fromIterable(ids)
                .flatMap(id -> userRoleRepository.deleteByUserId(id)
                        .then(userRepository.deleteById(id))
                        .doOnSuccess(v -> clearUserCache(id)))
                .then();
    }
    
    @Override
    public Mono<PageResult<UserVO>> getUserPage(int page, int size, String username, String email, Integer status) {
        PageRequest pageRequest = PageRequest.of(page, size);

        QueryType queryType = determineQueryType(username, email, status);
        QueryResult queryResult = selectQuery(queryType, username, email, status, pageRequest);

        return Mono.zip(queryResult.users().collectList(), queryResult.count())
                .map(tuple -> {
                    List<User> users = tuple.getT1();
                    Long total = tuple.getT2();

                    List<UserVO> userVOs = users.stream()
                            .map(userMapper::toUserVO)
                            .collect(Collectors.toList());

                    PageResult<UserVO> pageResult = new PageResult<>();
                    pageResult.setRecords(userVOs);
                    pageResult.setTotal(total);
                    pageResult.setSize(size);
                    pageResult.setCurrent(page + 1);
                    pageResult.setPages((total + size - 1) / size);

                    return pageResult;
                });
    }

    private enum QueryType {
        USERNAME_EMAIL_STATUS,
        USERNAME_EMAIL,
        USERNAME_STATUS,
        EMAIL_STATUS,
        USERNAME_ONLY,
        EMAIL_ONLY,
        STATUS_ONLY,
        ALL
    }

    private record QueryResult(Flux<User> users, Mono<Long> count) {}

    private QueryType determineQueryType(String username, String email, Integer status) {
        boolean hasUsername = username != null && !username.isEmpty();
        boolean hasEmail = email != null && !email.isEmpty();
        boolean hasStatus = status != null;

        if (hasUsername && hasEmail && hasStatus) return QueryType.USERNAME_EMAIL_STATUS;
        if (hasUsername && hasEmail) return QueryType.USERNAME_EMAIL;
        if (hasUsername && hasStatus) return QueryType.USERNAME_STATUS;
        if (hasEmail && hasStatus) return QueryType.EMAIL_STATUS;
        if (hasUsername) return QueryType.USERNAME_ONLY;
        if (hasEmail) return QueryType.EMAIL_ONLY;
        if (hasStatus) return QueryType.STATUS_ONLY;
        return QueryType.ALL;
    }

    private QueryResult selectQuery(QueryType type, String username, String email, Integer status, PageRequest pageRequest) {
        return switch (type) {
            case USERNAME_EMAIL_STATUS -> new QueryResult(
                    userRepository.findByUsernameLikeAndEmailLikeAndStatus(username, email, status, pageRequest),
                    userRepository.countByUsernameLikeAndEmailLikeAndStatus(username, email, status));
            case USERNAME_EMAIL -> new QueryResult(
                    userRepository.findByUsernameLikeAndEmailLike(username, email, pageRequest),
                    userRepository.countByUsernameLikeAndEmailLike(username, email));
            case USERNAME_STATUS -> new QueryResult(
                    userRepository.findByUsernameLikeAndStatus(username, status, pageRequest),
                    userRepository.countByUsernameLikeAndStatus(username, status));
            case EMAIL_STATUS -> new QueryResult(
                    userRepository.findByEmailLikeAndStatus(email, status, pageRequest),
                    userRepository.countByEmailLikeAndStatus(email, status));
            case USERNAME_ONLY -> new QueryResult(
                    userRepository.findByUsernameLike(username, pageRequest),
                    userRepository.countByUsernameLike(username));
            case EMAIL_ONLY -> new QueryResult(
                    userRepository.findByEmailLike(email, pageRequest),
                    userRepository.countByEmailLike(email));
            case STATUS_ONLY -> new QueryResult(
                    userRepository.findByStatus(status, pageRequest),
                    userRepository.countByStatus(status));
            case ALL -> new QueryResult(
                    userRepository.findAllUsers(pageRequest),
                    userRepository.countAllUsers());
        };
    }
    
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #id")
    public Mono<String> resetPassword(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.userNotFound()))
                .flatMap(user -> {
                    // 生成随机密码
                    String newPassword = generateRandomPassword();
                    // 加密密码 - 使用与其他方法相同的加密方式
                    user.setPassword(passwordEncoder.encode(newPassword));
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
    private Mono<Void> clearUserCache(Long userId) {
        String userKey = USER_CACHE_KEY + userId;
        String userListPattern = USER_LIST_CACHE_KEY + "*";

        return reactiveRedisTemplate.delete(userKey)
                .then(reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(userListPattern).count(100).build())
                        .flatMap(key -> reactiveRedisTemplate.delete(key).then())
                        .then())
                .doOnError(e -> log.error("清除用户缓存失败: userId={}, error={}", userId, e.getMessage()));
    }

    @Override
    public Flux<User> listUsers(int page, int size) {
        return userRepository.findAllUsers(PageRequest.of(page, size));
    }

    @Override
    @Transactional
    public Mono<User> register(User user) {
        // 使用 self 调用，确保事务和缓存生效
        return self.createUser(user);
    }

    @Override
    public Mono<String> login(String username, String password) {
        // 使用 self 调用，确保缓存生效
        return self.validateUserCredentials(username, password)
                .flatMap(user -> self.updateLastLogin(user.getId(), null)
                        .thenReturn("登录成功"));
    }
    
    @Override
    public Mono<User> validateUserCredentials(String username, String password) {
        // 使用 self 调用，确保 @Cacheable 生效
        return self.getUserByUsername(username)
                .flatMap(user -> {
                    // 检查用户状态
                    if (user.getStatus() != null && user.getStatus() == 0) {
                        return Mono.error(BusinessException.accountDisabled());
                    }
                    
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(BusinessException.loginFailed());
                    }
                    
                    // 验证通过，返回用户对象
                    return Mono.just(user);
                });
    }
    
    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_INFO_KEY + "' + #userId", unless = "#result == null")
    public Mono<UserInfoVO> getCurrentUserInfo(Long userId) {
        // 使用 self 调用，确保缓存生效
        return self.getUserById(userId)
                .flatMap(user -> roleRepository.findByUserId(userId)
                        .collectList()
                        .map(roles -> userMapper.toUserInfoVO(user, roles)));
    }
    
    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_DETAIL_KEY + "' + #id", unless = "#result == null")
    public Mono<UserInfoVO> getUserDetailById(Long id) {
        // 使用 self 调用，确保缓存生效
        return self.getUserById(id)
                .flatMap(user -> roleRepository.findByUserId(id)
                        .collectList()
                        .map(roles -> userMapper.toUserInfoVO(user, roles)));
    }
    
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #userDTO.id", condition = "#userDTO.id != null")
    public Mono<User> createUserWithRoles(UserDTO userDTO) {
        User user = userMapper.toUser(userDTO);
        
        // 使用 self 调用，确保事务和缓存生效
        return self.createUser(user)
                .flatMap(savedUser -> {
                    if (userDTO.getRoleIds() != null && !userDTO.getRoleIds().isEmpty()) {
                        return self.updateUserRoles(savedUser.getId(), userDTO.getRoleIds())
                                .thenReturn(savedUser);
                    }
                    return Mono.just(savedUser);
                });
    }
    
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #userDTO.id", condition = "#userDTO.id != null")
    public Mono<User> updateUserWithRoles(UserDTO userDTO) {
        // 使用 self 调用，确保缓存生效
        return self.getUserById(userDTO.getId())
                .flatMap(existingUser -> {
                    User updatedUser = userMapper.updateUserFromDTO(userDTO, existingUser);
                    return userRepository.save(updatedUser)
                            .flatMap(savedUser -> {
                                if (userDTO.getRoleIds() != null) {
                                    return self.updateUserRoles(savedUser.getId(), userDTO.getRoleIds())
                                            .thenReturn(savedUser);
                                }
                                return Mono.just(savedUser);
                            });
                });
    }
    
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #userId")
    public Mono<Boolean> updatePassword(Long userId, UserPasswordDTO passwordDTO) {
        // 使用 self 调用，确保缓存生效
        return self.getUserById(userId)
                .flatMap(user -> {
                    if (!passwordEncoder.matches(passwordDTO.getOldPassword(), user.getPassword())) {
                        return Mono.error(BusinessException.oldPasswordError());
                    }

                    user.setPassword(passwordEncoder.encode(passwordDTO.getNewPassword()));
                    user.setUpdateTime(LocalDateTime.now());
                    
                    return userRepository.save(user)
                            .map(savedUser -> true)
                            .doOnSuccess(result -> clearUserCache(userId));
                });
    }

} 