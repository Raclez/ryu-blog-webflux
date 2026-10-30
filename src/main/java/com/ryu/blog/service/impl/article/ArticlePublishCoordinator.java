package com.ryu.blog.service.impl.article;

import com.ryu.blog.dto.ArticlePublishRequest;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.service.TaskService;
import com.ryu.blog.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 文章发布编排
 *
 * <p>负责「文章状态」与「异步任务」之间的衔接：
 * <ul>
 *   <li>定时发布时提交一条 {@code ARTICLE_PUBLISH} 延迟任务；</li>
 *   <li>取消/改期时把该文章对应的待执行任务撤掉（<b>尽力而为</b>）；</li>
 *   <li>任务提交失败时把文章退回草稿，不留「永远不发布」的孤儿文章。</li>
 * </ul>
 *
 * <p><b>为什么取消可以只是「尽力而为」</b>：任务的执行以文章当前状态为准 ——
 * 任务到点时重新读文章，发现状态已经不是「待发布」就自己跳过。
 * 所以「取消」与「改期」的正确性不依赖任务是否真的被撤掉，
 * 撤掉只是让任务列表更整洁。
 *
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticlePublishCoordinator {

    private final TaskService taskService;
    private final AsyncTaskRepository taskRepository;
    private final ArticleCrudService crudService;

    /**
     * 文章写入之后处理发布方式
     *
     * <p>调用点在<b>文章事务之外</b>（门面层）：任务提交要写 {@code t_async_tasks} 并访问 Redis，
     * 不能和文章写入挤在同一个事务里。
     *
     * @param article      已写入的文章
     * @param mode         发布方式；{@code null} 表示「本次不涉及发布方式」
     * @param scheduleTime 计划发布时间（仅 SCHEDULED 使用）
     * @param userId       操作者（文章的作者）
     * @return 原文章
     */
    public Mono<Posts> afterWrite(Posts article, PublishMode mode, LocalDateTime scheduleTime, Long userId) {
        // mode 为 null = 本次请求没有涉及发布方式（更新场景下的普通改标题/改正文）。
        // 此时**什么都不做** —— 特别是不能撤掉已有的定时发布任务：
        // 改个标题就把发布任务撤了，文章会永远停在「待发布」。
        if (mode == null) {
            return Mono.just(article);
        }

        // 先撤掉该文章旧的待执行任务（改期场景），再按新模式重建。
        // 创建场景下没有旧任务，这一步是空转。
        return cancelPublishTask(article)
                .then(Mono.defer(() -> {
                    if (mode != PublishMode.SCHEDULED) {
                        return Mono.just(article);
                    }
                    return schedulePublish(article, userId)
                            .onErrorResume(error -> {
                                log.error("提交定时发布任务失败，文章退回草稿: articleId={}", article.getId(), error);
                                return crudService.revertToDraft(article.getId())
                                        .onErrorResume(revertError -> {
                                            log.error("文章退回草稿失败: articleId={}", article.getId(), revertError);
                                            return Mono.empty();
                                        })
                                        .then(Mono.error(error));
                            })
                            .thenReturn(article);
                }));
    }

    /**
     * 提交定时发布任务
     *
     * @param article 文章（publish_time 必须是计划时间）
     * @param userId  文章作者
     * @return 任务ID
     */
    public Mono<Long> schedulePublish(Posts article, Long userId) {
        LocalDateTime publishTime = article.getPublishTime();
        if (publishTime == null) {
            return Mono.error(new BusinessException("定时发布缺少计划时间"));
        }

        ArticlePublishRequest request = ArticlePublishRequest.builder()
                .postId(article.getId())
                .publishTime(publishTime)
                .build();

        // 优先级用 NORMAL：不占用高优先级的配额额度（定时发布量大且时效宽松）
        return taskService.submitTask(TaskType.ARTICLE_PUBLISH, request, userId, TaskPriority.NORMAL, publishTime);
    }

    /**
     * 撤销该文章对应的待执行发布任务（尽力而为）
     *
     * <p>失败不影响正确性：任务到点时会自校验文章状态并自行跳过。
     *
     * @param article 文章
     * @return 被取消的任务数
     */
    public Mono<Integer> cancelPublishTask(Posts article) {
        if (article.getId() == null || article.getUserId() == null) {
            return Mono.just(0);
        }

        return taskRepository.findActiveByUserIdAndType(article.getUserId(), TaskType.ARTICLE_PUBLISH, TaskStatus.PENDING)
                .filter(task -> matchesPost(task, article.getId()))
                .concatMap(task -> taskService.cancelTask(task.getId(), article.getUserId())
                        .filter(Boolean::booleanValue)
                        .doOnNext(cancelled -> log.info("已撤销定时发布任务: taskId={}, articleId={}",
                                task.getId(), article.getId()))
                        .thenReturn(1)
                        .onErrorResume(error -> {
                            log.warn("撤销定时发布任务失败: taskId={}, articleId={}", task.getId(), article.getId(), error);
                            return Mono.just(0);
                        }))
                .reduce(0, Integer::sum);
    }

    /**
     * 判断任务是不是这篇文章的发布任务
     *
     * <p>任务表里没有「文章ID」这一列（不新增字段），只能从请求体里读。
     */
    private boolean matchesPost(AsyncTask task, Long postId) {
        if (task.getRequestJson() == null) {
            return false;
        }
        ArticlePublishRequest request = JsonUtils.deserialize(task.getRequestJson(), ArticlePublishRequest.class);
        return request != null && postId.equals(request.getPostId());
    }
}
