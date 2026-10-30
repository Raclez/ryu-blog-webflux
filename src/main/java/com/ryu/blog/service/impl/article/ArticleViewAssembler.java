package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.BaseEntity;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.FileService;
import com.ryu.blog.vo.PostAdminListVO;
import com.ryu.blog.vo.PostFrontListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章视图装配服务
 *
 * <p>负责把 {@link Posts} 实体装配成前台 / 后台列表 VO，
 * 并负责列表场景下的批量关联数据查询（分类、标签、用户、统计、封面）。
 *
 * <p>所有关联数据都是「一次批量查询 + 内存映射」，
 * 避免逐行查询造成的 N+1。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleViewAssembler {

    private static final String DEFAULT_COVER_IMAGE = "/assets/images/default-cover.jpg";
    private static final String DEFAULT_AVATAR = "/assets/images/default-avatar.png";
    private static final String UNKNOWN_USER = "未知用户";

    private final PostsRepository postsRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final PostMapper postMapper;

    /**
     * 批量获取文章分类信息
     *
     * @param postIds 文章ID列表
     * @return 文章ID到分类信息(ID, 名称)的映射
     */
    public Mono<Map<Long, Tuple2<Long, String>>> batchGetArticleCategories(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.findPostsWithCategory(postIds)
                .filter(projection -> projection.getPostId() != null
                        && projection.getCategoryId() != null
                        && projection.getCategoryName() != null)
                // 一篇文章可以挂多个分类，这里只保留第一个。
                // 注意：Reactor 的 Flux 没有 collectMap(keyFn, valueFn, mergeFn) 重载
                // （三参版本收的是 Supplier<Map>），所以去重必须放在 collectMap 之前做，
                // 否则 collectMap 遇到重复键会抛 Duplicate key，
                // 整张分类映射被 onErrorResume 吞成空 Map（所有文章的分类名一起消失）。
                .distinct(projection -> projection.getPostId())
                .collectMap(
                    projection -> projection.getPostId(),
                    projection -> Tuples.of(projection.getCategoryId(), projection.getCategoryName())
                )
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    // 打完整堆栈：只记 e.getMessage() 会把 IllegalStateException /
                    // DataAccessException / TimeoutException 压成同一句话，
                    // 2026-10-28 的 `Request queue is full` 就是这样被藏了整整一轮排查。
                    log.error("批量获取文章分类信息失败: postCount={}", postIds.size(), e);
                    return Mono.just(Collections.emptyMap());
                });
    }
    {}
    /**
     * 批量获取用户信息
     *
     * @param userIds 用户ID列表
     * @return 用户ID到用户信息(名称, 头像)的映射
     */
    public Mono<Map<Long, Tuple2<String, String>>> batchGetUserInfo(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return userRepository.findByIdIn(userIds)
                .collectMap(BaseEntity::getId, user -> {
                    String userName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                    String avatarUrl = user.getAvatar();
                    if (avatarUrl == null || avatarUrl.isEmpty()) {
                        avatarUrl = DEFAULT_AVATAR;
                    }
                    return Tuples.of(userName, avatarUrl);
                })
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    // 同上：保留降级（列表照常渲染，只是缺作者名），但必须留下完整堆栈。
                    log.error("批量获取用户信息失败: userCount={}", userIds.size(), e);
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 批量获取文件URL
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到URL的映射
     */
    public Mono<Map<Long, String>> batchGetFileUrls(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return fileService.getBatchFilePermanentUrls(fileIds)
                .map(urlMap -> {
                    Map<Long, String> result = new HashMap<>(urlMap);
                    fileIds.forEach(fileId -> result.putIfAbsent(fileId, DEFAULT_COVER_IMAGE));
                    return result;
                })
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取文件URL失败", e);
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 获取单个用户信息
     *
     * @param userId 用户ID
     * @return 用户名称和头像的元组
     */
    public Mono<Tuple2<String, String>> getUserInfo(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String userName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                    String avatarUrl = user.getAvatar();
                    if (avatarUrl == null || avatarUrl.isEmpty()) {
                        avatarUrl = DEFAULT_AVATAR;
                    }
                    return Tuples.of(userName, avatarUrl);
                })
                .switchIfEmpty(Mono.just(Tuples.of(UNKNOWN_USER, DEFAULT_AVATAR)));
    }

    /**
     * 从文章列表中提取各种ID
     *
     * @param posts 文章列表
     * @return 包含文章ID、用户ID、封面图片ID的元组
     */
    public Tuple2<List<Long>, Tuple2<List<Long>, List<Long>>> extractIdsFromPosts(List<Posts> posts) {
        List<Long> postIds = posts.stream().map(Posts::getId).collect(Collectors.toList());
        List<Long> userIds = posts.stream().map(Posts::getUserId).distinct().collect(Collectors.toList());
        List<Long> coverImageIds = posts.stream()
                .filter(post -> post.getCoverImageId() != null)
                .map(Posts::getCoverImageId)
                .distinct()
                .collect(Collectors.toList());
        return Tuples.of(postIds, Tuples.of(userIds, coverImageIds));
    }

    /**
     * 并行获取文章关联数据
     *
     * @param postIds        文章ID列表
     * @param userIds        用户ID列表
     * @param coverImageIds 封面图片ID列表
     * @return 包含分类、标签、用户、统计、封面图片信息的元组
     */
    public Mono<reactor.util.function.Tuple5<Map<Long, Tuple2<Long, String>>, Map<Long, List<String>>, Map<Long, Tuple2<String, String>>, Map<Long, Tuple2<Integer, Integer>>, Map<Long, String>>> batchGetPostRelatedData(
            List<Long> postIds, List<Long> userIds, List<Long> coverImageIds) {

        Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap =
                batchGetArticleCategories(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, List<String>>> tagsMonoMap =
                batchGetArticleTags(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, Tuple2<String, String>>> usersMonoMap =
                batchGetUserInfo(userIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, Tuple2<Integer, Integer>>> statsMonoMap =
                batchGetArticleStats(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, String>> coverMonoMap =
                batchGetFileUrls(coverImageIds).subscribeOn(Schedulers.parallel());

        return Mono.zip(categoriesMonoMap, tagsMonoMap, usersMonoMap, statsMonoMap, coverMonoMap);
    }

    /**
     * 批量获取文章标签信息
     *
     * @param postIds 文章ID列表
     * @return 文章ID到标签列表的映射
     */
    public Mono<Map<Long, List<String>>> batchGetArticleTags(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.findPostsWithTags(postIds)
                .filter(projection -> projection.getPostId() != null
                        && projection.getTagId() != null
                        && projection.getTagName() != null)
                .groupBy(projection -> projection.getPostId())
                .flatMap(group -> group.map(projection -> projection.getTagName())
                        .collectList()
                        .map(tags -> Tuples.of(group.key(), tags)))
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取文章标签信息失败", e);
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 批量获取文章统计信息（评论数与点赞数）
     *
     * @param postIds 文章ID列表
     * @return 文章ID到统计信息的映射
     */
    public Mono<Map<Long, Tuple2<Integer, Integer>>> batchGetArticleStats(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.countCommentsByPostIds(postIds)
                .collectMap(
                    map -> ((Number) map.get("post_id")).longValue(),
                    map -> Tuples.of(((Number) map.get("comment_count")).intValue(), 0)
                )
                .map(statsMap -> {
                    Map<Long, Tuple2<Integer, Integer>> result = new HashMap<>(statsMap);
                    postIds.forEach(postId -> result.putIfAbsent(postId, Tuples.of(0, 0)));
                    return result;
                })
                .defaultIfEmpty(postIds.stream().collect(Collectors.toMap(id -> id, id -> Tuples.of(0, 0))))
                .onErrorResume(e -> {
                    log.error("批量获取文章统计信息失败", e);
                    return Mono.just(postIds.stream().collect(Collectors.toMap(id -> id, id -> Tuples.of(0, 0))));
                });
    }

    /**
     * 将文章实体转换为前台列表VO并填充额外信息
     *
     * @param post      文章实体
     * @param categoryMap 分类映射
     * @param tagMap      标签映射
     * @param userMap     用户映射
     * @param statsMap    统计映射
     * @param coverMap    封面图片映射
     * @return 填充完整的前台列表VO
     */
    public PostFrontListVO buildPostFrontListVO(Posts post,
                                                Map<Long, Tuple2<Long, String>> categoryMap,
                                                Map<Long, List<String>> tagMap,
                                                Map<Long, Tuple2<String, String>> userMap,
                                                Map<Long, Tuple2<Integer, Integer>> statsMap,
                                                Map<Long, String> coverMap) {
        Long pid = post.getId();
        Long userId = post.getUserId();

        Long categoryIdValue = null;
        String categoryName = null;
        if (categoryMap.containsKey(pid)) {
            Tuple2<Long, String> category = categoryMap.get(pid);
            categoryIdValue = category.getT1();
            categoryName = category.getT2();
        }

        List<String> tags = tagMap.getOrDefault(pid, List.of());

        String authorName = null;
        String authorAvatar = null;
        if (userMap.containsKey(userId)) {
            Tuple2<String, String> userInfo = userMap.get(userId);
            authorName = userInfo.getT1();
            authorAvatar = userInfo.getT2();
        }

        Integer commentCount = 0;
        Integer likeCount = 0;
        if (statsMap.containsKey(pid)) {
            Tuple2<Integer, Integer> stats = statsMap.get(pid);
            commentCount = stats.getT1();
            likeCount = stats.getT2();
        }

        String coverImageUrl = null;
        Long coverImageId = post.getCoverImageId();
        if (coverImageId != null && coverMap.containsKey(coverImageId)) {
            coverImageUrl = coverMap.get(coverImageId);
        }

        return postMapper.toFrontListVO(post, categoryIdValue, categoryName, tags,
                authorName, authorAvatar, coverImageUrl, commentCount, likeCount);
    }

    /**
     * 构建单个管理端列表VO
     */
    public PostAdminListVO buildPostAdminListVO(Posts post,
                                                Map<Long, Tuple2<Long, String>> categoryMap,
                                                Map<Long, Tuple2<String, String>> userMap) {
        Long postId = post.getId();
        Long userId = post.getUserId();

        Long categoryIdValue = null;
        String categoryName = null;
        if (categoryMap.containsKey(postId)) {
            Tuple2<Long, String> category = categoryMap.get(postId);
            categoryIdValue = category.getT1();
            categoryName = category.getT2();
        }

        String authorName = null;
        if (userMap.containsKey(userId)) {
            Tuple2<String, String> userInfo = userMap.get(userId);
            authorName = userInfo.getT1();
        }

        return postMapper.toAdminListVO(post, categoryIdValue, categoryName, authorName);
    }

    /**
     * 获取图片URL
     *
     * @param imageId 图片ID
     * @return 图片URL
     */
    public Mono<String> getImageUrl(Long imageId) {
        if (imageId == null) {
            return Mono.just(DEFAULT_COVER_IMAGE);
        }

        return batchGetFileUrls(Collections.singletonList(imageId))
                .map(urlMap -> urlMap.getOrDefault(imageId, DEFAULT_COVER_IMAGE));
    }
}
