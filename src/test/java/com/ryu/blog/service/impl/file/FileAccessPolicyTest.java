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
 * 任何登录用户都能改别人的文件信息、删别人的文件。
 *
 * <p><b>现在只管写</b>：文件可见性（{@code access_type}）已下线 ——
 * 对象存储桶本身匿名可读，那个字段挡不住直链，只制造「私有文件是安全的」这种错觉。
 * 所以这里只剩下「主人 / 他人 / 管理员」× 「有归属 / 无归属」两维。
 *
 * <p>规则本身是纯函数（不碰 Spring、不碰数据库），所以这里不 mock 任何东西 ——
 * 依赖越少，测试越不容易假失败。
 *
 * @author ryu
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

    private static File file(Long creatorId) {
        File file = new File();
        file.setId(1L);
        file.setFileName("test.png");
        file.setCreatorId(creatorId);
        return file;
    }

    @Test
    @DisplayName("主人：能改能删")
    void ownerCanModifyAndDeleteOwnFile() {
        File own = file(OWNER_ID);

        assertTrue(FileAccessPolicy.canModify(own, NORMAL));
        assertTrue(FileAccessPolicy.canDelete(own, NORMAL));
        FileAccessPolicy.requireCanModify(own, NORMAL);
        FileAccessPolicy.requireCanDelete(own, NORMAL);
    }

    @Test
    @DisplayName("管理员：能改能删任何人的文件")
    void adminCanModifyAndDeleteAnyFile() {
        File others = file(OWNER_ID);

        assertTrue(FileAccessPolicy.canModify(others, ADMIN));
        assertTrue(FileAccessPolicy.canDelete(others, ADMIN));
    }

    @Test
    @DisplayName("他人：改、删都抛 403")
    void strangerCannotModifyOrDelete() {
        File others = file(OWNER_ID);

        assertFalse(FileAccessPolicy.canModify(others, STRANGER));
        assertFalse(FileAccessPolicy.canDelete(others, STRANGER));
        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanModify(others, STRANGER));
        assertThrows(PermissionDeniedException.class,
                () -> FileAccessPolicy.requireCanDelete(others, STRANGER));
    }

    @Test
    @DisplayName("无归属文件（creatorId 为 null）：只有管理员能改能删")
    void fileWithoutOwnerIsOnlyWritableByAdmin() {
        File orphan = file(null);

        assertFalse(FileAccessPolicy.canModify(orphan, NORMAL));
        assertTrue(FileAccessPolicy.canModify(orphan, ADMIN));
        assertTrue(FileAccessPolicy.canDelete(orphan, ADMIN));
    }

    @Test
    @DisplayName("没有操作者上下文 / 匿名上下文：一律拒绝")
    void missingContextIsRejected() {
        File own = file(OWNER_ID);

        assertFalse(FileAccessPolicy.canModify(own, null));
        assertFalse(FileAccessPolicy.canModify(own, ANONYMOUS));
    }

    @Test
    @DisplayName("file 为 null：一律拒绝")
    void nullFileIsRejected() {
        assertFalse(FileAccessPolicy.canModify(null, NORMAL));
        assertFalse(FileAccessPolicy.canModify(null, ADMIN));
        assertFalse(FileAccessPolicy.canDelete(null, ADMIN));
    }
}
