package com.ryu.blog.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.entity.PostTag;
import com.ryu.blog.entity.Tag;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.TagMapper;
import com.ryu.blog.repository.PostTagRepository;
import com.ryu.blog.repository.TagRepository;
import com.ryu.blog.service.TagService;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

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
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final TagMapper tagMapper;


    @Override
    @Transactional
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
                    
                    return saveTagAndClearCache(tag);
                });
    }
    
    /**
     * 保存标签并清除缓存
     * @param tag 要保存的标签
     * @return 操作结果
     */
    private Mono<Boolean> saveTagAndClearCache(Tag tag) {
        return tagRepository.save(tag)
                .flatMap(savedTag -> clearTagCache().thenReturn(true));
    }
    
    @Override
    @Transactional
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
        return updateTagAndReturnSuccess(updatedTag);
    }

    private Mono<Boolean> updateTagAndReturnSuccess(Tag tag) {
        tag.setUpdateTime(LocalDateTime.now());
        
        return tagRepository.save(tag)
                .flatMap(savedTag -> {
                    // 清除缓存
                    return clearTagCache()
                    // 查找使用该标签的文章，清除相关缓存
                        .then(Flux.from(postTagRepository.findByTagId(savedTag.getId()))
                            .map(PostTag::getPostId)
                            .flatMap(articleId -> {
                                String key = CacheConstants.ARTICLE_TAGS_KEY + articleId;
                                return reactiveRedisTemplate.delete(key);
                            })
                                .then())
                        .thenReturn(true);
                });
    }

    @Override
    public Mono<TagVO> getTagById(Long id) {
        return tagRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.TAG_NOT_FOUND)))
                .map(tagMapper::toTagVO);
    }

    @Override
    @Transactional
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
                                            .flatMap(count -> {
                                                return saveTagAndClearCache(tag)
                                                        .flatMap(result -> {
                                                            // 清除文章标签缓存
                                                            clearArticleTagsCache(articleTags);
                                                            return Mono.just(true);
                                                        });
                                            });
                                } else {
                                    // 逻辑删除标签
                                    return saveTagAndClearCache(tag);
                                }
                            });
                });
    }
    
    /**
     * 清除文章标签缓存
     * @param articleTags 文章标签关联列表
     */
    private void clearArticleTagsCache(List<PostTag> articleTags) {
        articleTags.stream()
                .map(PostTag::getPostId)
                .distinct()
                .forEach(articleId -> {
                    String key = CacheConstants.ARTICLE_TAGS_KEY + articleId;
                    reactiveRedisTemplate.delete(key).subscribe();
                });
    }

    @Override
    public Flux<TagVO> getAllTags(boolean withCount) {
        if (withCount) {
            return getAllTagsWithCount();
        } else {
            return getAllTagsWithoutCount();
        }
    }
    
    private Flux<TagVO> getAllTagsWithoutCount() {
        // 从缓存或数据库获取标签列表
        return getTagsFromCacheOrDatabase(
                CacheConstants.TAG_ALL_KEY, 
                "标签列表",
                () -> tagRepository.findAllTags(),
                Duration.ofHours(1)
        );
    }
    
    private Flux<TagVO> getAllTagsWithCount() {
        return tagRepository.findAllTags()
                .flatMap(tag -> {
                    return tagRepository.countPostsByTagId(tag.getId())
                            .map(count -> tagMapper.toTagVOWithArticleCount(tag, count));
                });
    }

    @Override
    public Flux<TagVO> getTagsByArticleId(Long articleId) {
        String key = CacheConstants.ARTICLE_TAGS_KEY + articleId;
        // 从缓存或数据库获取文章标签列表
        return getTagsFromCacheOrDatabase(
                key,
                "文章标签列表: 文章ID=" + articleId,
                () -> tagRepository.findByPostId(articleId),
                Duration.ofHours(1)
        );
    }
    
    /**
     * 从缓存或数据库获取标签数据
     * @param cacheKey 缓存键
     * @param logPrefix 日志前缀
     * @param databaseSupplier 数据库查询提供者
     * @param cacheDuration 缓存时间
     * @return 标签VO的Flux流
     */
    private Flux<TagVO> getTagsFromCacheOrDatabase(String cacheKey, String logPrefix, Supplier<Flux<Tag>> databaseSupplier, Duration cacheDuration) {
        // 先尝试从缓存中获取
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(json -> {
                    try {
                        List<Tag> tags = JsonUtils.deserialize(json, new TypeReference<List<Tag>>() {});
                        if (tags == null) {
                            return Mono.empty();
                        }
                        log.debug("从缓存获取{}成功: {}条", logPrefix, tags.size());
                        return Mono.just(tags);
                    } catch (Exception e) {
                        log.error("解析{}JSON数据失败: {}", logPrefix, e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .map(tagMapper::toTagVO)
                .switchIfEmpty(
                        databaseSupplier.get()
                                .map(tagMapper::toTagVO)
                                .collectList()
                                .flatMap(tagVOs -> {
                                    if (tagVOs.isEmpty()) {
                                        return Mono.just(tagVOs);
                                    }
                                    
                                    // 获取原始Tag列表用于缓存
                                    return databaseSupplier.get()
                                            .collectList()
                                            .flatMap(tags -> {
                                                String json = JsonUtils.serialize(tags);
                                                if (json != null) {
                                                    log.debug("更新{}缓存，共{}条数据", logPrefix, tags.size());
                                                    return reactiveRedisTemplate.opsForValue()
                                                            .set(cacheKey, json, cacheDuration)
                                                            .thenReturn(tagVOs);
                                                }
                                                return Mono.just(tagVOs);
                                            });
                                })
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @Override
    @Transactional
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
                        .then()
                        .flatMap(v -> {
                            // 清除文章标签缓存
                            String key = CacheConstants.ARTICLE_TAGS_KEY + articleId;
                            return reactiveRedisTemplate.delete(key).thenReturn(true);
                        }));
    }

    @Override
    @Transactional
    public Mono<Boolean> removeTagsFromArticle(Long articleId) {
        return postTagRepository.deleteByPostId(articleId)
                .then()
                .flatMap(v -> {
                    // 清除文章标签缓存
                    String key = CacheConstants.ARTICLE_TAGS_KEY + articleId;
                    return reactiveRedisTemplate.delete(key).thenReturn(true);
                });
    }

    @Override
    public Mono<Boolean> checkTagNameExists(String name) {
        return tagRepository.countByName(name)
                .map(count -> count > 0);
    }

    @Override
    public Flux<TagVO> getHotTags(int limit) {
        // 先尝试从缓存中获取
        String key = CacheConstants.TAG_HOT_KEY + limit;
        return reactiveRedisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        List<Tag> tags = JsonUtils.deserialize(json, new TypeReference<List<Tag>>() {});
                        if (tags == null) {
                            return Mono.empty();
                        }
                        log.debug("从缓存获取热门标签列表成功: {}条", tags.size());
                        return Mono.just(tags);
                    } catch (Exception e) {
                        log.error("解析热门标签JSON数据失败: {}", e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .flatMap(tag -> tagRepository.countPostsByTagId(tag.getId())
                        .map(count -> tagMapper.toTagVOWithArticleCount(tag, count)))
                .switchIfEmpty(
                        tagRepository.findHotTags(limit)
                                .flatMap(tag -> tagRepository.countPostsByTagId(tag.getId())
                                        .map(count -> tagMapper.toTagVOWithArticleCount(tag, count)))
                                .collectList()
                                .flatMap(tagVOs -> {
                                    if (tagVOs.isEmpty()) {
                                        return Mono.just(tagVOs);
                                    }
                                    
                                    // 获取原始Tag列表用于缓存
                                    return tagRepository.findHotTags(limit)
                                            .collectList()
                                            .flatMap(tags -> {
                                                String json = JsonUtils.serialize(tags);
                                                if (json != null) {
                                                    log.debug("更新热门标签缓存，共{}条数据", tags.size());
                                                    return reactiveRedisTemplate.opsForValue()
                                                            .set(key, json, Duration.ofHours(1))
                                                            .thenReturn(tagVOs);
                                                }
                                                return Mono.just(tagVOs);
                                            });
                                })
                                .flatMapMany(Flux::fromIterable)
                );
    }
    
    /**
     * 清除标签相关缓存
     * @return 完成信号
     */
    private Mono<Void> clearTagCache() {
        log.debug("开始清理标签缓存...");
        return Mono.when(
                reactiveRedisTemplate.delete(CacheConstants.TAG_ALL_KEY),
                reactiveRedisTemplate.delete(CacheConstants.TAG_WITH_COUNT_KEY),
                reactiveRedisTemplate.keys(CacheConstants.TAG_HOT_KEY + "*")
                        .flatMap(reactiveRedisTemplate::delete)
                        .then()
        ).doOnSuccess(v -> log.debug("标签缓存清理完成"));
    }
} 