package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagListDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.entity.PostTag;
import com.ryu.blog.entity.Tag;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.TagMapper;
import com.ryu.blog.repository.PostTagRepository;
import com.ryu.blog.repository.TagRepository;
import com.ryu.blog.service.TagService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签服务实现类
 * <p>
 * 提供标签管理的核心业务逻辑实现，包括：
 * <ul>
 *   <li>标签的CRUD操作</li>
 *   <li>标签与文章的关联管理</li>
 *   <li>标签查询（分页、热门标签、文章标签）</li>
 *   <li>标签名称唯一性校验</li>
 * </ul>
 * 
 * <p>缓存策略：
 * <ul>
 *   <li>标签详情：按ID缓存</li>
 *   <li>标签列表：按查询条件缓存</li>
 *   <li>文章标签：按文章ID缓存</li>
 *   <li>热门标签：按数量限制缓存</li>
 *   <li>分页查询：按页码、大小和关键字缓存</li>
 * </ul>
 * 
 * <p>所有修改操作（创建、更新、删除）会清除相关缓存，确保数据一致性
 * 
 * @author ryu
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final TagMapper tagMapper;


    /**
     * 创建标签
     * <p>
     * 创建新标签前会先检查标签名称是否已存在，确保名称唯一性。
     * 创建成功后会清除所有标签相关的缓存。
     * 
     * @param tagCreateDTO 标签创建DTO
     * @return 创建结果，true表示成功
     * @throws BusinessException 当标签名称已存在时抛出
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> createTag(TagCreateDTO tagCreateDTO) {
        log.debug("[标签服务] 开始创建标签 - 标签名称: {}", tagCreateDTO.getName());
        
        Tag tag = tagMapper.toTag(tagCreateDTO);
        
        return checkTagNameExists(tag.getName())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("[标签服务] 标签名称已存在 - 标签名称: {}", tag.getName());
                        return Mono.error(BusinessException.tagNameExists());
                    }
                    
                    // 设置默认值
                    LocalDateTime now = LocalDateTime.now();
                    tag.setCreateTime(now);
                    tag.setUpdateTime(now);
                    tag.setIsDeleted(false);
                    
                    log.debug("[标签服务] 保存标签到数据库 - 标签名称: {}", tag.getName());
                    return tagRepository.save(tag)
                            .doOnSuccess(savedTag -> log.info("[标签服务] 标签创建成功 - 标签ID: {}, 标签名称: {}", 
                                    savedTag.getId(), savedTag.getName()))
                            .thenReturn(true);
                })
                .doOnError(e -> {
                    if (!(e instanceof BusinessException)) {
                        log.error("[标签服务] 创建标签失败 - 标签名称: {}, 错误: {}", 
                                tagCreateDTO.getName(), e.getMessage(), e);
                    }
                });
    }
    
    /**
     * 更新标签
     * <p>
     * 更新标签信息。如果修改了标签名称，会先检查新名称是否已被使用。
     * 更新成功后会清除所有标签相关的缓存。
     * 
     * @param tagUpdateDTO 标签更新DTO
     * @return 更新结果，true表示成功
     * @throws BusinessException 当标签不存在或新名称已被使用时抛出
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> updateTag(TagUpdateDTO tagUpdateDTO) {
        log.debug("[标签服务] 开始更新标签 - 标签ID: {}", tagUpdateDTO.getId());
        
        return tagRepository.findById(tagUpdateDTO.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[标签服务] 标签不存在 - 标签ID: {}", tagUpdateDTO.getId());
                    return Mono.error(BusinessException.tagNotFound());
                }))
                .flatMap(existingTag -> {
                    log.debug("[标签服务] 找到标签 - 标签ID: {}, 原名称: {}", 
                            existingTag.getId(), existingTag.getName());
                    
                    // 如果标签名称有变化，需要检查新名称是否已存在
                    if (tagUpdateDTO.getName() != null && !tagUpdateDTO.getName().equals(existingTag.getName())) {
                        log.debug("[标签服务] 标签名称发生变化，检查新名称是否存在 - 新名称: {}", 
                                tagUpdateDTO.getName());
                        
                        return checkTagNameExists(tagUpdateDTO.getName())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        log.warn("[标签服务] 新标签名称已存在 - 新名称: {}", 
                                                tagUpdateDTO.getName());
                                        return Mono.error(BusinessException.tagNameExists());
                                    }
                                    return processTagUpdate(tagUpdateDTO, existingTag);
                                });
                    } else {
                        return processTagUpdate(tagUpdateDTO, existingTag);
                    }
                })
                .doOnSuccess(result -> log.info("[标签服务] 标签更新成功 - 标签ID: {}", tagUpdateDTO.getId()))
                .doOnError(e -> {
                    if (!(e instanceof BusinessException)) {
                        log.error("[标签服务] 更新标签失败 - 标签ID: {}, 错误: {}", 
                                tagUpdateDTO.getId(), e.getMessage(), e);
                    }
                });
    }
    
    /**
     * 处理标签更新
     * <p>
     * 将DTO中的数据更新到现有标签实体，并保存到数据库
     * 
     * @param tagUpdateDTO 标签更新DTO
     * @param existingTag 现有标签实体
     * @return 更新结果
     */
    private Mono<Boolean> processTagUpdate(TagUpdateDTO tagUpdateDTO, Tag existingTag) {
        log.debug("[标签服务] 执行标签更新 - 标签ID: {}", existingTag.getId());
        
        Tag updatedTag = tagMapper.updateTagFromDTO(tagUpdateDTO, existingTag);
        updatedTag.setUpdateTime(LocalDateTime.now());
        
        return tagRepository.save(updatedTag)
                .doOnSuccess(saved -> log.debug("[标签服务] 标签保存成功 - 标签ID: {}", saved.getId()))
                .thenReturn(true);
    }

    /**
     * 根据ID获取标签详情
     * <p>
     * 查询结果会被缓存，缓存键为：tag:detail:{id}
     * 
     * @param id 标签ID
     * @return 标签VO对象
     * @throws BusinessException 当标签不存在时抛出
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_DETAIL_KEY + "' + #id")
    public Mono<TagVO> getTagById(Long id) {
        log.debug("[标签服务] 查询标签详情 - 标签ID: {}", id);
        
        return tagRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[标签服务] 标签不存在 - 标签ID: {}", id);
                    return Mono.error(BusinessException.tagNotFound());
                }))
                .map(tagMapper::toTagVO)
                .doOnSuccess(tagVO -> log.debug("[标签服务] 标签详情查询成功 - 标签ID: {}, 标签名称: {}", 
                        id, tagVO.getName()));
    }

    /**
     * 删除标签
     * <p>
     * 执行逻辑删除，将标签标记为已删除状态。
     * 如果标签关联了文章，会先删除所有关联关系。
     * 删除成功后会清除所有相关缓存。
     * 
     * @param id 标签ID
     * @return 删除结果，true表示成功
     * @throws BusinessException 当标签不存在时抛出
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_DETAIL_KEY + "' + #id"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> deleteTag(Long id) {
        log.debug("[标签服务] 开始删除标签 - 标签ID: {}", id);
        
        return tagRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[标签服务] 标签不存在 - 标签ID: {}", id);
                    return Mono.error(BusinessException.tagNotFound());
                }))
                .flatMap(tag -> {
                    log.debug("[标签服务] 找到标签，检查关联文章 - 标签ID: {}, 标签名称: {}", 
                            tag.getId(), tag.getName());
                    
                    // 检查是否有关联的文章
                    return postTagRepository.findByTagId(id)
                            .collectList()
                            .flatMap(articleTags -> {
                                // 准备标签进行逻辑删除
                                tag.setIsDeleted(true);
                                tag.setUpdateTime(LocalDateTime.now());
                                
                                if (!articleTags.isEmpty()) {
                                    log.info("[标签服务] 标签关联了 {} 篇文章，先删除关联关系 - 标签ID: {}", 
                                            articleTags.size(), id);
                                    
                                    // 删除标签前先删除文章标签关联
                                    return postTagRepository.deleteByTagId(id)
                                            .doOnSuccess(v -> log.debug("[标签服务] 文章标签关联删除成功 - 标签ID: {}", id))
                                            .then(tagRepository.save(tag))
                                            .doOnSuccess(saved -> log.info("[标签服务] 标签删除成功 - 标签ID: {}", id))
                                            .thenReturn(true);
                                } else {
                                    log.debug("[标签服务] 标签无关联文章，直接删除 - 标签ID: {}", id);
                                    
                                    // 逻辑删除标签
                                    return tagRepository.save(tag)
                                            .doOnSuccess(saved -> log.info("[标签服务] 标签删除成功 - 标签ID: {}", id))
                                            .thenReturn(true);
                                }
                            });
                })
                .doOnError(e -> {
                    if (!(e instanceof BusinessException)) {
                        log.error("[标签服务] 删除标签失败 - 标签ID: {}, 错误: {}", id, e.getMessage(), e);
                    }
                });
    }

    /**
     * 获取所有标签
     * <p>
     * 查询所有未删除的标签。可选择是否包含每个标签的文章数量统计。
     * 查询结果会被缓存，缓存键为：tag:all:{withCount}
     * 
     * @param withCount 是否包含文章数量统计
     * @return 标签列表流
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ALL_KEY + "' + #withCount")
    public Flux<TagVO> getAllTags(boolean withCount) {
        log.debug("[标签服务] 查询所有标签 - 是否包含文章数: {}", withCount);
        
        if (withCount) {
            return getAllTagsWithCount();
        } else {
            return getAllTagsWithoutCount();
        }
    }
    
    /**
     * 获取所有标签（不包含文章数量）
     * 
     * @return 标签列表流
     */
    private Flux<TagVO> getAllTagsWithoutCount() {
        return tagRepository.findAllTags()
                .map(tagMapper::toTagVO)
                .doOnComplete(() -> log.debug("[标签服务] 标签列表查询完成（不含文章数）"));
    }
    
    /**
     * 获取所有标签（包含文章数量）
     * <p>
     * 对每个标签查询其关联的文章数量
     * 
     * @return 标签列表流，包含文章数量
     */
    private Flux<TagVO> getAllTagsWithCount() {
        return tagRepository.findAllTags()
                .flatMap(tag -> {
                    return tagRepository.countPostsByTagId(tag.getId())
                            .map(count -> {
                                log.trace("[标签服务] 标签文章数统计 - 标签ID: {}, 文章数: {}", 
                                        tag.getId(), count);
                                return tagMapper.toTagVOWithArticleCount(tag, count);
                            });
                })
                .doOnComplete(() -> log.debug("[标签服务] 标签列表查询完成（含文章数）"));
    }

    /**
     * 根据文章ID获取标签列表
     * <p>
     * 查询指定文章关联的所有标签。
     * 查询结果会被缓存，缓存键为：tag:article:{articleId}
     * 
     * @param articleId 文章ID
     * @return 标签列表流
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "' + #articleId")
    public Flux<TagVO> getTagsByArticleId(Long articleId) {
        log.debug("[标签服务] 查询文章标签 - 文章ID: {}", articleId);
        
        return tagRepository.findByPostId(articleId)
                .map(tagMapper::toTagVO)
                .doOnComplete(() -> log.debug("[标签服务] 文章标签查询完成 - 文章ID: {}", articleId));
    }

    /**
     * 为文章添加标签
     * <p>
     * 为指定文章添加标签关联。会先删除文章原有的所有标签，再建立新的关联关系。
     * 操作成功后会清除相关缓存（文章标签、热门标签、标签列表等）。
     * 
     * @param articleId 文章ID
     * @param tagIds 标签ID列表
     * @return 操作结果，true表示成功
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_HOT_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ALL_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> addTagsToArticle(Long articleId, List<Long> tagIds) {
        log.debug("[标签服务] 为文章添加标签 - 文章ID: {}, 标签数: {}", articleId, 
                tagIds != null ? tagIds.size() : 0);
        
        if (tagIds == null || tagIds.isEmpty()) {
            log.debug("[标签服务] 标签列表为空，跳过添加 - 文章ID: {}", articleId);
            return Mono.just(true);
        }
        
        // 先删除原有的标签关联
        return removeTagsFromArticle(articleId)
                .then(Flux.fromIterable(tagIds)
                        .flatMap(tagId -> {
                            log.trace("[标签服务] 创建文章标签关联 - 文章ID: {}, 标签ID: {}", 
                                    articleId, tagId);
                            
                            PostTag articleTag = new PostTag();
                            articleTag.setPostId(articleId);
                            articleTag.setTagId(tagId);
                            articleTag.setCreateTime(LocalDateTime.now());
                            return postTagRepository.save(articleTag);
                        })
                        .then(Mono.just(true)))
                .doOnSuccess(result -> log.info("[标签服务] 文章标签添加成功 - 文章ID: {}, 标签数: {}", 
                        articleId, tagIds.size()))
                .doOnError(e -> log.error("[标签服务] 文章标签添加失败 - 文章ID: {}, 错误: {}", 
                        articleId, e.getMessage(), e));
    }

    /**
     * 移除文章的所有标签
     * <p>
     * 删除指定文章的所有标签关联关系。
     * 操作成功后会清除相关缓存。
     * 
     * @param articleId 文章ID
     * @return 操作结果，true表示成功
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_HOT_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_ALL_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> removeTagsFromArticle(Long articleId) {
        log.debug("[标签服务] 移除文章标签 - 文章ID: {}", articleId);
        
        return postTagRepository.deleteByPostId(articleId)
                .doOnSuccess(v -> log.info("[标签服务] 文章标签移除成功 - 文章ID: {}", articleId))
                .then(Mono.just(true))
                .doOnError(e -> log.error("[标签服务] 文章标签移除失败 - 文章ID: {}, 错误: {}", 
                        articleId, e.getMessage(), e));
    }

    /**
     * 检查标签名称是否存在
     * <p>
     * 用于创建或更新标签前的名称唯一性校验
     * 
     * @param name 标签名称
     * @return true表示名称已存在，false表示名称可用
     */
    @Override
    public Mono<Boolean> checkTagNameExists(String name) {
        log.debug("[标签服务] 检查标签名称是否存在 - 标签名称: {}", name);
        
        return tagRepository.countByName(name)
                .map(count -> {
                    boolean exists = count > 0;
                    log.debug("[标签服务] 标签名称检查结果 - 标签名称: {}, 是否存在: {}", name, exists);
                    return exists;
                });
    }

    /**
     * 获取热门标签
     * <p>
     * 根据标签关联的文章数量，查询最热门的标签列表。
     * 查询结果会被缓存，缓存键为：tag:hot:{limit}
     * 
     * @param limit 返回的标签数量限制
     * @return 热门标签列表流，按文章数量降序排列
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE, key = "'" + CacheConstants.TAG_HOT_KEY + "' + #limit")
    public Flux<TagVO> getHotTags(int limit) {
        log.debug("[标签服务] 查询热门标签 - 限制数量: {}", limit);
        
        return tagRepository.findHotTags(limit)
                .flatMap(tag -> tagRepository.countPostsByTagId(tag.getId())
                        .map(count -> {
                            log.trace("[标签服务] 热门标签统计 - 标签ID: {}, 标签名称: {}, 文章数: {}", 
                                    tag.getId(), tag.getName(), count);
                            return tagMapper.toTagVOWithArticleCount(tag, count);
                        }))
                .doOnComplete(() -> log.debug("[标签服务] 热门标签查询完成"));
    }

    /**
     * 分页查询标签列表
     * <p>
     * 支持关键字搜索的标签分页查询。可根据标签名称或描述进行模糊搜索。
     * 查询结果会被缓存，缓存键为：tag:page:{currentPage}:size:{pageSize}:keyword:{keyword}
     * 
     * @param tagListDTO 分页查询参数，包含页码、每页大小和搜索关键字
     * @return 分页结果，包含标签列表和分页信息
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE, 
               key = "'" + CacheConstants.TAG_PAGE_KEY + "' + #tagListDTO.currentPage + ':size:' + #tagListDTO.pageSize + ':keyword:' + #tagListDTO.keyword")
    public Mono<PageResult<TagVO>> getTagByPage(TagListDTO tagListDTO) {
        log.debug("[标签服务] 分页查询标签 - 页码: {}, 每页大小: {}, 关键字: {}", 
                tagListDTO.getCurrentPage(), tagListDTO.getPageSize(), tagListDTO.getKeyword());
        
        // 创建分页请求（Spring Data页码从0开始）
        int page = Math.max(0, tagListDTO.getCurrentPage() - 1);
        int size = tagListDTO.getPageSize();
        Pageable pageable = PageRequest.of(page, size);
        String keyword = tagListDTO.getKeyword();
        
        // 查询总记录数
        return tagRepository.countByKeyword(keyword)
                .flatMap(total -> {
                    log.debug("[标签服务] 标签总数: {}", total);
                    
                    if (total == 0) {
                        log.debug("[标签服务] 没有找到匹配的标签，返回空页");
                        return Mono.just(new PageResult<TagVO>());
                    }
                    
                    // 查询分页数据
                    return tagRepository.findByKeyword(keyword, pageable)
                            .map(tagMapper::toTagVO)
                            .collectList()
                            .map(tagVOs -> {
                                // 创建分页结果
                                PageResult<TagVO> pageResult = new PageResult<>();
                                pageResult.setRecords(tagVOs);
                                pageResult.setTotal(total);
                                pageResult.setSize(size);
                                pageResult.setCurrent(tagListDTO.getCurrentPage());
                                pageResult.setPages((total + size - 1) / size); // 计算总页数
                                
                                log.debug("[标签服务] 分页结果 - 总记录数: {}, 总页数: {}, 当前页记录数: {}", 
                                        total, pageResult.getPages(), tagVOs.size());
                                
                                return pageResult;
                            });
                })
                .doOnSuccess(result -> log.info("[标签服务] 分页查询标签成功 - 总记录数: {}, 当前页记录数: {}", 
                        result.getTotal(), result.getRecords().size()))
                .doOnError(e -> log.error("[标签服务] 分页查询标签失败 - 错误: {}", e.getMessage(), e));
    }
} 