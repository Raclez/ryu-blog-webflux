package com.ryu.blog.repository;

import com.ryu.blog.entity.AiContentTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI内容模板存储库
 * 
 * @author ryu
 * @since 1.0
 */
@Repository
public interface AiContentTemplateRepository extends ReactiveCrudRepository<AiContentTemplate, Long> {

    /**
     * 根据类型查询模板列表
     *
     * @param type 模板类型
     * @param isDeleted 是否删除
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByTypeAndIsDeleted(String type, Integer isDeleted);

    /**
     * 根据类型分页查询模板列表
     *
     * @param type 模板类型
     * @param isDeleted 是否删除
     * @param pageable 分页参数
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByTypeAndIsDeleted(String type, Integer isDeleted, Pageable pageable);

    /**
     * 查询系统模板列表
     *
     * @param isSystem 是否系统模板
     * @param isDeleted 是否删除
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByIsSystemAndIsDeleted(Integer isSystem, Integer isDeleted);

    /**
     * 根据用户ID查询自定义模板列表
     *
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByUserIdAndIsDeleted(Long userId, Integer isDeleted);

    /**
     * 根据用户ID分页查询自定义模板列表
     *
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @param pageable 分页参数
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByUserIdAndIsDeleted(Long userId, Integer isDeleted, Pageable pageable);

    /**
     * 根据名称查询模板
     *
     * @param name 模板名称
     * @param isDeleted 是否删除
     * @return 模板信息
     */
    Mono<AiContentTemplate> findByNameAndIsDeleted(String name, Integer isDeleted);

    /**
     * 根据名称检查模板是否存在
     *
     * @param name 模板名称
     * @param isDeleted 是否删除
     * @return 是否存在
     */
    Mono<Boolean> existsByNameAndIsDeleted(String name, Integer isDeleted);

    /**
     * 查询所有模板（按创建时间倒序）
     *
     * @param isDeleted 是否删除
     * @param pageable 分页参数
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByIsDeletedOrderByCreateTimeDesc(Integer isDeleted, Pageable pageable);

    /**
     * 统计模板数量
     *
     * @param isDeleted 是否删除
     * @return 模板数量
     */
    Mono<Long> countByIsDeleted(Integer isDeleted);

    /**
     * 根据类型统计模板数量
     *
     * @param type 模板类型
     * @param isDeleted 是否删除
     * @return 模板数量
     */
    Mono<Long> countByTypeAndIsDeleted(String type, Integer isDeleted);

    /**
     * 根据用户ID统计自定义模板数量
     *
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @return 模板数量
     */
    Mono<Long> countByUserIdAndIsDeleted(Long userId, Integer isDeleted);

    /**
     * 查询所有未删除的模板
     *
     * @param isDeleted 是否删除
     * @return 模板列表
     */
    Flux<AiContentTemplate> findByIsDeleted(Integer isDeleted);
}
