package com.ryu.blog.constant;

/**
 * 权限标识常量
 *
 * <p><b>常量值必须与 {@code t_permissions.identity} 列逐字一致</b> ——
 * 注解 {@code @SaCheckPermission("...")} 最终是与数据库里的字符串比对的，
 * 写错一个字符就是「注解看起来在、实际永远不生效」的静默失效。
 *
 * <p>本类的取值来自开发库真实数据（见 {@code target/db_backup_before_migration.sql} 的
 * {@code t_permissions} 快照），新增权限时**必须同时更新数据库种子数据**。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class PermissionConstants {

    private PermissionConstants() {
    }

    // =====================================================================
    // 用户模块 system:user:*
    // =====================================================================

    /** 查看用户（列表 / 详情） */
    public static final String USER_VIEW = "system:user:view";
    /** 新增用户 */
    public static final String USER_ADD = "system:user:add";
    /**
     * 编辑用户资料 —— 指<b>编辑自己</b>的资料。
     * 编辑他人资料需要额外持有 {@link #USER_MANAGE}。
     */
    public static final String USER_EDIT = "system:user:edit";
    /** 删除用户 */
    public static final String USER_REMOVE = "system:user:remove";
    /** 重置他人密码 */
    public static final String USER_RESET_PWD = "system:user:resetPwd";
    /** 修改用户启用 / 禁用状态 */
    public static final String USER_STATUS = "system:user:status";
    /** 给用户分配角色 */
    public static final String USER_ASSIGN = "system:user:assign";
    /**
     * 管理他人账户 —— 「改自己」与「改别人」的唯一分界。
     *
     * <p>归属判定式固定为：
     * <pre>{@code ownerId == null || operatorId.equals(ownerId) || hasPermission(USER_MANAGE)}</pre>
     * 不要为每个动作再发明 {@code :any} 变体 identity。
     */
    public static final String USER_MANAGE = "system:user:manage";

    // =====================================================================
    // 角色模块 system:role:*
    // =====================================================================

    /** 查看角色 */
    public static final String ROLE_VIEW = "system:role:view";
    /** 新增角色 */
    public static final String ROLE_ADD = "system:role:add";
    /** 编辑角色 */
    public static final String ROLE_EDIT = "system:role:edit";
    /** 删除角色 */
    public static final String ROLE_REMOVE = "system:role:remove";
    /** 为角色分配权限 */
    public static final String ROLE_ASSIGN = "system:role:assign";
    /** 启用 / 禁用角色 */
    public static final String ROLE_STATUS = "system:role:status";

    // =====================================================================
    // 菜单模块 system:menu:*
    // =====================================================================

    /** 查看菜单 */
    public static final String MENU_VIEW = "system:menu:view";
    /** 新增菜单 */
    public static final String MENU_ADD = "system:menu:add";
    /** 编辑菜单 */
    public static final String MENU_EDIT = "system:menu:edit";
    /** 删除菜单 */
    public static final String MENU_REMOVE = "system:menu:remove";
    /** 为菜单分配角色（决定哪些角色能看到该菜单） */
    public static final String MENU_ASSIGN = "system:menu:assign";

    // =====================================================================
    // 权限模块 system:permission:*
    // =====================================================================

    /** 查看权限（列表 / 某用户 / 某角色） */
    public static final String PERMISSION_VIEW = "system:permission:view";
    /** 新增权限 */
    public static final String PERMISSION_ADD = "system:permission:add";
    /** 编辑权限 / 启停权限 */
    public static final String PERMISSION_EDIT = "system:permission:edit";
    /** 删除权限 */
    public static final String PERMISSION_REMOVE = "system:permission:remove";

    // =====================================================================
    // 内容模块 content:*
    // =====================================================================

    /** 查看文章 */
    public static final String POST_VIEW = "content:post:view";
    /** 新增文章 */
    public static final String POST_ADD = "content:post:add";
    /** 编辑文章 */
    public static final String POST_EDIT = "content:post:edit";
    /** 删除文章 */
    public static final String POST_REMOVE = "content:post:remove";
    /** 发布文章 */
    public static final String POST_PUBLISH = "content:post:publish";
    /** 取消发布 */
    public static final String POST_UNPUBLISH = "content:post:unpublish";

    /** 查看分类 */
    public static final String CATEGORY_VIEW = "content:category:view";
    /** 新增分类 */
    public static final String CATEGORY_ADD = "content:category:add";
    /** 编辑分类 */
    public static final String CATEGORY_EDIT = "content:category:edit";
    /** 删除分类 */
    public static final String CATEGORY_REMOVE = "content:category:remove";

    /** 查看标签 */
    public static final String TAG_VIEW = "content:tag:view";
    /** 新增标签 */
    public static final String TAG_ADD = "content:tag:add";
    /** 编辑标签 */
    public static final String TAG_EDIT = "content:tag:edit";
    /** 删除标签 */
    public static final String TAG_REMOVE = "content:tag:remove";

    // =====================================================================
    // 评论模块 comment:*
    // =====================================================================

    /** 查看评论 */
    public static final String COMMENT_VIEW = "comment:view";
    /** 回复评论 */
    public static final String COMMENT_REPLY = "comment:reply";
    /** 删除评论 */
    public static final String COMMENT_REMOVE = "comment:remove";
    /** 审核评论 */
    public static final String COMMENT_APPROVE = "comment:approve";

    // =====================================================================
    // 监控模块 monitor:*
    // =====================================================================

    /** 查看服务器信息 */
    public static final String MONITOR_SERVER_VIEW = "monitor:server:view";
    /**
     * 查看站点访问统计 —— 全站口径（PV/UV 趋势、热门文章、设备与地区分布）。
     *
     * <p>与「自身数据」的浏览历史不是一回事：{@code GET /view/stats} 统计的是
     * 全站而不是当前用户，所以它是监控类数据，必须有独立能力位，
     * 不能只靠「登录即可访问」。
     */
    public static final String MONITOR_STATS_VIEW = "monitor:stats:view";
    /** 查看在线用户 */
    public static final String MONITOR_ONLINE_VIEW = "monitor:online:view";
    /** 踢下线 */
    public static final String MONITOR_ONLINE_LOGOUT = "monitor:online:logout";
    /** 查看日志 */
    public static final String MONITOR_LOG_VIEW = "monitor:log:view";
    /** 清理日志 */
    public static final String MONITOR_LOG_CLEAN = "monitor:log:clean";

    // =====================================================================
    // 系统配置模块 system:config:*
    // =====================================================================

    /** 查看系统配置（含 AI Provider 配置，仅管理员） */
    public static final String SYSTEM_CONFIG_VIEW = "system:config:view";
    /** 新增 / 修改 / 删除系统配置、清理配置缓存 */
    public static final String SYSTEM_CONFIG_EDIT = "system:config:edit";

    // =====================================================================
    // 存储策略模块 system:storage:*
    // =====================================================================

    /** 查看存储策略（返回值含 AK/SK，需管理员） */
    public static final String SYSTEM_STORAGE_VIEW = "system:storage:view";
    /** 新增 / 修改 / 删除 / 启停存储策略 */
    public static final String SYSTEM_STORAGE_EDIT = "system:storage:edit";

    // =====================================================================
    // 文件模块 system:file:*
    // =====================================================================

    /**
     * 上传文件 —— 含单文件、批量与分片上传。
     *
     * <p>与 {@link #SYSTEM_STORAGE_EDIT} 分开：后者是「改存储策略本身」
     * （换 AK/SK、切策略、删策略），那是管理员的事；
     * 而上传是每个作者都要用的日常操作，锁给管理员等于把编辑器砸了。
     */
    public static final String FILE_UPLOAD = "system:file:upload";
    /** 查看文件（列表 / 详情 / 版本 / 下载 / 预览） */
    public static final String FILE_VIEW = "system:file:view";
    /** 编辑文件信息（文件名、描述、状态） */
    public static final String FILE_EDIT = "system:file:edit";
    /** 删除文件 */
    public static final String FILE_REMOVE = "system:file:remove";

    // =====================================================================
    // 角色码（t_roles.code）
    // =====================================================================

    /** 超级管理员角色码 —— 与库中 {@code t_roles.code} 的大小写严格一致 */
    public static final String ROLE_CODE_ADMIN = "ADMIN";
    /** 普通用户角色码 */
    public static final String ROLE_CODE_USER = "USER";
}
