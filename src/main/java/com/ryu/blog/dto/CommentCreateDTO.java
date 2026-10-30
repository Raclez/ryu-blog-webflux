package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论创建数据传输对象
 *
 * <p><b>为什么不用 {@code Comment} 实体直接接收请求体</b>：
 * 实体上有 {@code status}（审核状态）、{@code userId}、{@code isDeleted}、
 * {@code id}、{@code likeCount} 这些<b>不该由客户端决定</b>的字段。
 * 直接绑定实体等于把审核状态交给客户端 —— 请求体里写 {@code "status": 1}
 * 就能让评论<b>自己变成「已通过」</b>，绕过审核。
 * 所以这里只暴露「评论内容」本身，其余字段一律由服务端填。
 *
 * <p>请求体 JSON 形状与原先绑定实体时<b>保持一致</b>（同样的字段名），
 * 前端无需改动。
 *
 * @author ryu
 */
@Data
@Schema(description = "评论创建数据传输对象")
public class CommentCreateDTO {

    @NotNull(message = "文章ID不能为空")
    @Positive(message = "文章ID必须为正数")
    @Schema(description = "被评论的文章ID", required = true)
    private Long postId;

    @Positive(message = "父评论ID必须为正数")
    @Schema(description = "父评论ID，顶级评论不传")
    private Long parentCommentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 5000, message = "评论内容不能超过5000个字符")
    @Schema(description = "评论正文", required = true)
    private String content;
}
