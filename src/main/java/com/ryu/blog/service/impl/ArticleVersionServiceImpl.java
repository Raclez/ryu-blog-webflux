package com.ryu.blog.service.impl;


import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.mapper.PostVersionMapper;
import com.ryu.blog.repository.PostVersionRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.ArticleVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章版本服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleVersionServiceImpl implements ArticleVersionService {

    private final PostVersionRepository postVersionRepository;
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;
    private final PostVersionMapper postVersionMapper;

    @Override
    @Transactional
    public Mono<PostVersion> createVersion(Posts article, String description) {
        log.debug("创建文章版本: 文章ID={}, 描述={}", article.getId(), description);
        
        if (article == null || article.getId() == null) {
            log.error("创建文章版本失败: 文章或文章ID为空");
            return Mono.error(new IllegalArgumentException("文章或文章ID不能为空"));
        }
        
        // 检查内容是否与最新版本相同，避免创建重复版本
        return postVersionRepository.findLatestVersionByPostId(article.getId(), 0)
                .flatMap(latestVersion -> {
                    // 比较内容是否相同
                    if (latestVersion.getContent() != null && 
                        latestVersion.getContent().equals(article.getContent())) {
                        log.debug("内容未变化，跳过版本创建: 文章ID={}", article.getId());
                        return Mono.just(latestVersion);
                    }
                    // 内容有变化，创建新版本
                    return createNewVersion(article, description);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 没有历史版本，创建第一个版本
                    return createNewVersion(article, description);
                }));
    }
    
    /**
     * 创建新版本（内部方法）
     */
    private Mono<PostVersion> createNewVersion(Posts article, String description) {
        // 计算文章内容字数
        final int wordCount = article.getContent() != null ? article.getContent().length() : 0;
        
        // 获取当前文章的最大版本号
        return postVersionRepository.findMaxVersionByPostId(article.getId())
                .defaultIfEmpty(0) // 如果没有版本，则默认为0
                .flatMap(maxVersion -> {
                    log.debug("获取到文章当前最大版本号: 文章ID={}, 最大版本号={}", article.getId(), maxVersion);
                    
                    // 创建新版本
                    PostVersion version = new PostVersion();
                    version.setPostId(article.getId());
                    version.setVersion(maxVersion + 1);
                    version.setContent(article.getContent());
                    version.setEditor(article.getUserId());
                    version.setDescription(description);
                    version.setCreateTime(LocalDateTime.now());
                    version.setUpdateTime(LocalDateTime.now());
                    version.setIsDeleted(0);
                    version.setWordCount(wordCount);
                    version.setIsLatest(true);
                    
                    // 将之前的最新版本标记为非最新（只更新一条记录）
                    return updatePreviousVersionsNotLatest(article.getId())
                        .then(postVersionRepository.save(version))
                        .flatMap(savedVersion -> {
                            // 保存成功后，检查并清理旧版本
                            return cleanupOldVersions(article.getId())
                                    .thenReturn(savedVersion);
                        })
                        .doOnSuccess(savedVersion -> log.info("文章版本创建成功: 文章ID={}, 版本号={}", article.getId(), savedVersion.getVersion()))
                        .doOnError(e -> log.error("文章版本创建失败: 文章ID={}, 错误信息={}", article.getId(), e.getMessage()));
                });
    }
    
    /**
     * 清理旧版本，保留最近的N个版本
     * 
     * @param postId 文章ID
     * @return 完成信号
     */
    private Mono<Void> cleanupOldVersions(Long postId) {
        final int MAX_VERSIONS = 50; // 最多保留50个版本
        
        return postVersionRepository.countByPostIdAndIsDeleted(postId, 0)
                .flatMap(count -> {
                    if (count <= MAX_VERSIONS) {
                        log.debug("版本数量未超限: 文章ID={}, 当前版本数={}", postId, count);
                        return Mono.empty();
                    }
                    
                    // 计算需要删除的版本数量
                    int toDelete = (int) (count - MAX_VERSIONS);
                    log.info("版本数量超限，开始清理: 文章ID={}, 当前版本数={}, 需要删除={}", postId, count, toDelete);
                    
                    // 查询最旧的N个版本（按版本号升序，排除最新版本）
                    return postVersionRepository.findByPostIdAndIsDeletedOrderByVersionDesc(postId, 0)
                            .filter(version -> !version.getIsLatest()) // 不删除最新版本
                            .sort((v1, v2) -> Integer.compare(v1.getVersion(), v2.getVersion())) // 按版本号升序
                            .take(toDelete) // 取最旧的N个
                            .flatMap(version -> {
                                // 逻辑删除
                                version.setIsDeleted(1);
                                version.setUpdateTime(LocalDateTime.now());
                                return postVersionRepository.save(version);
                            })
                            .then()
                            .doOnSuccess(v -> log.info("旧版本清理完成: 文章ID={}, 删除数量={}", postId, toDelete))
                            .doOnError(e -> log.error("旧版本清理失败: 文章ID={}, 错误={}", postId, e.getMessage()));
                })
                .then();
    }
    
    /**
     * 将文章当前的最新版本标记为非最新
     * 
     * @param postId 文章ID
     * @return 是否成功
     */
    private Mono<Boolean> updatePreviousVersionsNotLatest(Long postId) {
        log.debug("更新文章之前的最新版本为非最新: 文章ID={}", postId);
        
        // 只查询并更新当前的最新版本，而不是所有版本
        return postVersionRepository.findLatestVersionByPostId(postId, 0)
            .flatMap(latestVersion -> {
                latestVersion.setIsLatest(false);
                latestVersion.setUpdateTime(LocalDateTime.now());
                return postVersionRepository.save(latestVersion)
                    .map(saved -> true)
                    .doOnSuccess(result -> log.debug("更新文章之前的最新版本为非最新成功: 文章ID={}, 版本号={}", 
                            postId, latestVersion.getVersion()))
                    .doOnError(e -> log.error("更新文章之前的最新版本为非最新失败: 文章ID={}, 错误信息={}", postId, e.getMessage()));
            })
            .defaultIfEmpty(true); // 如果没有最新版本（第一次创建），直接返回true
    }

    @Override
    public Flux<PostVersion> getVersions(Long articleId, Long cursor, Integer limit) {
        log.debug("获取文章版本列表: 文章ID={}, 游标={}, 每页数量={}", articleId, cursor, limit);
        
        // 参数校验
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100; // 限制最大每页数量
        }
        final int finalLimit = limit;


        return postVersionRepository.findByPostIdWithCursor(articleId, cursor, 0, finalLimit)
                .flatMap(version -> {
                    // 获取用户信息用于展示，但不设置到实体中
                    return userRepository.findById(version.getEditor())
                            .map(user -> version)
                            .defaultIfEmpty(version);
                })
                .doOnComplete(() -> log.debug("获取文章版本列表完成: 文章ID={}", articleId))
                .doOnError(e -> log.error("获取文章版本列表失败: 文章ID={}, 错误信息={}", articleId, e.getMessage()));
    }

    @Override
    public Mono<PostVersion> getVersion(Long articleId, Integer version) {
        log.debug("获取文章指定版本: 文章ID={}, 版本号={}", articleId, version);
        
        return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound(version)))
                .flatMap(articleVersion -> {
                    // 获取用户信息用于展示，但不设置到实体中
                    return userRepository.findById(articleVersion.getEditor())
                            .map(user -> articleVersion)
                            .defaultIfEmpty(articleVersion);
                })
                .doOnSuccess(v -> log.debug("获取文章指定版本成功: 文章ID={}, 版本号={}", articleId, version))
                .doOnError(e -> log.error("获取文章指定版本失败: 文章ID={}, 版本号={}, 错误信息={}", articleId, version, e.getMessage()));
    }

    @Override
    public Mono<PostVersion> getLatestVersion(Long articleId) {
        log.debug("获取文章最新版本: 文章ID={}", articleId);
        
        return postVersionRepository.findLatestVersionByPostId(articleId, 0)
                .switchIfEmpty(Mono.error(BusinessException.postNoVersionHistory()))
                .flatMap(version -> {
                    // 获取用户信息用于展示，但不设置到实体中
                    return userRepository.findById(version.getEditor())
                            .map(user -> version)
                            .defaultIfEmpty(version);
                })
                .doOnSuccess(v -> log.debug("获取文章最新版本成功: 文章ID={}, 版本号={}", articleId, v.getVersion()))
                .doOnError(e -> log.error("获取文章最新版本失败: 文章ID={}, 错误信息={}", articleId, e.getMessage()));
    }

    @Override
    @Transactional
    public Mono<Posts> rollbackToVersion(Long articleId, Integer version, Long userId) {
        log.info("回滚文章到指定版本: 文章ID={}, 版本号={}, 用户ID={}", articleId, version, userId);
        
        // 获取指定版本
        return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound(version)))
                .flatMap(articleVersion -> {
                    // 获取当前文章
                    return postsRepository.findById(articleId)
                            .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                            .flatMap(article -> {
                                // 检查权限
                                if (!article.getUserId().equals(userId)) {
                                    log.warn("无权限操作此文章: 文章ID={}, 文章所有者={}, 请求用户={}", articleId, article.getUserId(), userId);
                                    return Mono.error(PermissionDeniedException.accessDenied("无权限操作此文章"));
                                }

                                // 更新文章内容为历史版本
                                article.setContent(articleVersion.getContent());
                                article.setUpdateTime(LocalDateTime.now());

                                // 保存更新后的文章，并创建回滚版本
                                return postsRepository.save(article)
                                        .flatMap(savedArticle -> 
                                                createVersion(savedArticle, "回滚到版本 " + version)
                                                        .thenReturn(savedArticle)
                                        );
                            });
                })
                .doOnSuccess(article -> log.info("文章回滚成功: 文章ID={}, 版本号={}", article.getId(), version))
                .doOnError(e -> log.error("文章回滚失败: 文章ID={}, 版本号={}, 错误信息={}", articleId, version, e.getMessage()));
    }

    @Override
    @Transactional
    public Mono<Boolean> deleteVersion(Long id, Long userId) {
        log.info("删除文章版本: 版本ID={}, 用户ID={}", id, userId);
        
        return postVersionRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound()))
                .flatMap(version -> {
                    // 获取文章
                    return postsRepository.findById(version.getPostId())
                            .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                            .flatMap(article -> {
                                // 检查权限
                                if (!article.getUserId().equals(userId)) {
                                    log.warn("无权限删除此版本: 文章ID={}, 版本ID={}, 文章所有者={}, 请求用户={}", 
                                           version.getPostId(), id, article.getUserId(), userId);
                                    return Mono.just(false);
                                }

                                // 不允许删除最新版本
                                return postVersionRepository.findLatestVersionByPostId(article.getId(), 0)
                                        .flatMap(latestVersion -> {
                                            if (latestVersion.getId().equals(id)) {
                                                log.warn("不允许删除最新版本: 版本ID={}", id);
                                                return Mono.just(false);
                                            }

                                            // 逻辑删除版本
                                            version.setIsDeleted(1);
                                            version.setUpdateTime(LocalDateTime.now());
                                            return postVersionRepository.save(version)
                                                    .map(savedVersion -> true)
                                                    .doOnSuccess(result -> log.info("版本删除成功: 版本ID={}", id));
                                        });
                            });
                })
                .defaultIfEmpty(false)
                .doOnError(e -> log.error("删除版本失败: 版本ID={}, 错误信息={}", id, e.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> compareVersions(Long articleId, Integer version1, Integer version2) {
        log.debug("比较文章版本差异: 文章ID={}, 版本1={}, 版本2={}", articleId, version1, version2);
        
        if (version1.equals(version2)) {
            log.warn("比较相同版本: 文章ID={}, 版本={}", articleId, version1);
            return Mono.just(Map.of("message", "相同版本无差异"));
        }
        
        // 获取两个版本
        Mono<PostVersion> v1Mono = postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version1, 0)
                .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound(version1)));
        
        Mono<PostVersion> v2Mono = postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version2, 0)
                .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound(version2)));

        return Mono.zip(v1Mono, v2Mono)
                .map(tuple -> {
                    PostVersion v1 = tuple.getT1();
                    PostVersion v2 = tuple.getT2();

                    Map<String, Object> diff = new HashMap<>();
                    
                    // 比较内容
                    if (!Objects.equals(v1.getContent(), v2.getContent())) {
                        diff.put("content", Map.of(
                            "v1", v1.getContent(), 
                            "v2", v2.getContent(),
                            "changed", true
                        ));
                    } else {
                        diff.put("content", Map.of("changed", false));
                    }
                    
                    // 计算字数差异
                    int v1WordCount = v1.getWordCount() != null ? v1.getWordCount() : 0;
                    int v2WordCount = v2.getWordCount() != null ? v2.getWordCount() : 0;
                    diff.put("wordCount", Map.of(
                        "v1", v1WordCount,
                        "v2", v2WordCount,
                        "diff", v2WordCount - v1WordCount
                    ));

                    // 添加版本信息
                    diff.put("v1", Map.of(
                            "version", v1.getVersion(),
                            "createTime", v1.getCreateTime(),
                            "description", v1.getDescription(),
                            "editor", v1.getEditor()
                    ));
                    
                    diff.put("v2", Map.of(
                            "version", v2.getVersion(),
                            "createTime", v2.getCreateTime(),
                            "description", v2.getDescription(),
                            "editor", v2.getEditor()
                    ));

                    return diff;
                })
                .doOnSuccess(diff -> log.debug("比较文章版本差异成功: 文章ID={}, 版本1={}, 版本2={}", articleId, version1, version2))
                .doOnError(e -> log.error("比较文章版本差异失败: 文章ID={}, 版本1={}, 版本2={}, 错误信息={}", 
                        articleId, version1, version2, e.getMessage()));
    }
} 