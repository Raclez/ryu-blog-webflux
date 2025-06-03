package com.ryu.blog.repository;

import com.ryu.blog.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
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
     * 根据条件分页查询用户
     * @param params 查询条件
     * @param pageable 分页参数
     * @return 用户列表
     */
    default Flux<User> findByCondition(Map<String, Object> params, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT * FROM t_users WHERE is_deleted = 0");
        
        if (params.containsKey("username")) {
            sql.append(" AND username LIKE '%").append(params.get("username")).append("%'");
        }
        
        if (params.containsKey("email")) {
            sql.append(" AND email LIKE '%").append(params.get("email")).append("%'");
        }
        
        if (params.containsKey("status")) {
            sql.append(" AND status = ").append(params.get("status"));
        }
        
        sql.append(" ORDER BY create_time DESC LIMIT ").append(pageable.getPageSize())
           .append(" OFFSET ").append(pageable.getOffset());
        
        return findByQuery(sql.toString());
    }
    
    /**
     * 根据条件统计用户总数
     * @param params 查询条件
     * @return 用户总数
     */
    default Mono<Long> countByCondition(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM t_users WHERE is_deleted = 0");
        
        if (params.containsKey("username")) {
            sql.append(" AND username LIKE '%").append(params.get("username")).append("%'");
        }
        
        if (params.containsKey("email")) {
            sql.append(" AND email LIKE '%").append(params.get("email")).append("%'");
        }
        
        if (params.containsKey("status")) {
            sql.append(" AND status = ").append(params.get("status"));
        }
        
        return countByQuery(sql.toString());
    }
    
    /**
     * 根据SQL查询用户
     * @param sql SQL语句
     * @return 用户列表
     */
    @Query("?#[0]")
    Flux<User> findByQuery(String sql);
    
    /**
     * 根据SQL统计用户
     * @param sql SQL语句
     * @return 用户总数
     */
    @Query("?#[0]")
    Mono<Long> countByQuery(String sql);
} 