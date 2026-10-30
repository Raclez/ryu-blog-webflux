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
 * <p><b>读与写用两套规则</b>：文件有「公开 / 私有」属性
 * （{@code access_type}），公开文件任何登录用户都能读，
 * 但<b>只有主人和管理员能改</b>。把两者混成一条规则，
 * 就会出现「公开的文件谁都能改」这种事故。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class FileAccessPolicy {

    private FileAccessPolicy() {
    }

    /** 公开：任何登录用户都能读 */
    public static final int ACCESS_PUBLIC = 0;
    /** 私有：仅创建者与管理员可读可写 */
    public static final int ACCESS_PRIVATE = 1;
    /**
     * 自定义：<b>未实现</b>，按私有处理。
     *
     * <p>库里 {@code access_type} 定义了 4 种，实际实现的只有公开与私有两种。
     * 保留常量是为了不打断已有调用方（历史数据里可能出现 2/3），
     * 但任何写入路径都不该再产生这两个值 —— 它们没有任何配套的判定逻辑，
     * 只会在读侧被当成私有，用户以为「共享给了指定的人」，实际谁也没有访问权。
     */
    public static final int ACCESS_CUSTOM = 2;

    /**
     * 链接访问：<b>未实现</b>，按私有处理。
     *
     * <p>分享链接功能从未实现（{@code t_file} 的 {@code share_key} 列从无写入，
     * 本轮已随表瘦身删除）。保留常量仅用于识别历史数据。
     */
    public static final int ACCESS_LINK = 3;

    /**
     * 文件是否对所有人开放读取
     *
     * @param file 文件
     * @return 公开返回 true
     */
    public static boolean isPublic(File file) {
        return file != null
                && file.getAccessType() != null
                && file.getAccessType() == ACCESS_PUBLIC;
    }

    /**
     * 当前操作者能否读取该文件
     *
     * <p>规则：公开文件谁都能读；非公开文件只有创建者与管理员能读。
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @return 允许返回 true
     */
    public static boolean canRead(File file, OperatorContext ctx) {
        if (file == null) {
            return false;
        }
        if (isPublic(file)) {
            return true;
        }
        return isOwnerOrAdmin(file, ctx);
    }

    /**
     * 当前操作者能否修改该文件
     *
     * <p>规则：只有创建者与管理员能改 —— <b>公开与否不影响写权限</b>。
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
     * 断言可以读取，否则抛 403
     *
     * @param file 文件
     * @param ctx  操作者上下文
     * @throws PermissionDeniedException 无权读取时抛出
     */
    public static void requireCanRead(File file, OperatorContext ctx) {
        if (!canRead(file, ctx)) {
            throw PermissionDeniedException.accessDenied("无权访问该文件");
        }
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
