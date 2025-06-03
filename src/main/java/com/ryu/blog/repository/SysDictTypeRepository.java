package com.ryu.blog.repository;

import com.ryu.blog.entity.SysDictType;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统字典类型数据访问接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface SysDictTypeRepository extends R2dbcRepository<SysDictType, Long> {

    /**
     * 查询所有未删除的字典类型
     *
     * @return 未删除的字典类型列表
     */
    @Query("SELECT * FROM t_sys_dict_types WHERE is_deleted = 0 ORDER BY id ASC")
    Flux<SysDictType> findAllNotDeleted();

    /**
     * 根据字典类型编码查询未删除的记录
     *
     * @param dictType 字典类型编码
     * @return 字典类型信息
     */
    @Query("SELECT * FROM t_sys_dict_types WHERE dict_type = :dictType AND is_deleted = 0")
    Mono<SysDictType> findByDictTypeAndNotDeleted(String dictType);

    /**
     * 根据字典类型编码查询
     *
     * @param dictType 字典类型编码
     * @return 字典类型信息
     */
    Mono<SysDictType> findByDictType(String dictType);

    /**
     * 查询所有启用的字典类型
     *
     * @return 启用的字典类型列表
     */
    @Query("SELECT * FROM t_sys_dict_types WHERE status = 1 AND is_deleted = 0 ORDER BY id ASC")
    Flux<SysDictType> findAllEnabled();
    
    /**
     * 根据名称模糊查询字典类型
     *
     * @param typeName 字典类型名称
     * @return 匹配的字典类型列表
     */
    @Query("SELECT * FROM t_sys_dict_types WHERE type_name LIKE CONCAT('%', :typeName, '%') AND is_deleted = 0 ORDER BY id ASC")
    Flux<SysDictType> findByTypeNameLike(String typeName);
    
    /**
     * 根据ID查询未删除的记录
     *
     * @param id 字典类型ID
     * @return 字典类型信息
     */
    @Query("SELECT * FROM t_sys_dict_types WHERE id = :id AND is_deleted = 0")
    Mono<SysDictType> findByIdAndNotDeleted(Long id);
    
    /**
     * 检查字典类型编码是否已存在（未删除的记录）
     *
     * @param dictType 字典类型编码
     * @return 存在数量
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_types WHERE dict_type = :dictType AND is_deleted = 0")
    Mono<Integer> countByDictTypeAndNotDeleted(String dictType);
    
    /**
     * 检查字典类型编码是否已存在
     *
     * @param dictType 字典类型编码
     * @return 存在数量
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_types WHERE dict_type = :dictType")
    Mono<Integer> countByDictType(String dictType);
} 