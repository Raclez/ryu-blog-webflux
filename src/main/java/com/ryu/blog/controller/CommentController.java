package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.CommentCreateDTO;
import com.ryu.blog.dto.CommentUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.Comment;
import com.ryu.blog.service.CommentService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 评论控制器
 *
 * <p><b>鉴权模型</b>：
 * <ul>
 *   <li><b>能力校验</b> —— {@code @SaCheckPermission}：
 *       看别人的评论要 {@code comment:view}，审核评论要 {@code comment:approve}；</li>
 *   <li><b>归属校验</b> —— Service 层 {@code OwnershipPolicy}：
 *       改/删评论只有作者本人（或管理员）可以。</li>
 * </ul>
 *
 * <p><b>请求体不再直接绑定 {@code Comment} 实体</b> —— 实体上有 {@code status}、
 * {@code userId}、{@code isDeleted} 等字段，绑定实体等于把审核状态交给客户端
 * （请求体里写 {@code "status": 1} 就能让评论自己变成「已通过」）。
 * 现在改用 {@link CommentCreateDTO} / {@link CommentUpdateDTO}，
 * JSON 字段名保持不变，前端无需改动。
 *
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
@Tag(name = "评论管理", description = "评论相关接口")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "创建评论", description = "创建新评论，新评论一律进入待审核状态")
    @PostMapping
    public Mono<Result<Comment>> createComment(@RequestBody @Validated CommentCreateDTO dto) {
        OperatorContext ctx = OperatorContext.current();
        Comment comment = new Comment();
        comment.setPostId(dto.getPostId());
        comment.setParentCommentId(dto.getParentCommentId());
        comment.setContent(dto.getContent());
        comment.setUserId(ctx.userId());
        log.info("创建评论: userId={}, articleId={}", ctx.userId(), dto.getPostId());

        return commentService.createComment(comment, ctx)
                .map(Result::success)
                .doOnSuccess(result -> log.info("创建评论成功: commentId={}", result.getData().getId()));
    }

    @Operation(summary = "更新评论", description = "更新已有评论（仅作者本人或管理员；不含审核状态）")
    @PutMapping("/{id}")
    public Mono<Result<Comment>> updateComment(
            @PathVariable Long id,
            @RequestBody @Validated CommentUpdateDTO dto) {
        dto.setId(id);
        log.info("更新评论: id={}", id);

        return commentService.updateComment(dto, OperatorContext.current())
                .map(Result::success)
                .doOnSuccess(result -> log.info("更新评论成功: id={}", id));
    }

    @Operation(summary = "获取评论详情", description = "根据ID获取评论详情")
    @GetMapping("/{id}")
    public Mono<Result<Comment>> getCommentById(@PathVariable Long id) {
        log.info("获取评论详情: id={}", id);

        return commentService.getCommentById(id)
                .map(Result::success)
                .doOnSuccess(result -> log.debug("获取评论详情成功: id={}", id));
    }

    @Operation(summary = "删除评论", description = "根据ID删除评论（仅作者本人或管理员）")
    @DeleteMapping("/{id}")
    public Mono<Result<String>> deleteComment(@PathVariable Long id) {
        log.info("删除评论: id={}", id);

        return commentService.deleteComment(id, OperatorContext.current())
                .thenReturn(Result.success("删除成功"))
                .doOnSuccess(result -> log.info("删除评论成功: id={}", id));
    }

    @Operation(summary = "获取文章评论列表", description = "分页获取文章评论列表（文章须公开可见）")
    @GetMapping("/article/{articleId}")
    public Mono<Result<Map<String, Object>>> getCommentsByArticleId(
            @PathVariable Long articleId,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.info("获取文章评论列表: articleId={}, page={}, size={}", articleId, page, size);

        OperatorContext ctx = OperatorContext.current();
        // 先校验文章可见 —— 这个接口是匿名的，不校验就会把草稿文章的评论读出去
        return commentService.requireArticleVisible(articleId, ctx)
                .then(commentService.countCommentsByArticleId(articleId))
                .flatMap(total -> commentService.getCommentsByArticleId(articleId, page, size)
                        .collectList()
                        .map(comments -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("total", total);
                            result.put("comments", comments);
                            log.debug("获取文章评论列表成功: articleId={}, 总数={}", articleId, total);
                            return Result.success(result);
                        }));
    }

    @Operation(summary = "获取用户评论列表", description = "分页获取指定用户的评论列表（需要 comment:view 能力）")
    @GetMapping("/user/{userId}")
    @SaCheckPermission(PermissionConstants.COMMENT_VIEW)
    public Mono<Result<Map<String, Object>>> getCommentsByUserId(
            @PathVariable Long userId,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.info("获取用户评论列表: userId={}, page={}, size={}", userId, page, size);

        return commentService.countCommentsByUserId(userId)
                .flatMap(total -> commentService.getCommentsByUserId(userId, page, size)
                        .collectList()
                        .map(comments -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("total", total);
                            result.put("comments", comments);
                            log.debug("获取用户评论列表成功: userId={}, 总数={}", userId, total);
                            return Result.success(result);
                        }));
    }

    @Operation(summary = "获取我的评论列表", description = "分页获取当前登录用户的评论列表")
    @GetMapping("/my")
    public Mono<Result<Map<String, Object>>> getMyComments(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        Long userId = OperatorContext.current().userId();
        log.info("获取我的评论列表: userId={}", userId);
        return getCommentsByUserId(userId, page, size);
    }

    @Operation(summary = "更新评论状态", description = "更新评论状态（需要 comment:approve 能力）")
    @PutMapping("/{id}/status")
    @SaCheckPermission(PermissionConstants.COMMENT_APPROVE)
    public Mono<Result<String>> updateCommentStatus(
            @PathVariable Long id,
            @Parameter(description = "状态：0-待审核，1-已通过，2-已拒绝") @RequestParam Integer status) {
        log.info("更新评论状态: id={}, status={}", id, status);

        return commentService.updateCommentStatus(id, status)
                .map(result -> Result.success("更新成功"))
                .doOnSuccess(r -> log.info("更新评论状态成功: id={}, status={}", id, status));
    }

    @Operation(summary = "获取待审核评论列表", description = "分页获取待审核评论列表（需要 comment:approve 能力）")
    @GetMapping("/pending")
    @SaCheckPermission(PermissionConstants.COMMENT_APPROVE)
    public Mono<Result<Map<String, Object>>> getPendingComments(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.info("获取待审核评论列表: page={}, size={}", page, size);

        return commentService.countPendingComments()
                .flatMap(total -> commentService.getPendingComments(page, size)
                        .collectList()
                        .map(comments -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("total", total);
                            result.put("comments", comments);
                            log.debug("获取待审核评论列表成功: 总数={}", total);
                            return Result.success(result);
                        }));
    }
}
