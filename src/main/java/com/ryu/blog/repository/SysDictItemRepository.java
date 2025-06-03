package com.ryu.blog.repository;

import com.ryu.blog.entity.SysDictItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统字典项数据访问接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface SysDictItemRepository extends R2dbcRepository<SysDictItem, Long> {

    /**
     * 根据字典类型ID查询未删除的字典项列表
     *
     * @param dictTypeId 字典类型ID
     * @return 字典项列表
     */
    @Query("SELECT * FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND is_deleted = 0 ORDER BY sort ASC")
    Flux<SysDictItem> findByDictTypeIdAndNotDeletedOrderBySort(Long dictTypeId);

    /**
     * 根据字典类型ID查询字典项列表
     *
     * @param dictTypeId 字典类型ID
     * @return 字典项列表
     */
    Flux<SysDictItem> findByDictTypeIdOrderBySort(Long dictTypeId);

    /**
     * 根据字典类型ID查询启用且未删除的字典项列表
     *
     * @param dictTypeId 字典类型ID
     * @return 启用的字典项列表
     */
    @Query("SELECT * FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND status = 1 AND is_deleted = 0 ORDER BY sort ASC")
    Flux<SysDictItem> findEnabledByDictTypeId(Long dictTypeId);

    /**
     * 根据字典类型ID和字典项键查询未删除的记录
     *
     * @param dictTypeId  字典类型ID
     * @param dictItemKey 字典项键
     * @return 字典项信息
     */
    @Query("SELECT * FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND dict_item_key = :dictItemKey AND is_deleted = 0")
    Mono<SysDictItem> findByDictTypeIdAndDictItemKeyAndNotDeleted(Long dictTypeId, String dictItemKey);

    /**
     * 根据字典类型ID和字典项键查询
     *
     * @param dictTypeId  字典类型ID
     * @param dictItemKey 字典项键
     * @return 字典项信息
     */
    Mono<SysDictItem> findByDictTypeIdAndDictItemKey(Long dictTypeId, String dictItemKey);

    /**
     * 根据字典类型ID和字典项键查询启用且未删除的字典项
     *
     * @param dictTypeId  字典类型ID
     * @param dictItemKey 字典项键
     * @return 启用的字典项信息
     */
    @Query("SELECT * FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND dict_item_key = :dictItemKey AND status = 1 AND is_deleted = 0")
    Mono<SysDictItem> findEnabledByDictTypeIdAndDictItemKey(Long dictTypeId, String dictItemKey);

    /**
     * 检查字典项是否已存在（未删除的记录）
     *
     * @param dictTypeId  字典类型ID
     * @param dictItemKey 字典项键
     * @return 存在数量
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND dict_item_key = :dictItemKey AND is_deleted = 0")
    Mono<Integer> countByDictTypeIdAndDictItemKeyAndNotDeleted(Long dictTypeId, String dictItemKey);

    /**
     * 检查字典项是否已存在
     *
     * @param dictTypeId  字典类型ID
     * @param dictItemKey 字典项键
     * @return 存在数量
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND dict_item_key = :dictItemKey")
    Mono<Integer> countByDictTypeIdAndDictItemKey(Long dictTypeId, String dictItemKey);
    
    /**
     * 根据ID查询未删除的记录
     *
     * @param id 字典项ID
     * @return 字典项信息
     */
    @Query("SELECT * FROM t_sys_dict_items WHERE id = :id AND is_deleted = 0")
    Mono<SysDictItem> findByIdAndNotDeleted(Long id);
    
    /**
     * 删除字典类型下的所有字典项
     *
     * @param dictTypeId 字典类型ID
     * @return 影响行数
     */
    @Query("UPDATE t_sys_dict_items SET is_deleted = 1, update_time = NOW() WHERE dict_type_id = :dictTypeId AND is_deleted = 0")
    Mono<Integer> deleteByDictTypeId(Long dictTypeId);
    
    /**
     * 根据字典类型ID分页查询未删除的字典项
     *
     * @param dictTypeId 字典类型ID
     * @param pageable 分页参数
     * @return 字典项列表
     */
    @Query("SELECT * FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND is_deleted = 0 ORDER BY sort ASC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<SysDictItem> findByDictTypeId(Long dictTypeId, Pageable pageable);
    
    /**
     * 查询字典类型ID对应的未删除字典项总数
     *
     * @param dictTypeId 字典类型ID
     * @return 总记录数
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_items WHERE dict_type_id = :dictTypeId AND is_deleted = 0")
    Mono<Long> countByDictTypeId(Long dictTypeId);
    
    /**
     * 根据字典项标签模糊查询未删除的记录并分页
     *
     * @param dictItemValue 字典项标签
     * @param pageable 分页参数
     * @return 字典项列表
     */
    @Query("SELECT i.* FROM t_sys_dict_items i WHERE i.dict_item_value LIKE CONCAT('%', :dictItemValue, '%') AND i.is_deleted = 0 ORDER BY i.dict_type_id, i.sort LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<SysDictItem> findByDictItemValueLike(String dictItemValue, Pageable pageable);
    
    /**
     * 根据字典类型ID和字典项标签模糊查询未删除的记录并分页
     *
     * @param dictTypeId 字典类型ID
     * @param dictItemValue 字典项标签
     * @param pageable 分页参数
     * @return 字典项列表
     */
    @Query("SELECT i.* FROM t_sys_dict_items i WHERE i.dict_type_id = :dictTypeId AND i.dict_item_value LIKE CONCAT('%', :dictItemValue, '%') AND i.is_deleted = 0 ORDER BY i.sort LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<SysDictItem> findByDictTypeIdAndDictItemValueLike(Long dictTypeId, String dictItemValue, Pageable pageable);
    
    /**
     * 统计符合条件的未删除记录总数
     *
     * @param dictItemValue 字典项标签
     * @return 总记录数
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_items i WHERE i.dict_item_value LIKE CONCAT('%', :dictItemValue, '%') AND i.is_deleted = 0")
    Mono<Long> countByDictItemValueLike(String dictItemValue);
    
    /**
     * 统计符合条件的未删除记录总数
     *
     * @param dictTypeId 字典类型ID
     * @param dictItemValue 字典项标签
     * @return 总记录数
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_items i WHERE i.dict_type_id = :dictTypeId AND i.dict_item_value LIKE CONCAT('%', :dictItemValue, '%') AND i.is_deleted = 0")
    Mono<Long> countByDictTypeIdAndDictItemValueLike(Long dictTypeId, String dictItemValue);
    
    /**
     * 根据字典类型编码获取未删除的字典项
     *
     * @param dictType 字典类型编码
     * @return 字典项列表
     */
    @Query("SELECT i.* FROM t_sys_dict_items i " +
           "JOIN t_sys_dict_types t ON i.dict_type_id = t.id " +
           "WHERE t.dict_type = :dictType AND i.status = 1 AND i.is_deleted = 0 AND t.is_deleted = 0 " +
           "ORDER BY i.sort")
    Flux<SysDictItem> findByDictTypeCode(String dictType);
    
    /**
     * 更新字典项状态
     *
     * @param id 字典项ID
     * @param status 状态 (1-启用，0-禁用)
     * @return 更新行数
     */
    @Query("UPDATE t_sys_dict_items SET status = :status, update_time = NOW() WHERE id = :id AND is_deleted = 0")
    Mono<Integer> updateStatusById(Long id, Integer status);
    
    /**
     * 统计未删除的字典项总数
     *
     * @return 总记录数
     */
    @Query("SELECT COUNT(*) FROM t_sys_dict_items WHERE is_deleted = 0")
    Mono<Long> countNotDeleted();
} 