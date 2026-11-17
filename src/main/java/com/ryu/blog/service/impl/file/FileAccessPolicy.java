package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.PermissionDeniedException;

import java.util.Objects;

/**
 * 文件访问规则
 *
 * <p>与 {@code ArticleVisibilityPolicy} / {@code OwnershipPolicy} 同构：
 * <b>纯函数、无 Spring 依赖、可直接单测</b>。
 *
 * <p><b>只判「谁写得动」，不判「谁读得到」</b>：文件的读权限曾经按
 * {@code access_type}（公开 / 私有）区分。但对象存储的桶是<b>匿名可读</b>的
 * （实测直接 GET 桶根路径就会返回完整对象列表），
 * 那个字段挡不住直链，只制造「私有文件是安全的」这种错觉 —— 已整体下线。
 * 现在文件一律公开可读；真要保密只能靠私有桶 / 私有前缀，一个 boolean 做不到。
 *
 * <p><b>为什么没有直接复用 {@code OwnershipPolicy}</b>：
 * {@code OwnershipPolicy.canActOn(ctx, null)} 返回 {@code true}
 * —— 它的语义是「这个资源没有归属概念（角色、菜单）」。
 * 但对文件而言 {@code creator_id IS NULL} 不是「没有归属」，
 * 而是<b>历史数据没填归属</b>（早期版本把上传者写死成 {@code 1L}，
 * 再早的记录干脆是 NULL）。把「不知道主人是谁」当成「谁都能改」，
 * 等于给了一条无主文件的默认写权限。所以这里单独处理：
 * <ul>
 *   <li>{@code creatorId == null} → <b>只有管理员</b>能改；</li>
 *   <li>{@code creatorId != null} → 本人或管理员。</li>
 * </ul>
 *
 * @author ryu
 * @since 1.0.0
 */
public final class FileAccessPolicy {

    private FileAccessPolicy() {
    }

    /**
     * 当前操作者能否修改该文件
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @return 允许返回 true
     */
    public static boolean canModify(File file, OperatorContext ctx) {
        return isOwnerOrAdmin(file, ctx);
    }

    /**
     * 当前操作者能否删除该文件
     *
     * <p>与修改同档：能改就能删。
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @return 允许返回 true
     */
    public static boolean canDelete(File file, OperatorContext ctx) {
        return isOwnerOrAdmin(file, ctx);
    }

    /**
     * 断言可以修改，否则抛 403
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @throws PermissionDeniedException 无权修改时抛出
     */
    public static void requireCanModify(File file, OperatorContext ctx) {
        if (!canModify(file, ctx)) {
            throw PermissionDeniedException.accessDenied("无权修改该文件");
        }
    }

    /**
     * 断言可以删除，否则抛 403
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @throws PermissionDeniedException 无权删除时抛出
     */
    public static void requireCanDelete(File file, OperatorContext ctx) {
        if (!canDelete(file, ctx)) {
            throw PermissionDeniedException.accessDenied("无权删除该文件");
        }
    }

    /**
     * 「主人或管理员」判定
     *
     * <p>{@code creatorId == null}（历史数据没填归属）时<b>只有管理员</b>能通过 ——
     * 见类注释里的理由。
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @return 允许返回 true
     */
    private static boolean isOwnerOrAdmin(File file, OperatorContext ctx) {
        if (file == null || ctx == null) {
            return false;
        }
        if (ctx.canManageOthers()) {
            return true;
        }
        Long ownerId = file.getCreatorId();
        if (ownerId == null) {
            return false;
        }
        return Objects.equals(ctx.userId(), ownerId);
    }
}
