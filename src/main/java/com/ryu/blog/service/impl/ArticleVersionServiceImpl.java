package com.ryu.blog.service.impl;


import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.Posts;
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
                    
                    // 将之前的最新版本标记为非最新
                    return (maxVersion > 0 ? 
                            updatePreviousVersionsNotLatest(article.getId()) : 
                            Mono.just(true))
                        .then(postVersionRepository.save(version))
                        .doOnSuccess(savedVersion -> log.info("文章版本创建成功: 文章ID={}, 版本号={}", article.getId(), savedVersion.getVersion()))
                        .doOnError(e -> log.error("文章版本创建失败: 文章ID={}, 错误信息={}", article.getId(), e.getMessage()));
                });
    }
    
    /**
     * 将文章所有之前的版本标记为非最新
     * 
     * @param postId 文章ID
     * @return 是否成功
     */
    private Mono<Boolean> updatePreviousVersionsNotLatest(Long postId) {
        log.debug("更新文章之前的版本为非最新: 文章ID={}", postId);
        
        return postVersionRepository.findByPostIdAndIsDeletedOrderByVersionDesc(postId, 0)
            .collectList()
            .flatMap(versions -> {
                if (versions.isEmpty()) {
                    return Mono.just(true);
                }
                
                List<PostVersion> updatedVersions = versions.stream()
                    .map(version -> {
                        version.setIsLatest(false);
                        return version;
                    })
                    .collect(Collectors.toList());
                
                return Flux.fromIterable(updatedVersions)
                    .flatMap(postVersionRepository::save)
                    .then(Mono.just(true))
                    .doOnSuccess(result -> log.debug("更新文章之前的版本为非最新成功: 文章ID={}, 更新数量={}", postId, versions.size()))
                    .doOnError(e -> log.error("更新文章之前的版本为非最新失败: 文章ID={}, 错误信息={}", postId, e.getMessage()));
            });
    }

    @Override
    public Flux<PostVersion> getVersions(Long articleId) {
        log.debug("获取文章版本列表: 文章ID={}", articleId);
        
        return postVersionRepository.findByPostIdAndIsDeletedOrderByVersionDesc(articleId, 0)
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
    public Mono<Map<String, Object>> getVersionsPaged(Long articleId, int page, int size) {
        log.debug("分页获取文章版本列表: 文章ID={}, page={}, size={}", articleId, page, size);
        
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        int finalPage = page;
        int finalSize = size;

        return postVersionRepository.countByPostIdAndIsDeleted(articleId, 0)
                .flatMap(total -> {
                    log.debug("文章版本总数: 文章ID={}, 总数={}", articleId, total);
                    
                    long offset = (finalPage - 1) * finalSize;
                    return postVersionRepository.findByPostIdAndIsDeletedOrderByVersionDesc(articleId, 0, finalSize, offset)
                            .flatMap(version -> {
                                // 获取用户信息用于展示，但不设置到实体中
                                return userRepository.findById(version.getEditor())
                                        .map(user -> {
                                            // 这里可以使用Mapper转换为VO，但为了保持接口一致，仍然返回实体
                                            return version;
                                        })
                                        .defaultIfEmpty(version);
                            })
                            .collectList()
                            .map(versions -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("records", versions);
                                result.put("total", total);
                                result.put("pages", (total + finalSize - 1) / finalSize);
                                result.put("current", finalPage);
                                return result;
                            });
                })
                .doOnSuccess(result -> log.debug("分页获取文章版本列表成功: 文章ID={}, 返回记录数={}", 
                        articleId, ((List<?>)result.get("records")).size()))
                .doOnError(e -> log.error("分页获取文章版本列表失败: 文章ID={}, 错误信息={}", articleId, e.getMessage()));
    }

    @Override
    public Mono<PostVersion> getVersion(Long articleId, Integer version) {
        log.debug("获取文章指定版本: 文章ID={}, 版本号={}", articleId, version);
        
        return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("文章版本不存在")))
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
                .switchIfEmpty(Mono.error(new RuntimeException("文章没有版本记录")))
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
                .switchIfEmpty(Mono.error(new RuntimeException("指定的文章版本不存在")))
                .flatMap(articleVersion -> {
                    // 获取当前文章
                    return postsRepository.findById(articleId)
                            .switchIfEmpty(Mono.error(new RuntimeException("文章不存在")))
                            .flatMap(article -> {
                                // 检查权限
                                if (!article.getUserId().equals(userId)) {
                                    log.warn("无权限操作此文章: 文章ID={}, 文章所有者={}, 请求用户={}", articleId, article.getUserId(), userId);
                                    return Mono.error(new RuntimeException("无权限操作此文章"));
                                }

                                // 先保存当前版本
                                return createVersion(article, "回滚前自动保存")
                                        .then(Mono.defer(() -> {
                                            // 更新文章为历史版本
                                            article.setContent(articleVersion.getContent());
                                            article.setUpdateTime(LocalDateTime.now());

                                            // 保存更新后的文章
                                            return postsRepository.save(article)
                                                    // 创建新版本记录回滚操作
                                                    .flatMap(savedArticle -> createVersion(
                                                            savedArticle,
                                                            "回滚到版本 " + version)
                                                            .thenReturn(savedArticle));
                                        }));
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
                .switchIfEmpty(Mono.error(new RuntimeException("文章版本不存在")))
                .flatMap(version -> {
                    // 获取文章
                    return postsRepository.findById(version.getPostId())
                            .switchIfEmpty(Mono.error(new RuntimeException("文章不存在")))
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
                .switchIfEmpty(Mono.error(new RuntimeException("版本 " + version1 + " 不存在")));
        
        Mono<PostVersion> v2Mono = postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version2, 0)
                .switchIfEmpty(Mono.error(new RuntimeException("版本 " + version2 + " 不存在")));

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