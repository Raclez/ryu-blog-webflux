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
    private final PostVersionMapper PostVersionMapper;

    @Override
    @Transactional
    public Mono<PostVersion> createVersion(Posts article, String description) {
        // 获取当前文章的最大版本号
        return postVersionRepository.findMaxVersionByPostId(article.getId())
                .defaultIfEmpty(0) // 如果没有版本，则默认为0
                .flatMap(maxVersion -> {
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

                    return postVersionRepository.save(version);
                });
    }

    @Override
    public Flux<PostVersion> getVersions(Long articleId) {
        return postVersionRepository.findByPostIdAndIsDeletedOrderByVersionDesc(articleId, 0)
                .flatMap(version -> {
                    // 获取用户信息用于展示，但不设置到实体中
                    return userRepository.findById(version.getEditor())
                            .map(user -> version)
                            .defaultIfEmpty(version);
                });
    }

    @Override
    public Mono<Map<String, Object>> getVersionsPaged(Long articleId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        int finalPage = page;
        int finalSize = size;

        return postVersionRepository.countByPostIdAndIsDeleted(articleId, 0)
                .flatMap(total -> {
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
                });
    }

    @Override
    public Mono<PostVersion> getVersion(Long articleId, Integer version) {
        return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                .flatMap(articleVersion -> {
                    // 获取用户信息用于展示，但不设置到实体中
                    return userRepository.findById(articleVersion.getEditor())
                            .map(user -> articleVersion)
                            .defaultIfEmpty(articleVersion);
                });
    }

    @Override
    public Mono<PostVersion> getLatestVersion(Long articleId) {
        return postVersionRepository.findLatestVersionByPostId(articleId, 0)
                .flatMap(version -> {
                    // 获取用户信息用于展示，但不设置到实体中
                    return userRepository.findById(version.getEditor())
                            .map(user -> version)
                            .defaultIfEmpty(version);
                });
    }

    @Override
    @Transactional
    public Mono<Posts> rollbackToVersion(Long articleId, Integer version, Long userId) {
        // 获取指定版本
        return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                .flatMap(articleVersion -> {
                    // 获取当前文章
                    return postsRepository.findById(articleId)
                            .flatMap(article -> {
                                // 检查权限
                                if (!article.getUserId().equals(userId)) {
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
                });
    }

    @Override
    @Transactional
    public Mono<Boolean> deleteVersion(Long id, Long userId) {
        return postVersionRepository.findById(id)
                .flatMap(version -> {
                    // 获取文章
                    return postsRepository.findById(version.getPostId())
                            .flatMap(article -> {
                                // 检查权限
                                if (!article.getUserId().equals(userId)) {
                                    return Mono.just(false);
                                }

                                // 不允许删除最新版本
                                return postVersionRepository.findLatestVersionByPostId(article.getId(), 0)
                                        .flatMap(latestVersion -> {
                                            if (latestVersion.getId().equals(id)) {
                                                return Mono.just(false);
                                            }

                                            // 逻辑删除版本
                                            version.setIsDeleted(1);
                                            version.setUpdateTime(LocalDateTime.now());
                                            return postVersionRepository.save(version)
                                                    .map(savedVersion -> true);
                                        });
                            });
                })
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Map<String, Object>> compareVersions(Long articleId, Integer version1, Integer version2) {
        // 获取两个版本
        Mono<PostVersion> v1Mono = postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version1, 0);
        Mono<PostVersion> v2Mono = postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version2, 0);

        return Mono.zip(v1Mono, v2Mono)
                .map(tuple -> {
                    PostVersion v1 = tuple.getT1();
                    PostVersion v2 = tuple.getT2();

                    Map<String, Object> diff = new HashMap<>();
                    
                    // 比较内容
                    if (!Objects.equals(v1.getContent(), v2.getContent())) {
                        diff.put("content", Map.of("v1", v1.getContent(), "v2", v2.getContent()));
                    }

                    // 添加版本信息
                    diff.put("v1", Map.of(
                            "version", v1.getVersion(),
                            "createTime", v1.getCreateTime(),
                            "description", v1.getDescription()
                    ));
                    diff.put("v2", Map.of(
                            "version", v2.getVersion(),
                            "createTime", v2.getCreateTime(),
                            "description", v2.getDescription()
                    ));

                    return diff;
                });
    }
} 