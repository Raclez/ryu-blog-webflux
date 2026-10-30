package com.ryu.blog.constant;

/**
 * 系统常量
 *
 * <p><b>只保留全仓真正有引用的常量。</b>原先这里还有 40 多个
 * {@code PASSWORD_*}、{@code DATA_SCOPE_*}、{@code POST_STATUS_*} 之类的定义，
 * 全部零引用；其中 {@code POST_STATUS_*} 的取值还与 {@code Posts} 实体互相矛盾。
 * 死常量不只是噪音 —— 它们会被后来的人当成「唯一来源」误用。
 *
 * <p>新增常量前先在仓库里 grep 一遍；没有再放进来。
 *
 * @author ryu
 */
public final class SystemConstants {

    private SystemConstants() {
    }

    /** 通用状态：1-正常 */
    public static final int STATUS_NORMAL = 1;
    /** 通用状态：0-禁用 */
    public static final int STATUS_DISABLED = 0;

    /**
     * 超级管理员用户 ID。
     *
     * <p>这个 ID 只用于「禁止把超级管理员删掉/停用/改角色」这类兜底保护；
     * 真正的权限判断一律走 RBAC，不要用「是不是 admin」代替权限码。
     */
    public static final Long SUPER_ADMIN_ID = 1L;

    /**
     * 应用内「公开文件」路由前缀。
     *
     * <p>文件对外只暴露这一条路：
     * {@code GET /files/public/{存储路径}}。
     *
     * <p><b>为什么需要它</b>：存储策略的 {@code getPublicUrl} 在没有配置对象存储访问地址时
     * 必须给出一个兜底地址，而早先的兜底是 {@code /api/files/public/**} ——
     * 项目根本没有 {@code /api} 前缀（{@code webflux.base-path: /}），那条链接是死的。
     * 本地存储 {@code buildAccessUrlAsync} 的兜底 {@code /files/**} 同样不存在，
     * 而且撞在 {@code /files/{id}} 这套按 ID 的 API 命名空间里。
     *
     * <p>为什么公开文件需要一条<b>匿名</b>的直链：{@code <img src>} 带不了
     * {@code Authorization} 头，而按 ID 的路由（{@code /files/preview/{id}}）
     * 每次访问都要 {@code system:file:view} 权限 + 请求头 ——
     * 博客正文里的图片根本渲染不出来。
     */
    public static final String PUBLIC_FILE_ROUTE_PREFIX = "/files/public/";
}
