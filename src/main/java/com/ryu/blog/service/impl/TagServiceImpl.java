package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagListDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.entity.PostTag;
import com.ryu.blog.entity.Tag;
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
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final TagMapper tagMapper;


    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> createTag(TagCreateDTO tagCreateDTO) {
        Tag tag = tagMapper.toTag(tagCreateDTO);
        
        return checkTagNameExists(tag.getName())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new RuntimeException(MessageConstants.TAG_NAME_EXISTS));
                    }
                    
                    // 设置默认值
                    tag.setCreateTime(LocalDateTime.now());
                    tag.setUpdateTime(LocalDateTime.now());
                    tag.setIsDeleted(false);
                    
                    return tagRepository.save(tag).thenReturn(true);
                });
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> updateTag(TagUpdateDTO tagUpdateDTO) {
        return tagRepository.findById(tagUpdateDTO.getId())
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.TAG_NOT_FOUND)))
                .flatMap(existingTag -> {
                    // 如果标签名称有变化，需要检查是否已存在
                    if (tagUpdateDTO.getName() != null && !tagUpdateDTO.getName().equals(existingTag.getName())) {
                        return checkTagNameExists(tagUpdateDTO.getName())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(new RuntimeException(MessageConstants.TAG_NAME_EXISTS));
                                    }
                                    return processTagUpdate(tagUpdateDTO, existingTag);
                                });
                    } else {
                        return processTagUpdate(tagUpdateDTO, existingTag);
                    }
                });
    }
    
    private Mono<Boolean> processTagUpdate(TagUpdateDTO tagUpdateDTO, Tag existingTag) {
        Tag updatedTag = tagMapper.updateTagFromDTO(tagUpdateDTO, existingTag);
        updatedTag.setUpdateTime(LocalDateTime.now());
        return tagRepository.save(updatedTag).thenReturn(true);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_DETAIL_KEY + "' + #id")
    public Mono<TagVO> getTagById(Long id) {
        return tagRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.TAG_NOT_FOUND)))
                .map(tagMapper::toTagVO);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_DETAIL_KEY + "' + #id"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> deleteTag(Long id) {
        return tagRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.TAG_NOT_FOUND)))
                .flatMap(tag -> {
                    // 检查是否有关联的文章
                    return postTagRepository.findByTagId(id)
                            .collectList()
                            .flatMap(articleTags -> {
                                // 准备标签进行逻辑删除
                                tag.setIsDeleted(true);
                                tag.setUpdateTime(LocalDateTime.now());
                                
                                if (!articleTags.isEmpty()) {
                                    // 删除标签前先删除文章标签关联
                                    return postTagRepository.deleteByTagId(id)
                                            .then(tagRepository.save(tag))
                                            .thenReturn(true);
                                } else {
                                    // 逻辑删除标签
                                    return tagRepository.save(tag).thenReturn(true);
                                }
                            });
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ALL_KEY + "' + #withCount")
    public Flux<TagVO> getAllTags(boolean withCount) {
        if (withCount) {
            return getAllTagsWithCount();
        } else {
            return getAllTagsWithoutCount();
        }
    }
    
    private Flux<TagVO> getAllTagsWithoutCount() {
        return tagRepository.findAllTags()
                .map(tagMapper::toTagVO);
    }
    
    private Flux<TagVO> getAllTagsWithCount() {
        return tagRepository.findAllTags()
                .flatMap(tag -> {
                    return tagRepository.countPostsByTagId(tag.getId())
                            .map(count -> tagMapper.toTagVOWithArticleCount(tag, count));
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "' + #articleId")
    public Flux<TagVO> getTagsByArticleId(Long articleId) {
        return tagRepository.findByPostId(articleId)
                .map(tagMapper::toTagVO);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_HOT_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ALL_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> addTagsToArticle(Long articleId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Mono.just(true);
        }
        
        // 先删除原有的标签关联
        return removeTagsFromArticle(articleId)
                .then(Flux.fromIterable(tagIds)
                        .flatMap(tagId -> {
                            PostTag articleTag = new PostTag();
                            articleTag.setPostId(articleId);
                            articleTag.setTagId(tagId);
                            articleTag.setCreateTime(LocalDateTime.now());
                            return postTagRepository.save(articleTag);
                        })
                        .then(Mono.just(true)));
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ARTICLE_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_HOT_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_ALL_KEY + "*'"),
        @CacheEvict(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_PAGE_PATTERN + "'")
    })
    public Mono<Boolean> removeTagsFromArticle(Long articleId) {
        return postTagRepository.deleteByPostId(articleId)
                .then(Mono.just(true));
    }

    @Override
    public Mono<Boolean> checkTagNameExists(String name) {
        return tagRepository.countByName(name)
                .map(count -> count > 0);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE_NAME, key = "'" + CacheConstants.TAG_HOT_KEY + "' + #limit")
    public Flux<TagVO> getHotTags(int limit) {
        return tagRepository.findHotTags(limit)
                .flatMap(tag -> tagRepository.countPostsByTagId(tag.getId())
                        .map(count -> tagMapper.toTagVOWithArticleCount(tag, count)));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.TAG_CACHE_NAME, 
               key = "'" + CacheConstants.TAG_PAGE_KEY + "' + #tagListDTO.currentPage + ':size:' + #tagListDTO.pageSize + ':keyword:' + #tagListDTO.keyword")
    public Mono<PageResult<TagVO>> getTagByPage(TagListDTO tagListDTO) {
        // 创建分页请求
        int page = Math.max(0, tagListDTO.getCurrentPage() - 1); // Spring Data页码从0开始
        int size = tagListDTO.getPageSize();
        Pageable pageable = PageRequest.of(page, size);
        String keyword = tagListDTO.getKeyword();
        
        // 查询总记录数
        return tagRepository.countByKeyword(keyword)
                .flatMap(total -> {
                    if (total == 0) {
                        // 如果没有记录，返回空页
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
                                return pageResult;
                            });
                })
                .doOnSuccess(result -> log.debug("分页查询标签成功，总数: {}", result.getTotal()))
                .doOnError(e -> log.error("分页查询标签失败: {}", e.getMessage(), e));
    }
} 