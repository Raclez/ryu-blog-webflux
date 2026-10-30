package com.ryu.blog.service.impl;

import com.ryu.blog.dto.ArticlePublishRequest;
import com.ryu.blog.dto.ArticlePublishResult;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.service.ArticleService;
import com.ryu.blog.service.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 文章定时发布任务处理器
 *
 * <p>到点时把「待发布」的文章翻成「已发布」。
 *
 * <p><b>幂等</b>：任务可能被重复执行（Redis 丢数据后由 promoter 重建队列、
 * 任务超时重投等），所以每次执行都<b>重新读文章、以文章当前状态为准</b>：
 * 状态已经不是「待发布」就直接跳过，绝不把一篇已发布的文章重复发布一遍。
 *
 * <p><b>缓存</b>：这里注入的是 {@link ArticleService} 门面而不是内部的 crud 组件 ——
 * 只有走门面才会触发 {@code @Caching(evict = ...)}，
 * 否则文章发布了但前台列表/详情缓存仍是旧的，读者看不到。
 *
 * <p><b>{@code @Lazy} 是必须的，不是偷懒</b>：文章门面要向 task 模块提交定时发布任务，
 * 而 task 模块的处理器又要回调文章门面来发布文章 ——
 * 两个方向都是构造器注入，会形成 bean 循环：
 * <pre>
 * taskServiceImpl → taskHandlerRegistry → articlePublishTaskHandler
 *      → articleServiceImpl → articlePublishCoordinator → taskServiceImpl
 * </pre>
 * 打断「task 模块 → 文章门面」这一侧：门面本来就是被调方，
 * 而本处理器的 {@link #execute} 只在任务到期时才会跑（那会儿上下文早就起好了），
 * 所以延迟解析门面是安全的。
 *
 * @author ryu
 */
@Slf4j
@Component
public class ArticlePublishTaskHandler implements TaskHandler<ArticlePublishRequest, ArticlePublishResult> {

    private final ArticleService articleService;
    private final PostsRepository postsRepository;

    public ArticlePublishTaskHandler(@Lazy ArticleService articleService, PostsRepository postsRepository) {
        this.articleService = articleService;
        this.postsRepository = postsRepository;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.ARTICLE_PUBLISH;
    }

    /**
     * 声明请求类型：任务在 DB 里存的是 JSON，执行前由注册器按这个类型还原。
     */
    @Override
    public Class<ArticlePublishRequest> getRequestType() {
        return ArticlePublishRequest.class;
    }

    @Override
    public Mono<ArticlePublishResult> execute(ArticlePublishRequest request) {
        Long postId = request.getPostId();
        log.info("执行文章定时发布任务: 文章ID={}, 计划时间={}", postId, request.getPublishTime());

        return postsRepository.findById(postId)
                .flatMap(article -> {
                    // --- 自校验：以下几种情况都不是失败，只是无事可做 ---

                    if (Boolean.TRUE.equals(article.getIsDeleted())) {
                        log.warn("文章定时发布任务跳过：文章已删除: postId={}", postId);
                        return Mono.just(ArticlePublishResult.skipped(postId, article.getTitle(), "文章已删除"));
                    }

                    if (article.getStatus() == null || article.getStatus() != Posts.Status.PENDING) {
                        log.warn("文章定时发布任务跳过：文章状态已变更: postId={}, 当前状态={}",
                                postId, article.getStatus());
                        return Mono.just(ArticlePublishResult.skipped(postId, article.getTitle(), "文章状态已变更"));
                    }

                    // 提前到点（Redis 丢数据后重建队列、或时钟回拨）——再等等，不发布。
                    LocalDateTime publishTime = article.getPublishTime();
                    if (publishTime != null && publishTime.isAfter(LocalDateTime.now())) {
                        log.warn("文章定时发布任务跳过：还未到发布时间: postId={}, 计划时间={}", postId, publishTime);
                        return Mono.just(ArticlePublishResult.skipped(postId, article.getTitle(), "还未到发布时间"));
                    }

                    // --- 真正发布 ---
                    // 以「作者本人」的身份操作：归属校验（作者改自己的文章）天然通过。
                    OperatorContext authorCtx = new OperatorContext(article.getUserId(), false);

                    PostStatusDTO statusDTO = new PostStatusDTO();
                    statusDTO.setId(postId);
                    statusDTO.setStatus(Posts.Status.PUBLISHED);

                    return articleService.updateArticleStatus(statusDTO, authorCtx)
                            .map(published -> {
                                log.info("文章定时发布成功: postId={}, 标题={}", postId, published.getTitle());
                                return ArticlePublishResult.published(postId, published.getTitle());
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("文章定时发布任务跳过：文章不存在: postId={}", postId);
                    return Mono.just(ArticlePublishResult.skipped(postId, null, "文章不存在"));
                }))
                .doOnError(error -> log.error("文章定时发布失败: postId={}, 错误信息={}",
                        postId, error.getMessage(), error));
    }

    @Override
    public String successMessage(Object result) {
        if (!(result instanceof ArticlePublishResult publishResult)) {
            return null;
        }
        if (publishResult.isSkipped()) {
            return "文章已跳过定时发布：" + publishResult.getReason();
        }
        return "文章《" + publishResult.getTitle() + "》已定时发布";
    }

    @Override
    public String failureMessage(Throwable error) {
        return "文章定时发布失败：" + error.getMessage();
    }
}
