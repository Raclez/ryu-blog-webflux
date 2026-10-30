package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.PostCategory;
import com.ryu.blog.entity.PostTag;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.CategoryRepository;
import com.ryu.blog.repository.PostCategoryRepository;
import com.ryu.blog.repository.PostTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * 文章关联关系服务
 *
 * <p>承载文章与分类、标签的关联关系维护。
 * 这里只有一种实现 —— {@code ArticleServiceImpl} 与 {@code CategoryServiceImpl}
 * 之前各写了一份，语义还不一致（后者会校验分类存在并驱逐分类缓存）。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleRelationService {

    private final PostCategoryRepository postCategoryRepository;
    private final PostTagRepository postTagRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 添加文章分类关联
     *
     * @param articleId  文章ID
     * @param categoryId 分类ID
     * @return 关联记录
     */
    public Mono<PostCategory> addArticleCategory(Long articleId, Long categoryId) {
        log.debug("添加文章分类关联: 文章ID={}, 分类ID={}", articleId, categoryId);

        // 分类必须存在，否则会挂上指向不存在分类的脏关联
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(BusinessException.categoryNotFound()))
                .flatMap(category -> postCategoryRepository.findByPostIdAndCategoryId(articleId, categoryId)
                        .switchIfEmpty(Mono.defer((Supplier<Mono<PostCategory>>) () -> {
                            PostCategory articleCategory = new PostCategory();
                            articleCategory.setPostId(articleId);
                            articleCategory.setCategoryId(categoryId);
                            articleCategory.setCreateTime(LocalDateTime.now());
                            return postCategoryRepository.save(articleCategory);
                        })))
                .doOnSuccess(ac -> log.debug("添加文章分类关联成功: 文章ID={}, 分类ID={}", articleId, categoryId))
                .doOnError(e -> log.error("添加文章分类关联失败: 文章ID={}, 分类ID={}", articleId, categoryId, e));
    }

    /**
     * 移除文章分类关联
     *
     * @param articleId  文章ID
     * @param categoryId 分类ID
     * @return 完成信号
     */
    public Mono<Void> removeArticleCategory(Long articleId, Long categoryId) {
        log.debug("移除文章分类关联: 文章ID={}, 分类ID={}", articleId, categoryId);

        return postCategoryRepository.findByPostIdAndCategoryId(articleId, categoryId)
                .flatMap(postCategoryRepository::delete)
                .doOnSuccess(v -> log.debug("移除文章分类关联成功: 文章ID={}, 分类ID={}", articleId, categoryId))
                .doOnError(e -> log.error("移除文章分类关联失败: 文章ID={}, 分类ID={}", articleId, categoryId, e));
    }

    /**
     * 移除文章所有分类关联
     *
     * @param articleId 文章ID
     * @return 完成信号
     */
    public Mono<Void> removeAllArticleCategories(Long articleId) {
        log.debug("移除文章所有分类关联: 文章ID={}", articleId);

        return postCategoryRepository.deleteByPostId(articleId)
                .doOnSuccess(v -> log.debug("移除文章所有分类关联成功: 文章ID={}", articleId))
                .doOnError(e -> log.error("移除文章所有分类关联失败: 文章ID={}", articleId, e));
    }

    /**
     * 获取文章的分类ID列表
     *
     * @param articleId 文章ID
     * @return 分类ID列表
     */
    public Flux<Long> getArticleCategoryIds(Long articleId) {
        log.debug("获取文章的分类IDs: 文章ID={}", articleId);

        return postCategoryRepository.findByPostId(articleId)
                .map(PostCategory::getCategoryId)
                .doOnComplete(() -> log.debug("获取文章的分类IDs完成: 文章ID={}", articleId))
                .doOnError(e -> log.error("获取文章的分类IDs失败: 文章ID={}", articleId, e));
    }

    /**
     * 添加文章标签关联（先清后建）
     *
     * @param articleId 文章ID
     * @param tagIds    标签ID列表
     * @return 完成信号
     */
    public Mono<Void> addArticleTags(Long articleId, List<String> tagIds) {
        if (articleId == null || tagIds == null || tagIds.isEmpty()) {
            return Mono.empty();
        }

        log.debug("添加文章标签关联: 文章ID={}, 标签IDs={}", articleId, tagIds);

        // 先删除现有关联
        return postTagRepository.deleteByPostId(articleId)
                .then(Flux.fromIterable(tagIds)
                        .<PostTag>flatMap(tagIdStr -> {
                            try {
                                Long tagId = Long.parseLong(tagIdStr);
                                PostTag postTag = new PostTag();
                                postTag.setPostId(articleId);
                                postTag.setTagId(tagId);
                                postTag.setCreateTime(LocalDateTime.now());
                                return postTagRepository.save(postTag);
                            } catch (NumberFormatException e) {
                                log.warn("标签ID格式错误: {}, 将被忽略", tagIdStr);
                                return Mono.empty();
                            }
                        })
                        .collectList()
                        .doOnSuccess(savedTags -> log.debug("文章标签关联保存成功: 文章ID={}, 保存数量={}", articleId, savedTags.size()))
                        .doOnError(e -> log.error("文章标签关联保存失败: 文章ID={}", articleId, e))
                        .then());
    }
}
