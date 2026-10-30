package com.ryu.blog.service.impl.article;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostStickyDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.repository.PostCategoryRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.service.ArticleVersionService;
import com.ryu.blog.service.ContentService;
import com.ryu.blog.service.impl.OwnershipPolicy;
import com.ryu.blog.utils.MarkdownUtils;
import com.ryu.blog.vo.MarkdownExportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章写入服务
 *
 * <p>承载文章的创建、更新、状态变更、删除、Markdown 导入导出。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCrudService {

    private static final int BATCH_SIZE = 50;
    private static final int DEFAULT_EXCERPT_LENGTH = 200;
    private static final String UNNAMED_ARTICLE = "未命名文章";

    private final PostsRepository postsRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final ArticleVersionService articleVersionService;
    private final PostMapper postMapper;
    private final ContentService contentService;
    private final ObjectMapper objectMapper;
    private final ArticleRelationService relationService;

    /**
     * 创建文章
     */
    @Transactional
    public Mono<Posts> createArticle(PostCreateDTO articleCreateDTO, Long userId) {
        log.info("创建文章: 标题={}", articleCreateDTO.getTitle());

        Posts article = new Posts();
        article.setTitle(articleCreateDTO.getTitle());
        article.setContent(articleCreateDTO.getContent());
        article.setExcerpt(articleCreateDTO.getExcerpt());
        article.setCoverImageId(articleCreateDTO.getCoverImageId());
        // 别名（slug）：之前这里没set，DTO 里的 slug 一直被静默丢弃。
        // 空白串归一化成 null —— slug 上有唯一键，空串会撞键。
        article.setSlug(normalizeSlug(articleCreateDTO.getSlug()));
        // 发布方式决定初始状态与发布时间：
        // DRAFT→草稿、IMMEDIATE→已发布、SCHEDULED→待发布（计划时间落在 publish_time 上）。
        // 定时发布的任务在文章提交后由门面层补交（事务之外），失败则把文章退回草稿。
        applyPublishMode(article, articleCreateDTO.getPublishMode(), articleCreateDTO.getScheduleTime());
        article.setIsOriginal(articleCreateDTO.getIsOriginal() != null ? articleCreateDTO.getIsOriginal() : true);

        article.setSort(articleCreateDTO.getSort() != null ? articleCreateDTO.getSort() : 0);
        article.setAllowComment(articleCreateDTO.getAllowComment() != null ? articleCreateDTO.getAllowComment() : true);
        // 置顶：此前 DTO 里有这个字段、实体里却没有 —— 客户端传的置顶标记被静默丢弃。
        article.setIsSticky(articleCreateDTO.getIsSticky() != null ? articleCreateDTO.getIsSticky() : false);
        article.setSourceUrl(articleCreateDTO.getSourceUrl());
        article.setLicense(articleCreateDTO.getLicense());
        article.setViews(0);

        // 作者ID必须来自登录态。原先这里写的是 `userId != null ? userId : 1L` ——
        // 拿不到用户ID时把文章记在**用户1**名下，既是越权（冒名）也让人查不出真凶。
        // 现在拿不到就直接拒绝。
        if (userId == null) {
            return Mono.error(new BusinessException("未获取到当前用户，无法创建文章"));
        }
        article.setUserId(userId);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());

        // 【已移除】原先这里有一段「如果状态是已发布，设置发布时间」——
        // 但上面刚把状态固定成 DRAFT，这个分支**永远进不去**，是典型的死代码。
        // 发布时间由 updateArticleStatus 在真正发布时写入。

        article.setVisibility(articleCreateDTO.getVisibility() != null ? articleCreateDTO.getVisibility() : Posts.Visibility.PUBLIC);
        // 访问密码存 BCrypt 哈希，不存明文 —— 口径与 t_sys_user.password 一致。
        // 理由与迁移口径见 ArticleAccessPasswordPolicy。
        article.setPassword(ArticleAccessPasswordPolicy.encode(articleCreateDTO.getPassword()));
        article.setIsDeleted(false);

        // 处理SEO元数据
        String seoMeta = processSeoMetadata(articleCreateDTO.getSeoTitle(), articleCreateDTO.getSeoDescription());
        article.setSeoMeta(seoMeta);

        // 处理摘要
        Mono<String> excerptMono = StringUtils.hasText(articleCreateDTO.getExcerpt())
                ? Mono.just(articleCreateDTO.getExcerpt())
                : contentService.generateExcerpt(articleCreateDTO.getContent(), DEFAULT_EXCERPT_LENGTH);

        // 保存文章及关联数据
        return excerptMono
                .doOnNext(article::setExcerpt)
                .then(checkSlugAvailable(articleCreateDTO.getSlug(), null))
                .then(postsRepository.save(article))
                .flatMap(savedArticle -> {
                    // 依次执行所有写操作。
                    //
                    // ⚠️ 这里<b>不能</b>用 Mono.when 并发执行：本方法带 @Transactional，
                    // 事务会把整条链绑到同一条 R2DBC 连接上，而 r2dbc-mysql 1.4.0 的
                    // RequestQueue 是 SPSC 队列且 submit() 不持锁 —— 并发提交会误判队满，
                    // 抛 `IllegalStateException: Request queue is full`。
                    // 分类关联 / 标签关联 / 版本创建都是写库，必须串行。
                    Mono<Void> writeOps = Mono.empty();

                    // 保存文章分类关联
                    if (articleCreateDTO.getCategoryId() != null) {
                        log.debug("关联文章分类: 文章ID: {}, 分类ID: {}", savedArticle.getId(), articleCreateDTO.getCategoryId());
                        writeOps = writeOps.then(
                                relationService.addArticleCategory(savedArticle.getId(), articleCreateDTO.getCategoryId()).then());
                    }

                    // 保存文章标签关联
                    if (articleCreateDTO.getTagsIds() != null && !articleCreateDTO.getTagsIds().isEmpty()) {
                        log.debug("关联文章标签: 文章ID: {}, 标签IDs: {}", savedArticle.getId(), articleCreateDTO.getTagsIds());
                        writeOps = writeOps.then(
                                relationService.addArticleTags(savedArticle.getId(), articleCreateDTO.getTagsIds()));
                    }

                    // 创建文章版本
                    log.debug("创建文章初始版本: 文章ID: {}", savedArticle.getId());
                    writeOps = writeOps.then(
                            articleVersionService.createVersion(savedArticle, "初始版本").then());

                    return writeOps.thenReturn(savedArticle);
                })
                .doOnSuccess(savedArticle -> log.info("文章创建成功: ID={}, 标题={}", savedArticle.getId(), savedArticle.getTitle()))
                .doOnError(e -> log.error("文章创建失败: 标题={}, 错误信息={}", articleCreateDTO.getTitle(), e.getMessage(), e));
    }

    /**
     * 更新文章
     *
     * <p><b>归属校验</b>：只能改自己的文章（拥有「管理他人」能力者除外）。
     * 规则见 {@code OwnershipPolicy#requireCanActOn}，不满足时抛 403。
     */
    @Transactional
    public Mono<Posts> updateArticle(PostUpdateDTO articleUpdateDTO, OperatorContext ctx) {
        log.info("根据DTO更新文章: ID={}", articleUpdateDTO.getId());

        return postsRepository.findById(articleUpdateDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(existingArticle -> {
                    // 归属校验：本人 or 管理员。PermissionDeniedException 被
                    // GlobalExceptionHandler 映射成 HTTP 403。
                    OwnershipPolicy.requireCanActOn(ctx, existingArticle.getUserId());

                    // 保存旧内容作为比较
                    String oldContent = existingArticle.getContent();
                    // 旧的访问密码：详情接口**刻意不返回明文密码**（见 PostDetailVO），
                    // 所以编辑器回显出来的一定是空的 ——
                    // 若照单全收地把这个空串写回去，密码保护的文章一编辑就失去密码。
                    String oldPassword = existingArticle.getPassword();

                    // 使用MapStruct更新实体
                    postMapper.updateEntityFromDTO(articleUpdateDTO, existingArticle);

                    // 访问密码只在**真的给了新值**时才覆盖。
                    // 空串与 null 都视为「不改」——
                    // MapStruct 的 nullValuePropertyMappingStrategy=IGNORE 只挡 null，挡不住空串。
                    //
                    // ⚠️ 这里用的是 **mapper 之前**取到的 oldPassword，不是 mapper 之后的值：
                    // 前端「没填访问密码」时提交的是空串，mapper 会把空串写进实体 ——
                    // 若用 mapper 之后的值，「不改密码」就变成了「清空密码」。
                    if (StringUtils.hasText(articleUpdateDTO.getPassword())) {
                        // 新口令 → 存哈希，不存明文
                        existingArticle.setPassword(ArticleAccessPasswordPolicy.encode(articleUpdateDTO.getPassword()));
                    } else {
                        // 不改密码；但历史遗留的明文顺手洗成哈希（口令本身不变）
                        existingArticle.setPassword(ArticleAccessPasswordPolicy.rehashIfPlaintext(oldPassword));
                    }

                    // 别名归一化：空白串存成 null（slug 上有唯一键，空串会撞键）。
                    // MapStruct 只会跳过 null，不会跳过空串 —— 编辑器里把别名清空
                    // 提交上来时，实体里必须真的是 null，而不是 ""。
                    existingArticle.setSlug(normalizeSlug(existingArticle.getSlug()));

                    // 发布方式（可选）：不传表示不改状态。
                    // 传了才处理 —— 否则一次普通的「改标题」会把已发布的文章打回草稿。
                    if (articleUpdateDTO.getPublishMode() != null) {
                        applyPublishMode(existingArticle,
                                articleUpdateDTO.getPublishMode(),
                                articleUpdateDTO.getScheduleTime());
                    }

                    // 设置更新时间
                    existingArticle.setUpdateTime(LocalDateTime.now());

                    // 更新SEO信息
                    String seoMeta = processSeoMetadata(articleUpdateDTO.getSeoTitle(), articleUpdateDTO.getSeoDescription());
                    if (seoMeta != null) {
                        existingArticle.setSeoMeta(seoMeta);
                    }

                    // 保存更新后的文章
                    return checkSlugAvailable(existingArticle.getSlug(), existingArticle.getId())
                            .then(postsRepository.save(existingArticle))
                            .flatMap(updatedArticle -> {
                                // 依次执行所有写操作。
                                //
                                // ⚠️ 这里<b>不能</b>用 Mono.when 并发执行：本方法带 @Transactional，
                                // 事务会把整条链绑到同一条 R2DBC 连接上，而 r2dbc-mysql 1.4.0 的
                                // RequestQueue 是 SPSC 队列且 submit() 不持锁 —— 并发提交会误判队满，
                                // 抛 `IllegalStateException: Request queue is full`。
                                Mono<Void> writeOps = Mono.empty();

                                // 处理分类关联
                                if (articleUpdateDTO.getCategoryId() != null) {
                                    writeOps = writeOps.then(
                                            relationService.removeAllArticleCategories(updatedArticle.getId())
                                                    .then(relationService.addArticleCategory(updatedArticle.getId(), articleUpdateDTO.getCategoryId()).then())
                                    );
                                }

                                // 处理标签关联
                                if (articleUpdateDTO.getTagsIds() != null) {
                                    writeOps = writeOps.then(
                                            relationService.addArticleTags(updatedArticle.getId(), articleUpdateDTO.getTagsIds())
                                    );
                                }

                                // 如果内容发生变更，创建新版本
                                if (!oldContent.equals(updatedArticle.getContent())) {
                                    log.debug("创建文章新版本: 文章ID: {}", updatedArticle.getId());
                                    writeOps = writeOps.then(
                                            articleVersionService.createVersion(updatedArticle, "内容更新").then());
                                }

                                return writeOps.thenReturn(updatedArticle);
                            });
                })
                .doOnSuccess(updatedArticle -> log.info("文章更新成功: ID={}, 标题={}", updatedArticle.getId(), updatedArticle.getTitle()))
                .doOnError(e -> log.error("文章更新失败: ID={}, 错误信息={}", articleUpdateDTO.getId(), e.getMessage(), e));
    }

    /**
     * 更新文章状态
     *
     * <p><b>归属校验</b>：只能改自己的文章状态（拥有「管理他人」能力者除外）。
     */
    @Transactional
    public Mono<Posts> updateArticleStatus(PostStatusDTO statusDTO, OperatorContext ctx) {
        log.info("更新文章状态: ID={}, 状态={}", statusDTO.getId(), statusDTO.getStatus());

        return postsRepository.findById(statusDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(existingArticle -> {
                    OwnershipPolicy.requireCanActOn(ctx, existingArticle.getUserId());

                    // 使用MapStruct更新实体状态
                    postMapper.updateStatusFromDTO(statusDTO, existingArticle);

                    // 设置更新时间
                    existingArticle.setUpdateTime(LocalDateTime.now());

                    // 如果从非发布状态变为已发布状态，设置发布时间
                    //
                    // 两种情况要写「现在」：
                    //   publish_time 为空 —— 从来没发布过（草稿 / 待发布没有计划时间）
                    //   publish_time 在未来 —— 之前排的是定时发布，现在提前发布，
                    //     计划时间已经作废；留着它的话前台会按这个未来的时间排序，
                    //     一篇刚发布的文章会挂在「明天」。
                    // 到点自动发布的那条路不受影响：那时 publish_time 已经是过去的时间。
                    LocalDateTime publishTime = existingArticle.getPublishTime();
                    if (existingArticle.getStatus() == Posts.Status.PUBLISHED
                            && (publishTime == null || publishTime.isAfter(LocalDateTime.now()))) {
                        existingArticle.setPublishTime(LocalDateTime.now());
                    }

                    return postsRepository.save(existingArticle);
                })
                .doOnSuccess(updatedArticle -> log.info("文章状态更新成功: ID={}, 状态={}", updatedArticle.getId(), updatedArticle.getStatus()))
                .doOnError(e -> log.error("文章状态更新失败: ID={}, 错误信息={}", statusDTO.getId(), e.getMessage(), e));
    }

    /**
     * 删除文章（软删除）
     *
     * <p><b>归属校验</b>：只能删自己的文章（拥有「管理他人」能力者除外）。
     */
    @Transactional
    public Mono<Void> deleteArticle(Long id, OperatorContext ctx) {
        log.info("删除文章: ID={}", id);

        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    OwnershipPolicy.requireCanActOn(ctx, article.getUserId());

                    // 检查文章是否已删除
                    if (Boolean.TRUE.equals(article.getIsDeleted())) {
                        return Mono.error(BusinessException.postAlreadyDeleted());
                    }

                    article.setIsDeleted(true);
                    return postsRepository.save(article)
                            .then(relationService.removeAllArticleCategories(id))
                            .then();
                })
                .doOnSuccess(v -> log.info("文章删除成功: ID={}", id))
                .doOnError(e -> {
                    if (e instanceof BusinessException) {
                        log.warn("文章删除失败: ID={}", id, e);
                    } else {
                        log.error("文章删除失败: ID={}, 错误信息={}", id, e.getMessage(), e);
                    }
                });
    }

    /**
     * 批量删除文章
     *
     * <p><b>归属校验</b>：批次里的<b>每一篇</b>都必须归操作者所有（拥有「管理他人」能力者除外）。
     * 只要有一篇不是本人的，整批拒绝 —— 不做「删掉能删的、跳过不能删的」，
     * 否则调用方无法从结果里知道自己误伤了谁。
     */
    @Transactional
    public Mono<Void> batchDeleteArticles(List<String> ids, OperatorContext ctx) {
        log.info("批量删除文章: IDs={}", ids);

        if (ids == null || ids.isEmpty()) {
            log.warn("批量删除文章: 传入的ID列表为空");
            return Mono.empty();
        }

        // 将字符串ID转换为Long类型
        List<Long> longIds;
        try {
            longIds = ids.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            log.error("批量删除文章: ID格式错误: {}", e.getMessage(), e);
            return Mono.error(new BusinessException("ID格式错误: " + e.getMessage()));
        }

        log.debug("开始批量删除文章: 数量={}", longIds.size());

        // 分批处理，避免一次处理过多数据
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < longIds.size(); i += BATCH_SIZE) {
            batches.add(longIds.subList(i, Math.min(i + BATCH_SIZE, longIds.size())));
        }

        // 逐批处理 - 使用自定义Repository批量删除方法
        // ⚠️ 三处都必须用 concatMap（串行），不能用 flatMap（并发）：
        // 本方法带 @Transactional（门面 ArticleServiceImpl#batchDeleteArticles 也有），
        // 整条链绑定在同一条 R2DBC 连接上。r2dbc-mysql 1.4.0 每条连接只有一个
        // SPSC 的 RequestQueue，且 submit() 不加内部锁 —— 并发提交会读到尚未推进的
        // producerIndex 而误判队满，抛 `IllegalStateException: Request queue is full`，
        // 批量删除会静默少删。
        // 归属校验也放在同一条链里 —— 它是一次读，同样不能并发提交。
        return requireAllOwnedBy(ctx, longIds)
                .thenMany(Flux.fromIterable(batches))
                .concatMap(batchIds ->
                    // 使用批量软删除方法
                    postsRepository.batchSoftDelete(batchIds)
                        .flatMap(deletedCount -> {
                            log.debug("批量删除结果: 删除数量={}", deletedCount);
                            // 批量删除文章分类关联
                            return Flux.fromIterable(batchIds)
                                .concatMap(postCategoryRepository::deleteByPostId)
                                .then(Mono.just(deletedCount));
                        })
                )
                .then()
                .doOnSuccess(v -> log.info("批量删除文章完成: 请求删除数量={}", longIds.size()))
                .doOnError(e -> {
                    if (e instanceof BusinessException) {
                        log.warn("批量删除文章失败", e);
                    } else {
                        log.error("批量删除文章失败: 错误信息={}", e.getMessage(), e);
                    }
                });
    }

    /**
     * 按发布方式设置文章状态与发布时间
     *
     * <p>规则本体在 {@link ArticlePublishPolicy}（纯函数，可直接断言）。
     *
     * @param article      文章实体
     * @param mode         发布方式（null 视为草稿）
     * @param scheduleTime 计划发布时间（仅 SCHEDULED 使用）
     */
    private void applyPublishMode(Posts article, PublishMode mode, LocalDateTime scheduleTime) {
        ArticlePublishPolicy.apply(article, mode, scheduleTime);
    }

    /**
     * 取消定时发布：文章退回草稿
     *
     * <p>publish_time 必须一起清掉：留着的话，之后再手动发布时
     * {@link #updateArticleStatus} 会因为「publish_time 非空」而跳过写入，
     * 文章的发布时间会一直停在那个早已作废的计划时间上。
     *
     * <p><b>归属校验</b>：只能取消自己的文章（拥有「管理他人」能力者除外）。
     *
     * @param postId 文章ID
     * @param ctx    操作者上下文
     * @return 退回草稿后的文章
     */
    @Transactional
    public Mono<Posts> cancelScheduledPublish(Long postId, OperatorContext ctx) {
        log.info("取消定时发布: ID={}", postId);

        return postsRepository.findById(postId)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    OwnershipPolicy.requireCanActOn(ctx, article.getUserId());

                    if (article.getStatus() != Posts.Status.PENDING) {
                        return Mono.error(new BusinessException("该文章当前不是定时发布状态，无法取消"));
                    }

                    article.setStatus(Posts.Status.DRAFT);
                    article.setPublishTime(null);
                    article.setUpdateTime(LocalDateTime.now());

                    return postsRepository.save(article);
                })
                .doOnSuccess(article -> log.info("已取消定时发布: ID={}, 标题={}", postId, article.getTitle()))
                .doOnError(e -> log.error("取消定时发布失败: ID={}, 错误信息={}", postId, e.getMessage(), e));
    }

    /**
     * 设置/取消文章置顶
     *
     * <p>置顶是<b>独立于正文</b>的开关：列表页上要能一键切换，
     * 不该要求作者把整篇文章重新提交一遍。
     *
     * <p><b>归属校验</b>：只能置顶自己的文章（拥有「管理他人」能力者除外）。
     *
     * @param stickyDTO 文章ID与目标置顶状态
     * @param ctx        操作者上下文
     * @return 设置后的文章
     */
    @Transactional
    public Mono<Posts> updateSticky(PostStickyDTO stickyDTO, OperatorContext ctx) {
        log.info("更新文章置顶状态: ID={}, 置顶={}", stickyDTO.getId(), stickyDTO.getIsSticky());

        return postsRepository.findById(stickyDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    OwnershipPolicy.requireCanActOn(ctx, article.getUserId());

                    article.setIsSticky(Boolean.TRUE.equals(stickyDTO.getIsSticky()));
                    article.setUpdateTime(LocalDateTime.now());

                    return postsRepository.save(article);
                })
                .doOnSuccess(article -> log.info("文章置顶状态更新成功: ID={}, 置顶={}",
                        article.getId(), article.getIsSticky()))
                .doOnError(e -> log.error("文章置顶状态更新失败: ID={}, 错误信息={}",
                        stickyDTO.getId(), e.getMessage(), e));
    }

    /**
     * 把待发布的文章退回草稿（系统动作，不做归属校验）
     *
     * <p>用于「定时发布任务提交失败」的回滚：文章已经是待发布状态，
     * 但没有任务会把它发布出去 —— 与其留一篇永远不发布的文章，不如退回草稿。
     *
     * @param postId 文章ID
     * @return 完成信号
     */
    @Transactional
    public Mono<Void> revertToDraft(Long postId) {
        return postsRepository.findById(postId)
                .filter(article -> article.getStatus() != null
                        && article.getStatus() == Posts.Status.PENDING)
                .flatMap(article -> {
                    log.warn("文章 {} 的定时发布任务提交失败，退回草稿", postId);
                    article.setStatus(Posts.Status.DRAFT);
                    article.setPublishTime(null);
                    article.setUpdateTime(LocalDateTime.now());
                    return postsRepository.save(article);
                })
                .then();
    }

    /**
     * 校验批次里的每一篇文章都归操作者所有
     *
     * <p>只要有一篇不属于操作者（且操作者没有「管理他人」的能力），
     * 整批拒绝，抛 403。
     *
     * @param ctx     操作者上下文
     * @param postIds 文章ID列表
     * @return 完成信号
     */
    private Mono<Void> requireAllOwnedBy(OperatorContext ctx, List<Long> postIds) {
        if (ctx == null || !ctx.isAuthenticated()) {
            // 未登录：连「自己的文章」都谈不上，直接拒绝
            return Mono.error(PermissionDeniedException.accessDenied("无权操作他人的数据"));
        }
        return postsRepository.findByIdIn(postIds)
                .collectList()
                .flatMap(articles -> {
                    // 库里查不到的（已软删或不存在）不参与归属判定 ——
                    // 后续批量删除本来就只影响未删除的。
                    for (Posts article : articles) {
                        if (!OwnershipPolicy.canActOn(ctx, article.getUserId())) {
                            log.warn("批量删除被拒：文章 {} 不属于当前操作者", article.getId());
                            return Mono.error(PermissionDeniedException.accessDenied("无权操作他人的数据"));
                        }
                    }
                    return Mono.empty();
                });
    }

    /**
     * 导入Markdown文件为文章
     */
    @Transactional
    public Mono<Void> importMarkdownArticle(FilePart file, Long categoryId, Long userId) {
        log.info("导入Markdown文件: 文件名={}, 分类ID={}, 用户ID={}", file.filename(), categoryId, userId);

        // 使用非阻塞方式读取文件内容
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(markdownContent -> {
                    // 使用MarkdownUtils提取标题和摘要
                    String title = MarkdownUtils.extractTitle(markdownContent);
                    String excerpt = MarkdownUtils.extractExcerpt(markdownContent, 200);

                    // 创建文章对象
                    PostCreateDTO postCreateDTO = new PostCreateDTO();
                    postCreateDTO.setTitle(title);
                    postCreateDTO.setContent(markdownContent);
                    postCreateDTO.setExcerpt(excerpt);
                    postCreateDTO.setIsOriginal(true);
                    postCreateDTO.setAllowComment(true);
                    postCreateDTO.setVisibility(Posts.Visibility.PUBLIC);

                    // 创建文章
                    return createArticle(postCreateDTO, userId)
                            // 如果指定了分类，添加文章分类关联
                            .flatMap(article -> {
                                if (categoryId != null) {
                                    return relationService.addArticleCategory(article.getId(), categoryId)
                                            .then(Mono.just(article));
                                }
                                return Mono.just(article);
                            });
                })
                .then();
    }

    /**
     * 导出文章为Markdown
     *
     * <p><b>归属校验</b>：导出等于拿走全文，只允许作者本人（拥有「管理他人」能力者除外）。
     */
    @Transactional(readOnly = true)
    public Mono<MarkdownExportVO> exportArticleToMarkdown(Long id, OperatorContext ctx) {
        log.info("导出文章为Markdown: ID={}", id);

        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文章不存在")))
                .map(article -> {
                    OwnershipPolicy.requireCanActOn(ctx, article.getUserId());
                    // 1. 使用文章标题作为文件名
                    String title = StringUtils.hasText(article.getTitle()) ? article.getTitle() : UNNAMED_ARTICLE;

                    // 替换文件名中的非法字符，但保留中文
                    // 只替换文件系统不允许的特殊字符，不替换中文
                    String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");

                    // 添加.md后缀
                    String filename = safeTitle + ".md";

                    // 2. 直接使用博客内容作为Markdown内容
                    String content = article.getContent();
                    if (content == null) {
                        content = "";
                    }

                    return MarkdownExportVO.builder()
                            .content(content)
                            .filename(filename)
                            .build();
                })
                .doOnSuccess(result -> log.info("导出文章为Markdown成功: ID={}, 文件名={}", id, result.getFilename()));
    }

    /**
     * 处理SEO元数据并序列化为JSON字符串
     *
     * @param seoTitle       SEO标题
     * @param seoDescription SEO描述
     * @return 序列化后的JSON字符串，失败时返回null
     */
    /**
     * 校验文章别名（slug）是否已被占用
     *
     * <p>别名是文章的 URL 标识，必须唯一。此前 DTO 声明了 slug、也预留了
     * {@code BusinessException.postSlugExists()}，但从来没有真正校验过，
     * 谁都能占用同一个别名（而且别名本身都没被保存）。
     *
     * @param slug       别名（为空则跳过校验）
     * @param excludeId 排除的文章ID（创建时传 null）
     * @return 完成信号
     */
    private Mono<Void> checkSlugAvailable(String slug, Long excludeId) {
        String normalized = normalizeSlug(slug);
        if (normalized == null) {
            return Mono.empty();
        }
        return postsRepository.countSlugUsage(normalized, excludeId)
                .flatMap(count -> {
                    if (count != null && count > 0) {
                        log.warn("文章别名已被占用: slug={}, excludeId={}", normalized, excludeId);
                        return Mono.error(BusinessException.postSlugExists());
                    }
                    return Mono.empty();
                });
    }

    /**
     * 归一化别名（slug）
     *
     * <p>空白串归成 {@code null}。原因有二：
     * <ol>
     *   <li>{@code t_posts.slug} 上有唯一键 {@code uk_posts_slug} —— 两篇文章都存空串会撞唯一键，
     *       而 MySQL 的唯一键不区分 NULL（多个 NULL 可以共存），所以「没有别名」必须存成 NULL；</li>
     *   <li>校验时空白串本来就要跳过（{@code StringUtils.hasText}），
     *       归一化后校验与落库用的是同一份值，不会出现「校验过了但落库是另一个值」。</li>
     * </ol>
     */
    private String normalizeSlug(String slug) {
        return StringUtils.hasText(slug) ? slug.trim() : null;
    }

    private String processSeoMetadata(String seoTitle, String seoDescription) {
        if (seoTitle == null && seoDescription == null) {
            return null;
        }

        Map<String, String> seoMeta = new HashMap<>();
        if (seoTitle != null) {
            seoMeta.put("seoTitle", seoTitle);
        }
        if (seoDescription != null) {
            seoMeta.put("seoDescription", seoDescription);
        }

        try {
            return objectMapper.writeValueAsString(seoMeta);
        } catch (JsonProcessingException e) {
            log.error("SEO元数据序列化失败", e);
            return null;
        }
    }
}
