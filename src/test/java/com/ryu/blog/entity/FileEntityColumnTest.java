package com.ryu.blog.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体字段必须与 DDL 保持一致。
 *
 * <p><b>它守的是什么</b>：R2DBC 的派生查询按<b>实体</b>推导列名。
 * 实体里留着一个已经被 `apply-ddl.sh` 删掉的列，
 * 迁移跑完之后每一条 `SELECT … FROM t_file` 都会变成
 * {@code Unknown column 'xxx' in 'field list'} —— 上传、下载、删除、搜索全 500。
 *
 * <p>这几个列在代码里从无写入，属于「删了就删了」的死列；
 * 这条测试保证实体与 schema.sql / apply-ddl.sh 同步删除，不会只剩一半。
 *
 * @author ryu
 */
class FileEntityColumnTest {

    /** 本轮从 t_file 上删除的死列。 */
    private static final List<String> DROPPED_COLUMNS = List.of(
            "cdnUrl", "shareKey", "shareExpireTime", "hasThumbnail", "thumbnailPath");

    @Test
    @DisplayName("File 实体不再声明已被 DDL 删除的列")
    void entityHasNoDroppedColumns() {
        List<String> present = Arrays.stream(File.class.getDeclaredFields())
                .map(Field::getName)
                .filter(DROPPED_COLUMNS::contains)
                .collect(Collectors.toList());

        assertTrue(present.isEmpty(),
                "实体里还有这些列，而 schema.sql / apply-ddl.sh 已经把它们删了：" + present
                        + " —— 迁移之后所有 t_file 查询都会报 Unknown column");
    }
}
