package com.ryu.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.constant.SystemConstants;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.RegisterDTO;
import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.dto.UserPasswordDTO;
import com.ryu.blog.entity.Role;
import com.ryu.blog.entity.User;
import com.ryu.blog.entity.UserRole;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.mapper.UserMapper;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.UserService;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.utils.SaTokenUtils;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.UserInfoVO;
import com.ryu.blog.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final UserPermissionCache permissionCache;
    private final UserPermissionResolver userPermissionResolver;
    private final CacheManager cacheManager;

    private UserService self;

    private static final String USER_COUNT_CACHE_KEY = "user:count";

    /** 随机口令的字符集 */
    private static final String PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    /** 随机口令长度 */
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            ReactiveRedisTemplate<String, Object> reactiveRedisTemplate,
            UserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder,
            UserPermissionCache permissionCache,
            UserPermissionResolver userPermissionResolver,
            CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionCache = permissionCache;
        this.userPermissionResolver = userPermissionResolver;
        this.cacheManager = cacheManager;
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
        // 【安全】客户端可能带了 id —— 必须清掉。
        // Spring Data R2DBC 的 save() 在 id 非空时走 UPDATE 分支（isNew == false），
        // 不清 id 的话 `POST /auth/register {"id":1,...}` 会直接改写 id=1 那一行，
        // 连 admin 的密码一起换掉 —— 未认证即可接管任意账号。
        user.setId(null);

        // 设置默认值
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setIsDeleted(false);

        // 新建用户一律「正常」：状态有专用的改状态接口（system:user:status），
        // 不接受调用方在建号时就把状态定下来。
        user.setStatus(1);

        // 使用 BCrypt 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user)
                .flatMap(savedUser -> clearUserCache(savedUser.getId())
                        .then(reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).then())
                        .then(assignDefaultRole(savedUser.getId()))
                        .thenReturn(savedUser));
    }

    /**
     * 给新用户分配默认角色
     *
     * <p>原先注册流程完全不写 {@code t_user_roles}，而 {@code getDefaultRole()}
     * 全仓库只有 {@code GET /roles/default} 一个读取点 —— 结果是
     * <b>注册出来的用户永远没有任何角色</b>。
     *
     * <p>显式指定角色的场景（{@code createUserWithRoles}）会在之后覆盖这个默认值。
     */
    private Mono<Void> assignDefaultRole(Long userId) {
        return roleRepository.findDefaultRoles()
                .next()
                .flatMap(role -> {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(role.getId());
                    userRole.setCreateTime(LocalDateTime.now());
                    userRole.setUpdateTime(LocalDateTime.now());
                    return userRoleRepository.save(userRole).then();
                })
                .doOnSuccess(v -> log.debug("已为新用户分配默认角色: userId={}", userId))
                .onErrorResume(e -> {
                    log.warn("分配默认角色失败: userId={}, error={}", userId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<User> getUserById(Long id) {
        // 只保留 @Cacheable 一层缓存。
        // 原先这里同时维护了手写 Redis（键 "user:{id}"，TTL 1 小时）与 @Cacheable
        // （键 "user:id:{id}"，TTL 30 分钟）两套键空间，而 clearUserCache 只删前者 ——
        // 两层的 TTL 与失效规则对不上，改密后旧密码能在 TTL 内继续登录。
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.userNotFound()));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE, key = "'" + CacheConstants.USER_USERNAME_KEY + "' + #username", unless = "#result == null")
    public Mono<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(BusinessException.userNotFound()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<Void> deleteUser(Long id) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            requireNotSelf(ctx, id, "不能删除自己");
            requireNotSuperAdmin(id, "不能删除超级管理员");
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
                                            .then(reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).then())
                                            // 吊销该用户已签发的 token，否则删除后仍能继续访问
                                            .then(kickout(savedUser.getId()))
                                            // 权限快照失效
                                            .then(permissionCache.evict(savedUser.getId()));
                                });
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
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<Integer> updateUserStatus(Long id, Integer status) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            requireNotSelf(ctx, id, "不能修改自己的状态");
            requireNotSuperAdmin(id, "不能修改超级管理员的状态");
            return userRepository.updateStatus(id, status)
                    .flatMap(result -> clearUserCache(id)
                            .then(kickout(id))
                            .then(permissionCache.evict(id))
                            .thenReturn(result));
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<Void> updateUserRoles(Long userId, List<Long> roleIds) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            // 【安全】不能给自己改角色 —— 这是最直接的提权路径：
            // 「给自己一个 admin 角色」比任何别的漏洞都好利用。
            requireNotSelf(ctx, userId, "不能修改自己的角色");
            requireNotSuperAdmin(userId, "不能修改超级管理员的角色");

            // 先删除原有的用户角色关联
            return userRoleRepository.deleteByUserId(userId)
                    .then(Flux.fromIterable(roleIds)
                            .concatMap(roleId -> {
                                // concatMap：@Transactional 内共用同一条 R2DBC 连接，
                                // flatMap 的并发执行会让语句在同一连接上交错
                                UserRole userRole = new UserRole();
                                userRole.setUserId(userId);
                                userRole.setRoleId(roleId);
                                userRole.setCreateTime(LocalDateTime.now());
                                userRole.setUpdateTime(LocalDateTime.now());
                                return userRoleRepository.save(userRole);
                            })
                            .then())
                    // 清除缓存（必须串进响应式链，doOnSuccess 里构造的 Mono 不会被订阅）
                    .then(clearUserCache(userId))
                    // 角色变了，权限快照必须立刻重建，否则旧的权限还能继续用
                    .then(permissionCache.refresh(userId));
        });
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
                .flatMap(result -> clearUserCache(id).thenReturn(result));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<Void> batchDeleteUsers(List<Long> ids) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            if (ids == null || ids.isEmpty()) {
                return Mono.empty();
            }
            // 自我保护与超管保护
            for (Long id : ids) {
                requireNotSelf(ctx, id, "不能删除自己");
                requireNotSuperAdmin(id, "不能删除超级管理员");
            }
            // 统一为逻辑删除 —— 原先这里是物理 DELETE，与 deleteUser 的软删语义分裂，
            // 而且不清 user:count 缓存，导致用户总数最多错 1 小时
            return Flux.fromIterable(ids)
                    .concatMap(id -> userRepository.findById(id)
                            .flatMap(user -> {
                                user.setIsDeleted(true);
                                user.setUpdateTime(LocalDateTime.now());
                                return userRepository.save(user);
                            })
                            .flatMap(saved -> userRoleRepository.deleteByUserId(saved.getId())
                                    .then(clearUserCache(saved.getId()))
                                    .then(kickout(saved.getId()))
                                    .then(permissionCache.evict(saved.getId()))
                                    .then(reactiveRedisTemplate.delete(USER_COUNT_CACHE_KEY).then())))
                    .then();
        });
    }

    @Override
    public Mono<PageResult<UserVO>> getUserPage(int page, int size, String username, String email, Integer status) {
        PageRequest pageRequest = PageRequest.of(page, size);

        QueryType queryType = determineQueryType(username, email, status);
        QueryResult queryResult = selectQuery(queryType, username, email, status, pageRequest);

        return Mono.zip(queryResult.users().collectList(), queryResult.count())
                .flatMap(tuple -> {
                    List<User> users = tuple.getT1();
                    Long total = tuple.getT2();

                    // 列表页原先用的是单参 toUserVO，roles / roleIds 恒为 null。
                    // 这里用两条批量查询（用户→角色关联、角色详情）补齐，避免逐行 N+1。
                    return loadRolesByUserIds(users)
                            .map(rolesByUser -> {
                                List<UserVO> userVOs = users.stream()
                                        .map(user -> userMapper.toUserVO(user,
                                                rolesByUser.getOrDefault(user.getId(), List.of())))
                                        .collect(Collectors.toList());

                                PageResult<UserVO> pageResult = new PageResult<>();
                                pageResult.setRecords(userVOs);
                                pageResult.setTotal(total);
                                pageResult.setSize(size);
                                pageResult.setCurrent(page + 1);
                                pageResult.setPages((total + size - 1) / size);
                                return pageResult;
                            });
                });
    }

    /**
     * 批量加载「用户 → 启用中的角色」，用于用户列表补齐 roleIds / roles
     */
    private Mono<Map<Long, List<Role>>> loadRolesByUserIds(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).filter(java.util.Objects::nonNull).toList();
        if (userIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        return userRoleRepository.findByUserIdIn(userIds)
                .collectList()
                .flatMap(links -> {
                    Set<Long> roleIds = links.stream()
                            .map(UserRole::getRoleId)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());

                    if (roleIds.isEmpty()) {
                        return Mono.just(Map.<Long, List<Role>>of());
                    }

                    return roleRepository.findActiveByIdIn(roleIds)
                            .collectMap(Role::getId)
                            .map(roleMap -> {
                                Map<Long, List<Role>> rolesByUser = new HashMap<>();
                                for (UserRole link : links) {
                                    Role role = roleMap.get(link.getRoleId());
                                    if (role != null) {
                                        rolesByUser.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(role);
                                    }
                                }
                                return rolesByUser;
                            });
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
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<String> resetPassword(Long id) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            requireNotSelf(ctx, id, "不能重置自己的密码，请使用「修改密码」接口");
            requireNotSuperAdmin(id, "不能重置超级管理员的密码");
            return userRepository.findById(id)
                    .switchIfEmpty(Mono.error(BusinessException.userNotFound()))
                    .flatMap(user -> {
                        // 生成随机密码
                        String newPassword = generateRandomPassword();
                        // 加密密码 - 使用与其他方法相同的加密方式
                        user.setPassword(passwordEncoder.encode(newPassword));
                        return userRepository.save(user)
                                .map(savedUser -> newPassword)
                                .flatMap(password -> clearUserCache(id)
                                        // 重置密码后旧 token 也要失效
                                        .then(kickout(id))
                                        .thenReturn(password));
                    });
        });
    }

    /**
     * 生成随机密码
     *
     * <p>用 {@link SecureRandom} 而不是 {@code java.util.Random}：
     * 后者是 48 位 LCG，观测几个样本就能推出种子、进而预测后续生成的密码。
     * 长度也从 8 位提到 12 位。
     *
     * @return 随机密码
     */
    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // =====================================================================
    // 自我保护 / 超管保护
    // =====================================================================

    /**
     * 断言操作者不是在操作自己
     *
     * <p>用于「改自己的角色」「删自己」这类操作 —— 它们的正确入口另有其人
     * （改密码走 {@code PUT /users/password}）。
     *
     * @param target 目标用户ID
     * @param reason 拒绝原因
     */
    private void requireNotSelf(OperatorContext ctx, Long target, String reason) {
        if (OwnershipPolicy.isSelf(ctx, target)) {
            throw PermissionDeniedException.accessDenied(reason);
        }
    }

    /**
     * 断言目标不是超级管理员
     *
     * @param target 目标用户ID
     * @param reason 拒绝原因
     */
    private void requireNotSuperAdmin(Long target, String reason) {
        if (SystemConstants.SUPER_ADMIN_ID.equals(target)) {
            throw PermissionDeniedException.accessDenied(reason);
        }
    }

    /**
     * 吊销某用户已签发的所有 token
     *
     * <p>原先改密 / 禁用 / 删除用户后都不吊销 token，而 token 有效期是 30 天 ——
     * 账号被禁用了还能继续用旧 token 访问。
     *
     * @param userId 用户ID
     */
    private Mono<Void> kickout(Long userId) {
        if (userId == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> StpUtil.kickout(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("吊销用户 token 失败: userId={}, error={}", userId, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 清除用户相关缓存
     *
     * <p>直接操作 {@link CacheManager}，按键精确驱逐。
     * 原先这里是手写 Redis 删除（键 {@code "user:" + userId}），
     * 而真正生效的缓存键是 {@code user:id:{id}} / {@code user:info:{id}} /
     * {@code user:detail:{id}} / {@code user:username:{name}} —— 键空间根本对不上，
     * 删了个寂寞。
     *
     * <p>注意：<b>username 键不在这里清</b>（这里拿不到用户名），
     * 改密路径用 {@code @CacheEvict(allEntries = true)} 一次性清空。
     *
     * @param userId 用户ID
     */
    private Mono<Void> clearUserCache(Long userId) {
        if (userId == null) {
            return Mono.empty();
        }
        return Mono.<Void>fromCallable(() -> {
                    Cache cache = cacheManager.getCache(CacheConstants.USER_CACHE);
                    if (cache != null) {
                        cache.evict(CacheConstants.USER_ID_KEY + userId);
                        cache.evict(CacheConstants.USER_INFO_KEY + userId);
                        cache.evict(CacheConstants.USER_DETAIL_KEY + userId);
                    }
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("清除用户缓存失败: userId={}, error={}", userId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Flux<User> listUsers(int page, int size) {
        return userRepository.findAllUsers(PageRequest.of(page, size));
    }

    @Override
    @Transactional
    public Mono<UserInfoVO> register(RegisterDTO registerDTO) {
        // 手工建实体，而不是直接反序列化 User：
        // 实体的 id 若被客户端赋值，R2DBC 的 save() 会走 UPDATE 分支覆盖任意用户行。
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(registerDTO.getPassword());
        user.setEmail(registerDTO.getEmail());
        user.setNickname(registerDTO.getNickname());
        user.setPhone(registerDTO.getPhone());
        user.setAvatar(registerDTO.getAvatar());
        user.setBio(registerDTO.getBio());

        // 使用 self 调用，确保事务和缓存生效
        return self.createUser(user)
                .flatMap(saved -> roleRepository.findActiveRolesByUserId(saved.getId())
                        .collectList()
                        .map(roles -> {
                            // 返回 VO 而不是实体 —— 实体里带着 BCrypt 密码哈希
                            UserInfoVO vo = userMapper.toUserInfoVO(saved, roles);
                            vo.setPermissions(List.of());
                            return vo;
                        }));
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
                    // 已逻辑删除的账号一律不能登录
                    if (Boolean.TRUE.equals(user.getIsDeleted())) {
                        return Mono.error(BusinessException.loginFailed());
                    }

                    // 检查用户状态：0-禁用，2-锁定
                    // 原先只判 0，status=2 的锁定账号可以直接登录
                    if (user.getStatus() != null && user.getStatus() == 0) {
                        return Mono.error(BusinessException.accountDisabled());
                    }
                    if (user.getStatus() != null && user.getStatus() == 2) {
                        return Mono.error(BusinessException.accountLocked());
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
                .flatMap(user -> roleRepository.findActiveRolesByUserId(userId)
                        .collectList()
                        .flatMap(roles -> userPermissionResolver.resolvePermissionIdentities(userId)
                                .map(permissions -> {
                                    UserInfoVO vo = userMapper.toUserInfoVO(user, roles);
                                    // MapStruct 的 toUserInfoVO 把 permissions 标成了 ignore，
                                    // 这里补上 —— 前端要靠它渲染按钮级权限
                                    vo.setPermissions(new ArrayList<>(permissions));
                                    return vo;
                                })));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.USER_CACHE,
            // key 必须带上当前用户身份：归属校验在方法体里，而缓存命中时方法体不会执行 ——
            // key 里只有用户 ID 的话，A 查过的详情会被缓存下来发给 B。
            // （与文章详情、文件详情的缓存 key 同一套理由，见 CacheAnnotationGuardTest）
            key = "'" + CacheConstants.USER_DETAIL_KEY + "' + #id"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull()"
                    + " + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result == null")
    public Mono<UserInfoVO> getUserDetailById(Long id) {
        // 身份在<b>组装期</b>取一次：缓存 key 也是在组装期算的（SpEL 只能同步取值），
        // 两者必须同一时刻、同一来源，否则就是
        // 「A 的可见性判定配 B 的缓存 key」——判定按 A 放行，key 却按 B 落库，
        // 私有内容被写进别人的 key 下面，之后谁拿到那个 key 谁就能读到。
        //
        // 这里用 SaTokenUtils（ThreadLocal）而不是 Reactor Context 的 OperatorContext，
        // 正是因为 key 走的就是 SaTokenUtils；切面已保证这个 ThreadLocal 与
        // Reactor Context 的身份一致（见 IdentitySourceConsistencyTest）。
        OperatorContext ctx = SaTokenUtils.currentOperatorContext();
        return Mono.defer(() -> {
            // 归属校验：只能查自己，或拥有「管理他人」能力
            OwnershipPolicy.requireCanActOn(ctx, id);
            // 使用 self 调用，确保缓存生效
            return self.getUserById(id)
                    .flatMap(user -> roleRepository.findActiveRolesByUserId(id)
                            .collectList()
                            .flatMap(roles -> userPermissionResolver.resolvePermissionIdentities(id)
                                    .map(permissions -> {
                                        UserInfoVO vo = userMapper.toUserInfoVO(user, roles);
                                        vo.setPermissions(new ArrayList<>(permissions));
                                        return vo;
                                    })));
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<User> createUserWithRoles(UserDTO userDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            User user = userMapper.toUser(userDTO);

            // 新建用户时指定角色 = 分配角色，要 system:user:assign。
            // 不校验的话，「能建用户」就等于「能建一个 admin 用户」——
            // 那编辑接口上的角色闸门也就形同虚设。
            boolean assigningRoles = userDTO.getRoleIds() != null && !userDTO.getRoleIds().isEmpty();
            Mono<Void> assignGuard = assigningRoles
                    ? requireAssignPermission(ctx)
                    : Mono.empty();

            // 使用 self 调用，确保事务和缓存生效
            return assignGuard
                    .then(self.createUser(user))
                    .flatMap(savedUser -> {
                        if (assigningRoles) {
                            // 新建的用户还没有角色，self 代理这一层不能带 requireNotSelf 之外的限制，
                            // 所以这里直接写关联表而不是复用 updateUserRoles（后者会拒绝给自己改角色）
                            return assignRoles(savedUser.getId(), userDTO.getRoleIds())
                                    .thenReturn(savedUser);
                        }
                        return Mono.just(savedUser);
                    });
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<User> updateUserWithRoles(UserDTO userDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            // 归属校验：只能改自己，或拥有「管理他人」能力
            OwnershipPolicy.requireCanActOn(ctx, userDTO.getId());

            // 使用 self 调用，确保缓存生效
            return self.getUserById(userDTO.getId())
                    .flatMap(existingUser -> {
                        User updatedUser = userMapper.updateUserFromDTO(userDTO, existingUser);
                        return userRepository.save(updatedUser)
                                .flatMap(savedUser -> applyRoleChange(ctx, savedUser.getId(), userDTO.getRoleIds())
                                        .thenReturn(savedUser));
                    });
        });
    }

    /**
     * 角色变更闸门 —— 「改资料」和「改角色」不是一回事。
     *
     * <p>判定规则见 {@link RoleChangePolicy}（纯函数，可直接单测）：
     * 改自己 → 忽略 roleIds；角色没变 → 不额外要权限；
     * 改别人的角色 → 还要 {@code system:user:assign}。
     */
    private Mono<Void> applyRoleChange(OperatorContext ctx, Long targetUserId, List<Long> requestedRoleIds) {
        if (requestedRoleIds == null) {
            return Mono.empty();
        }
        return roleIdsChanged(targetUserId, requestedRoleIds)
                .flatMap(changed -> {
                    RoleChangePolicy.Decision decision =
                            RoleChangePolicy.decide(ctx, targetUserId, changed);
                    return switch (decision) {
                        case IGNORED, UNCHANGED -> Mono.empty();
                        case NEEDS_ASSIGN_PERMISSION ->
                                requireAssignPermission(ctx).then(assignRoles(targetUserId, requestedRoleIds));
                    };
                });
    }

    /**
     * 请求的角色集合与当前是否不同
     */
    private Mono<Boolean> roleIdsChanged(Long userId, List<Long> requestedRoleIds) {
        return userRoleRepository.findRoleIdsByUserId(userId)
                .collect(Collectors.toSet())
                .map(current -> !current.equals(new HashSet<>(requestedRoleIds)));
    }

    /**
     * 断言操作者持有 {@code system:user:assign}
     *
     * <p>走 {@link UserPermissionResolver}（权限判定的唯一权威实现），
     * 不在这里另写一套过滤条件。
     */
    private Mono<Void> requireAssignPermission(OperatorContext ctx) {
        return userPermissionResolver.hasPermission(ctx.userId(), PermissionConstants.USER_ASSIGN)
                .flatMap(granted -> granted
                        ? Mono.empty()
                        : Mono.error(PermissionDeniedException.missingPermission(
                                PermissionConstants.USER_ASSIGN)));
    }

    /**
     * 覆盖式分配角色（先删后插），并刷新权限快照
     */
    private Mono<Void> assignRoles(Long userId, List<Long> roleIds) {
        return userRoleRepository.deleteByUserId(userId)
                .then(Flux.fromIterable(roleIds)
                        .concatMap(roleId -> {
                            UserRole userRole = new UserRole();
                            userRole.setUserId(userId);
                            userRole.setRoleId(roleId);
                            userRole.setCreateTime(LocalDateTime.now());
                            userRole.setUpdateTime(LocalDateTime.now());
                            return userRoleRepository.save(userRole);
                        })
                        .then())
                .then(clearUserCache(userId))
                .then(permissionCache.refresh(userId));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.USER_CACHE, allEntries = true)
    public Mono<Boolean> updatePassword(Long userId, UserPasswordDTO passwordDTO) {
        // allEntries=true 而不是只驱逐 user:id:{id}：
        // getUserByUsername 的缓存键是 user:username:{name}，只驱逐 id 键的话
        // 改密后旧密码在 TTL（30 分钟）内仍然能登录。
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
                            .flatMap(result -> clearUserCache(userId)
                                    // 改密后旧 token 失效
                                    .then(kickout(userId))
                                    .thenReturn(result));
                });
    }

} 