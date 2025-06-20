package com.ryu.blog.repository;

import com.ryu.blog.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 用户存储库接口
 * @author ryu
 */
@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    Mono<User> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱
     * @return 用户信息
     */
    Mono<User> findByEmail(String email);

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 用户信息
     */
    Mono<User> findByPhone(String phone);

    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM t_users WHERE username = :username AND is_deleted = 0")
    Mono<Boolean> existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     * @param email 邮箱
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM t_users WHERE email = :email AND is_deleted = 0")
    Mono<Boolean> existsByEmail(String email);

    /**
     * 检查手机号是否存在
     * @param phone 手机号
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM t_users WHERE phone = :phone AND is_deleted = 0")
    Mono<Boolean> existsByPhone(String phone);

    /**
     * 更新用户状态
     * @param id 用户ID
     * @param status 状态
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_users SET status = :status, update_time = NOW() WHERE id = :id")
    Mono<Integer> updateStatus(Long id, Integer status);

    /**
     * 更新用户最后登录信息
     * @param id 用户ID
     * @param ip IP地址
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_users SET last_login_ip = :ip, last_login_time = NOW() WHERE id = :id")
    Mono<Integer> updateLastLogin(Long id, String ip);

    /**
     * 分页查询用户列表
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findAllUsers(Pageable pageable);

    /**
     * 统计用户总数
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0")
    Mono<Long> countAllUsers();
    
    /**
     * 根据用户名模糊查询用户
     * @param username 用户名
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByUsernameLike(String username, Pageable pageable);
    
    /**
     * 根据邮箱模糊查询用户
     * @param email 邮箱
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND email LIKE CONCAT('%', :email, '%') ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByEmailLike(String email, Pageable pageable);
    
    /**
     * 根据状态查询用户
     * @param status 状态
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND status = :status ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByStatus(Integer status, Pageable pageable);
    
    /**
     * 根据用户名和邮箱模糊查询用户
     * @param username 用户名
     * @param email 邮箱
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') AND email LIKE CONCAT('%', :email, '%') ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByUsernameLikeAndEmailLike(String username, String email, Pageable pageable);
    
    /**
     * 根据用户名和状态查询用户
     * @param username 用户名
     * @param status 状态
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') AND status = :status ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByUsernameLikeAndStatus(String username, Integer status, Pageable pageable);
    
    /**
     * 根据邮箱和状态查询用户
     * @param email 邮箱
     * @param status 状态
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND email LIKE CONCAT('%', :email, '%') AND status = :status ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByEmailLikeAndStatus(String email, Integer status, Pageable pageable);
    
    /**
     * 根据用户名、邮箱和状态查询用户
     * @param username 用户名
     * @param email 邮箱
     * @param status 状态
     * @param pageable 分页参数
     * @return 用户列表
     */
    @Query("SELECT * FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') AND email LIKE CONCAT('%', :email, '%') AND status = :status ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<User> findByUsernameLikeAndEmailLikeAndStatus(String username, String email, Integer status, Pageable pageable);
    
    /**
     * 统计用户名模糊匹配的用户总数
     * @param username 用户名
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%')")
    Mono<Long> countByUsernameLike(String username);
    
    /**
     * 统计邮箱模糊匹配的用户总数
     * @param email 邮箱
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND email LIKE CONCAT('%', :email, '%')")
    Mono<Long> countByEmailLike(String email);
    
    /**
     * 统计状态匹配的用户总数
     * @param status 状态
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND status = :status")
    Mono<Long> countByStatus(Integer status);
    
    /**
     * 统计用户名和邮箱模糊匹配的用户总数
     * @param username 用户名
     * @param email 邮箱
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') AND email LIKE CONCAT('%', :email, '%')")
    Mono<Long> countByUsernameLikeAndEmailLike(String username, String email);
    
    /**
     * 统计用户名和状态匹配的用户总数
     * @param username 用户名
     * @param status 状态
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') AND status = :status")
    Mono<Long> countByUsernameLikeAndStatus(String username, Integer status);
    
    /**
     * 统计邮箱和状态匹配的用户总数
     * @param email 邮箱
     * @param status 状态
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND email LIKE CONCAT('%', :email, '%') AND status = :status")
    Mono<Long> countByEmailLikeAndStatus(String email, Integer status);
    
    /**
     * 统计用户名、邮箱和状态匹配的用户总数
     * @param username 用户名
     * @param email 邮箱
     * @param status 状态
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0 AND username LIKE CONCAT('%', :username, '%') AND email LIKE CONCAT('%', :email, '%') AND status = :status")
    Mono<Long> countByUsernameLikeAndEmailLikeAndStatus(String username, String email, Integer status);
} 