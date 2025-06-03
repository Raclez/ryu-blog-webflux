package com.ryu.blog.constant;

/**
 * 权限常量
 * 
 * @author ryu
 */
public class PermissionConstants {
    
    /** 通用权限 */
    public static final String ALL = "*:*:*";            // 所有权限
    public static final String VIEW = "*:view";          // 查看权限
    public static final String LIST = "*:list";          // 列表权限
    public static final String ADD = "*:add";            // 添加权限
    public static final String EDIT = "*:edit";          // 编辑权限
    public static final String REMOVE = "*:remove";      // 删除权限
    public static final String EXPORT = "*:export";      // 导出权限
    public static final String IMPORT = "*:import";      // 导入权限
    
    /** 用户模块 */
    public static final String USER_VIEW = "system:user:view";          // 查看用户
    public static final String USER_LIST = "system:user:list";          // 用户列表
    public static final String USER_ADD = "system:user:add";            // 添加用户
    public static final String USER_EDIT = "system:user:edit";          // 编辑用户
    public static final String USER_REMOVE = "system:user:remove";      // 删除用户
    public static final String USER_EXPORT = "system:user:export";      // 导出用户
    public static final String USER_IMPORT = "system:user:import";      // 导入用户
    public static final String USER_RESET_PWD = "system:user:resetPwd"; // 重置密码
    
    /** 角色模块 */
    public static final String ROLE_VIEW = "system:role:view";          // 查看角色
    public static final String ROLE_LIST = "system:role:list";          // 角色列表
    public static final String ROLE_ADD = "system:role:add";            // 添加角色
    public static final String ROLE_EDIT = "system:role:edit";          // 编辑角色
    public static final String ROLE_REMOVE = "system:role:remove";      // 删除角色
    public static final String ROLE_EXPORT = "system:role:export";      // 导出角色
    
    /** 菜单模块 */
    public static final String MENU_VIEW = "system:menu:view";          // 查看菜单
    public static final String MENU_LIST = "system:menu:list";          // 菜单列表
    public static final String MENU_ADD = "system:menu:add";            // 添加菜单
    public static final String MENU_EDIT = "system:menu:edit";          // 编辑菜单
    public static final String MENU_REMOVE = "system:menu:remove";      // 删除菜单
    
    /** 文章模块 */
    public static final String POST_VIEW = "content:post:view";          // 查看文章
    public static final String POST_LIST = "content:post:list";          // 文章列表
    public static final String POST_ADD = "content:post:add";            // 添加文章
    public static final String POST_EDIT = "content:post:edit";          // 编辑文章
    public static final String POST_REMOVE = "content:post:remove";      // 删除文章
    public static final String POST_PUBLISH = "content:post:publish";    // 发布文章
    public static final String POST_UNPUBLISH = "content:post:unpublish";// 取消发布
    
    /** 分类模块 */
    public static final String CATEGORY_VIEW = "content:category:view";          // 查看分类
    public static final String CATEGORY_LIST = "content:category:list";          // 分类列表
    public static final String CATEGORY_ADD = "content:category:add";            // 添加分类
    public static final String CATEGORY_EDIT = "content:category:edit";          // 编辑分类
    public static final String CATEGORY_REMOVE = "content:category:remove";      // 删除分类
    
    /** 标签模块 */
    public static final String TAG_VIEW = "content:tag:view";          // 查看标签
    public static final String TAG_LIST = "content:tag:list";          // 标签列表
    public static final String TAG_ADD = "content:tag:add";            // 添加标签
    public static final String TAG_EDIT = "content:tag:edit";          // 编辑标签
    public static final String TAG_REMOVE = "content:tag:remove";      // 删除标签
    
    /** 评论模块 */
    public static final String COMMENT_VIEW = "content:comment:view";          // 查看评论
    public static final String COMMENT_LIST = "content:comment:list";          // 评论列表
    public static final String COMMENT_ADD = "content:comment:add";            // 添加评论
    public static final String COMMENT_EDIT = "content:comment:edit";          // 编辑评论
    public static final String COMMENT_REMOVE = "content:comment:remove";      // 删除评论
    public static final String COMMENT_APPROVE = "content:comment:approve";    // 审核评论
    
    /** 文件模块 */
    public static final String FILE_VIEW = "system:file:view";          // 查看文件
    public static final String FILE_LIST = "system:file:list";          // 文件列表
    public static final String FILE_ADD = "system:file:add";            // 上传文件
    public static final String FILE_DOWNLOAD = "system:file:download";  // 下载文件
    public static final String FILE_REMOVE = "system:file:remove";      // 删除文件
    
    /** 配置模块 */
    public static final String CONFIG_VIEW = "system:config:view";          // 查看配置
    public static final String CONFIG_LIST = "system:config:list";          // 配置列表
    public static final String CONFIG_ADD = "system:config:add";            // 添加配置
    public static final String CONFIG_EDIT = "system:config:edit";          // 编辑配置
    public static final String CONFIG_REMOVE = "system:config:remove";      // 删除配置
    
    /** 系统管理 */
    public static final String SYSTEM_MONITOR = "system:monitor";        // 系统监控
    public static final String SYSTEM_TOOL = "system:tool";              // 系统工具
    public static final String SYSTEM_LOG = "system:log";                // 系统日志
    public static final String SYSTEM_BACKUP = "system:backup";          // 系统备份
    public static final String SYSTEM_RESTORE = "system:restore";        // 系统恢复
    
    /** 日志模块 */
    public static final String LOG_VIEW = "system:log:view";              // 查看日志
    public static final String LOG_LIST = "system:log:list";              // 日志列表
    public static final String LOG_REMOVE = "system:log:remove";          // 删除日志
    public static final String LOG_CLEAN = "system:log:clean";            // 清空日志
} 