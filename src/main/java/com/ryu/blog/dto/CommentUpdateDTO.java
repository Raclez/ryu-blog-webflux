package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论更新数据传输对象
 *
 * <p><b>刻意不包含 {@code status}</b>：审核状态只能由
 * {@code PUT /comment/{id}/status}（需要 {@code comment:approve} 能力）来改。
 * 如果把 {@code status} 留在更新接口里，任何登录用户都能给自己的评论
 * 「改状态」把它洗成已通过 —— 这正是原先直接绑定 {@code Comment} 实体时
 * 存在的漏洞。
 *
 * <p>请求体 JSON 形状与原先绑定实体时<b>保持一致</b>（同样只用到 id 与 content），
 * 前端无需改动。
 *
 * @author ryu
 */
@Data
@Schema(description = "评论更新数据传输对象")
public class CommentUpdateDTO {

    @NotNull(message = "评论ID不能为空")
    @Positive(message = "评论ID必须为正数")
    @Schema(description = "评论ID", required = true)
    private Long id;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 5000, message = "评论内容不能超过5000个字符")
    @Schema(description = "评论正文", required = true)
    private String content;
}
