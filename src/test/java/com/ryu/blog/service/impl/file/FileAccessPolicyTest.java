package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.PermissionDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件访问规则的单元测试
 *
 * <p>这条规则是 Stage 6 补上的安全边界：整改前文件模块
 * <b>整条链路（读 / 写 / 删 / 列表 / 搜索）都没有归属校验</b>，
 * 任何登录用户都能下载别人的文件、改别人的文件信息、删别人的文件。
 *
 * <p>规则本身是纯函数（不碰 Spring、不碰数据库），所以这里不 mock 任何东西 ——
 * 依赖越少，测试越不容易假失败。
 *
 * <p>用例覆盖四个维度：<b>主人 / 他人 / 管理员</b> ×
 * <b>公开 / 私有 / 无归属</b>。
 */
class FileAccessPolicyTest {

    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_ID = 200L;

    /** 普通用户：无「管理他人」能力 */
    private static final OperatorContext NORMAL = new OperatorContext(OWNER_ID, false);
    /** 另一个普通用户 */
    private static final OperatorContext STRANGER = new OperatorContext(OTHER_ID, false);
    /** 管理员：持有「管理他人」能力 */
    private static final OperatorContext ADMIN = new OperatorContext(OTHER_ID, true);
    /** 匿名上下文 */
    private static final OperatorContext ANONYMOUS = OperatorContext.ANONYMOUS;

    // ------------------------------------------------------------------
    // 构造工具
    // ------------------------------------------------------------------

    private static File file(Long creatorId, Integer accessType) {
        File file = new File();
        file.setId(1L);
        file.setFileName("test.png");
        file.setCreatorId(creatorId);
        file.setAccessType(accessType);
        return file;
    }

    private static File publicFile(Long creatorId) {
        return file(creatorId, FileAccessPolicy.ACCESS_PUBLIC);
    }

    private static File privateFile(Long creatorId) {
        return file(creatorId, FileAccessPolicy.ACCESS_PRIVATE);
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Test
    @DisplayName("公开文件：任何登录用户都能读")
    void publicFileIsReadableByAnyone() {
        assertTrue(FileAccessPolicy.canRead(publicFile(OWNER_ID), NORMAL));
        assertTrue(FileAccessPolicy.canRead(publicFile(OWNER_ID), STRANGER));
        assertTrue(FileAccessPolicy.canRead(publicFile(OWNER_ID), ADMIN));
        assertTrue(FileAccessPolicy.isPublic(publicFile(OWNER_ID)));
    }

    @Test
    @DisplayName("私有文件：只有创建者与管理员能读")
    void privateFileIsOnlyReadableByOwnerAndAdmin() {
        assertTrue(FileAccessPolicy.canRead(privateFile(OWNER_ID), NORMAL));
        assertFalse(FileAccessPolicy.canRead(privateFile(OWNER_ID), STRANGER));
        assertTrue(FileAccessPolicy.canRead(privateFile(OWNER_ID), ADMIN));
    }

    @Test
    @DisplayName("私有文件：他人读取抛 403")
    void privateFileThrowsWhenStrangerReads() {
        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanRead(privateFile(OWNER_ID), STRANGER));

        // 本人与管理员不应抛
        FileAccessPolicy.requireCanRead(privateFile(OWNER_ID), NORMAL);
        FileAccessPolicy.requireCanRead(privateFile(OWNER_ID), ADMIN);
    }

    @Test
    @DisplayName("无归属文件（creatorId 为 null）：只有管理员能读")
    void fileWithoutOwnerIsOnlyReadableByAdmin() {
        File orphan = privateFile(null);

        assertFalse(FileAccessPolicy.canRead(orphan, NORMAL));
        assertFalse(FileAccessPolicy.canRead(orphan, STRANGER));
        assertTrue(FileAccessPolicy.canRead(orphan, ADMIN));
    }

    @Test
    @DisplayName("没有操作者上下文 → 一律拒绝")
    void nullContextIsRejected() {
        assertFalse(FileAccessPolicy.canRead(privateFile(OWNER_ID), null));
        assertFalse(FileAccessPolicy.canModify(privateFile(OWNER_ID), null));
        assertFalse(FileAccessPolicy.canDelete(privateFile(OWNER_ID), null));
    }

    @Test
    @DisplayName("匿名上下文 → 私有文件不可读")
    void anonymousCannotReadPrivateFile() {
        assertFalse(FileAccessPolicy.canRead(privateFile(OWNER_ID), ANONYMOUS));
    }

    @Test
    @DisplayName("file 为 null → 一律拒绝")
    void nullFileIsRejected() {
        assertFalse(FileAccessPolicy.canRead(null, NORMAL));
        assertFalse(FileAccessPolicy.canModify(null, ADMIN));
        assertFalse(FileAccessPolicy.isPublic(null));
    }

    // ------------------------------------------------------------------
    // 写（改 / 删）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("公开文件：他人依然不能改、不能删")
    void publicFileIsStillNotWritableByStranger() {
        File pub = publicFile(OWNER_ID);

        assertFalse(FileAccessPolicy.canModify(pub, STRANGER));
        assertFalse(FileAccessPolicy.canDelete(pub, STRANGER));
        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanModify(pub, STRANGER));
        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanDelete(pub, STRANGER));
    }

    @Test
    @DisplayName("私有文件：主人能改能删")
    void ownerCanModifyAndDeleteOwnFile() {
        File priv = privateFile(OWNER_ID);

        assertTrue(FileAccessPolicy.canModify(priv, NORMAL));
        assertTrue(FileAccessPolicy.canDelete(priv, NORMAL));
        FileAccessPolicy.requireCanModify(priv, NORMAL);
        FileAccessPolicy.requireCanDelete(priv, NORMAL);
    }

    @Test
    @DisplayName("私有文件：管理员能改能删")
    void adminCanModifyAndDeleteAnyFile() {
        File priv = privateFile(OWNER_ID);

        assertTrue(FileAccessPolicy.canModify(priv, ADMIN));
        assertTrue(FileAccessPolicy.canDelete(priv, ADMIN));
    }

    @Test
    @DisplayName("私有文件：他人改、删都抛 403")
    void strangerCannotModifyOrDelete() {
        File priv = privateFile(OWNER_ID);

        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanModify(priv, STRANGER));
        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanDelete(priv, STRANGER));
    }

    @Test
    @DisplayName("无归属文件：只有管理员能改能删")
    void fileWithoutOwnerIsOnlyWritableByAdmin() {
        File orphan = privateFile(null);

        assertFalse(FileAccessPolicy.canModify(orphan, NORMAL));
        assertFalse(FileAccessPolicy.canDelete(orphan, NORMAL));
        assertTrue(FileAccessPolicy.canModify(orphan, ADMIN));
        assertTrue(FileAccessPolicy.canDelete(orphan, ADMIN));
    }

    @Test
    @DisplayName("自定义 / 链接访问类型：按私有处理")
    void customAndLinkAccessTreatedAsPrivate() {
        File custom = file(OWNER_ID, FileAccessPolicy.ACCESS_CUSTOM);
        File link = file(OWNER_ID, FileAccessPolicy.ACCESS_LINK);

        assertFalse(FileAccessPolicy.canRead(custom, STRANGER));
        assertFalse(FileAccessPolicy.canRead(link, STRANGER));
        assertTrue(FileAccessPolicy.canRead(custom, NORMAL));
        assertTrue(FileAccessPolicy.canRead(link, NORMAL));
    }
}
