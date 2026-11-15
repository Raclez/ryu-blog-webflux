package com.ryu.blog.service.impl;


import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.mapper.PostVersionMapper;
import com.ryu.blog.repository.PostVersionRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.ArticleVersionService;
import com.ryu.blog.service.impl.article.ArticleVisibilityPolicy;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.utils.SaTokenUtils;
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
                    version.setIsDeleted(false);
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
                        .doOnError(e -> log.error("文章版本创建失败: 文章ID={}", article.getId(), e));
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
                                version.setIsDeleted(true);
                                version.setUpdateTime(LocalDateTime.now());
                                return postVersionRepository.save(version);
                            })
                            .then()
                            .doOnSuccess(v -> log.info("旧版本清理完成: 文章ID={}, 删除数量={}", postId, toDelete))
                            .doOnError(e -> log.error("旧版本清理失败: 文章ID={}", postId, e));
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
                    .doOnError(e -> log.error("更新文章之前的最新版本为非最新失败: 文章ID={}", postId, e));
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

        // 在订阅前取当前登录用户（Sa-Token 上下文随响应式链传播，链内再取可能已丢失）
        final Long currentUserId = currentUserIdOrNull();
        // 可见性还有「管理员」一档（与写路径同档），判定依赖这一项
        final boolean canManageOthers = canManageOthersOrFalse();

        return canAccessArticle(articleId, currentUserId, canManageOthers)
                .flatMapMany(allowed -> {
                    if (!allowed) {
                        return Flux.error(BusinessException.postNotFound());
                    }
                    return postVersionRepository.findByPostIdWithCursor(articleId, cursor, 0, finalLimit)
                            .flatMap(version -> {
                                // 获取用户信息用于展示，但不设置到实体中
                                return userRepository.findById(version.getEditor())
                                        .map(user -> version)
                                        .defaultIfEmpty(version);
                            });
                })
                .doOnComplete(() -> log.debug("获取文章版本列表完成: 文章ID={}", articleId))
                .doOnError(e -> log.error("获取文章版本列表失败: 文章ID={}", articleId, e));
    }

    @Override
    public Mono<PostVersion> getVersion(Long articleId, Integer version) {
        log.debug("获取文章指定版本: 文章ID={}, 版本号={}", articleId, version);

        final Long currentUserId = currentUserIdOrNull();
        // 可见性还有「管理员」一档（与写路径同档），判定依赖这一项
        final boolean canManageOthers = canManageOthersOrFalse();

        return canAccessArticle(articleId, currentUserId, canManageOthers)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(BusinessException.postNotFound());
                    }
                    return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                            .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound(version)))
                            .flatMap(articleVersion -> {
                                // 获取用户信息用于展示，但不设置到实体中
                                return userRepository.findById(articleVersion.getEditor())
                                        .map(user -> articleVersion)
                                        .defaultIfEmpty(articleVersion);
                            });
                })
                .doOnSuccess(v -> log.debug("获取文章指定版本成功: 文章ID={}, 版本号={}", articleId, version))
                .doOnError(e -> log.error("获取文章指定版本失败: 文章ID={}, 版本号={}", articleId, version, e));
    }

    @Override
    public Mono<PostVersion> getLatestVersion(Long articleId) {
        log.debug("获取文章最新版本: 文章ID={}", articleId);

        final Long currentUserId = currentUserIdOrNull();
        // 可见性还有「管理员」一档（与写路径同档），判定依赖这一项
        final boolean canManageOthers = canManageOthersOrFalse();

        return canAccessArticle(articleId, currentUserId, canManageOthers)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(BusinessException.postNotFound());
                    }
                    return postVersionRepository.findLatestVersionByPostId(articleId, 0)
                            .switchIfEmpty(Mono.error(BusinessException.postNoVersionHistory()))
                            .flatMap(version -> {
                                // 获取用户信息用于展示，但不设置到实体中
                                return userRepository.findById(version.getEditor())
                                        .map(user -> version)
                                        .defaultIfEmpty(version);
                            });
                })
                .doOnSuccess(v -> log.debug("获取文章最新版本成功: 文章ID={}, 版本号={}", articleId, v.getVersion()))
                .doOnError(e -> log.error("获取文章最新版本失败: 文章ID={}", articleId, e));
    }

    /**
     * 取当前登录用户ID，未登录或上下文不可用时返回 null
     */
    private Long currentUserIdOrNull() {
        return SaTokenUtils.currentUserIdOrNull();
    }

    /**
     * 当前操作者是否拥有「管理他人资源」的能力（{@code system:user:manage}）
     *
     * <p>与 {@link #currentUserIdOrNull()} 同源同时机：可见性判定依赖这两项。
     */
    private boolean canManageOthersOrFalse() {
        return SaTokenUtils.currentCanManageOthers();
    }

    /**
     * 版本历史的访问校验
     *
     * <p>版本里存的是文章正文全文，可见性必须与文章详情一致：
     * <b>只有「已发布」且「公开」的文章，其版本历史才能被匿名读到</b>，
     * 其余情况只允许作者本人与「能管理他人」的管理者。
     *
     * <p>原先这些读接口完全不校验 —— 只要知道 postId（自增整数，可遍历枚举）
     * 就能拿到文章的历史版本内容，包括尚未发布的草稿内容。
     *
     * @param articleId 文章ID
     * @param currentUserId 当前登录用户ID（未登录为 null）
     * @param canManageOthers 当前操作者是否拥有「管理他人资源」的能力
     * @return 是否允许访问
     */
    private Mono<Boolean> canAccessArticle(Long articleId, Long currentUserId, boolean canManageOthers) {
        return postsRepository.findById(articleId)
                .map(article -> ArticleVisibilityPolicy.canView(article, currentUserId, canManageOthers))
                .defaultIfEmpty(false)
                .doOnNext(allowed -> {
                    if (!allowed) {
                        log.debug("文章未公开，拒绝访问版本历史: 文章ID={}", articleId);
                    }
                });
    }

    @Override
    @Transactional
    public Mono<Posts> rollbackToVersion(Long articleId, Integer version) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            log.info("回滚文章到指定版本: 文章ID={}, 版本号={}, 操作者={}", articleId, version, ctx);

            // 获取指定版本
            return postVersionRepository.findByPostIdAndVersionAndIsDeleted(articleId, version, 0)
                    .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound(version)))
                    .flatMap(articleVersion -> {
                        // 获取当前文章
                        return postsRepository.findById(articleId)
                                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                                .flatMap(article -> {
                                    // 归属校验：作者本人，或「能管理他人」的管理者
                                    // （与 ArticleCrudService 的写路径同一条规则）
                                    if (!OwnershipPolicy.canActOn(ctx, article.getUserId())) {
                                        log.warn("无权限操作此文章: 文章ID={}, 文章所有者={}, 操作者={}",
                                                articleId, article.getUserId(), ctx);
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
                    .doOnError(e -> log.error("文章回滚失败: 文章ID={}, 版本号={}", articleId, version, e));
        });
    }

    @Override
    @Transactional
    public Mono<Boolean> deleteVersion(Long id) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            log.info("删除文章版本: 版本ID={}, 操作者={}", id, ctx);

            return postVersionRepository.findById(id)
                    .switchIfEmpty(Mono.error(BusinessException.postVersionNotFound()))
                    .flatMap(version -> {
                        // 获取文章
                        return postsRepository.findById(version.getPostId())
                                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                                .flatMap(article -> {
                                    // 归属校验：作者本人，或「能管理他人」的管理者
                                    if (!OwnershipPolicy.canActOn(ctx, article.getUserId())) {
                                        log.warn("无权限删除此版本: 文章ID={}, 版本ID={}, 文章所有者={}, 操作者={}",
                                                version.getPostId(), id, article.getUserId(), ctx);
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
                                                version.setIsDeleted(true);
                                                version.setUpdateTime(LocalDateTime.now());
                                                return postVersionRepository.save(version)
                                                        .map(savedVersion -> true)
                                                        .doOnSuccess(result -> log.info("版本删除成功: 版本ID={}", id));
                                            });
                                });
                    })
                    .defaultIfEmpty(false)
                    .doOnError(e -> log.error("删除版本失败: 版本ID={}", id, e));
        });
    }

    @Override
    public Mono<Map<String, Object>> compareVersions(Long articleId, Integer version1, Integer version2) {
        log.debug("比较文章版本差异: 文章ID={}, 版本1={}, 版本2={}", articleId, version1, version2);

        // 在订阅前取当前登录用户（Sa-Token 上下文随响应式链传播，链内再取可能已丢失）
        final Long currentUserId = currentUserIdOrNull();
        // 可见性还有「管理员」一档（与写路径同档），判定依赖这一项
        final boolean canManageOthers = canManageOthersOrFalse();

        // 可见性校验必须排在「两个版本相同」的短路之前，
        // 否则未授权的人用 version1 == version2 就能试探出文章是否存在
        return canAccessArticle(articleId, currentUserId, canManageOthers)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(BusinessException.postNotFound());
                    }
                    if (version1.equals(version2)) {
                        log.warn("比较相同版本: 文章ID={}, 版本={}", articleId, version1);
                        return Mono.just(Map.<String, Object>of("message", "相同版本无差异"));
                    }
                    return compareVersionsInternal(articleId, version1, version2);
                })
                .doOnSuccess(diff -> log.debug("比较文章版本差异成功: 文章ID={}, 版本1={}, 版本2={}", articleId, version1, version2))
                .doOnError(e -> log.error("比较文章版本差异失败: 文章ID={}, 版本1={}, 版本2={}", articleId, version1, version2, e));
    }

    /**
     * 比较两个版本的实际内容（调用方已完成可见性校验）
     */
    private Mono<Map<String, Object>> compareVersionsInternal(Long articleId, Integer version1, Integer version2) {
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
                });
    }
} 