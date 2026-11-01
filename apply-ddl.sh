#!/usr/bin/env bash
# =============================================================================
# 对齐本地开发库的 schema（幂等，可重复执行）
#
#   目标库：localhost:3306/ryu_blog_webflux
#   凭据从 .env 读取（DB_USERNAME / DB_PASSWORD）
#
# 为什么是 bash 而不是 .sql：
#   MySQL **不支持** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`（那是 MariaDB 的语法），
#   也不支持 `ADD INDEX IF NOT EXISTS`。
#   所以这里统一用「先查 information_schema，再决定要不要执行」的方式实现幂等，
#   而不是把不支持的语法塞进 .sql 里等着报错。
#
# 全新建库请直接用 src/main/resources/schema.sql（那是幂等 DDL，用 CREATE TABLE IF NOT EXISTS）。
#
# 执行前会自动备份一份 dump 到 target/db_backup_before_migration.sql
# =============================================================================
set -eu
cd "$(dirname "$0")"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-ryu_blog_webflux}"
MYSQL_BIN="${MYSQL_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe}"
DUMP_BIN="${DUMP_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe}"

if [ -f .env ]; then
    # 注意 || true：set -e 下，"VAR=$(grep ...)" 如果 grep 没匹配到会返回 1，
    # 整个脚本会直接退出。.env 里缺某个键是正常情况，不能因此中止。
    export MYSQL_PWD="$(grep -E '^DB_PASSWORD=' .env | cut -d= -f2- || true)"
    DB_USER="$(grep -E '^DB_USERNAME=' .env | cut -d= -f2- || true)"
fi
DB_USER="${DB_USER:-root}"

# 统一走 utf8mb4，否则中文 COMMENT 会被按客户端默认字符集（Windows 上是 GBK）写坏
mysql_q() { "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N -B "$DB_NAME" -e "$1"; }
mysql_ddl() { "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME" -e "$1"; }

mkdir -p target

echo "==> 目标: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "==> MySQL 版本: $(mysql_q 'SELECT VERSION()')"

# ---- 前置检查：确认连得上，且这就是要改的库 ----
echo "==> 当前库概况（请确认这是你要改的库）:"
mysql_q "SELECT CONCAT('  表数量: ', COUNT(*)) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'" || true
mysql_q "SELECT CONCAT('  t_posts 行数: ', COUNT(*)) FROM \`$DB_NAME\`.t_posts" 2>/dev/null || echo "  （t_posts 还不存在）" || true
mysql_q "SELECT CONCAT('  t_file  行数: ', COUNT(*)) FROM \`$DB_NAME\`.t_file" 2>/dev/null || echo "  （t_file 还不存在）" || true

# ---- 前置检查：迁移要用到的表是否都在 ----
# 缺表不阻断整次迁移（各表之间是独立的），只跳过该表上的操作并在最后汇总。
echo "==> 前置检查：目标表是否齐全"
REQUIRED_TABLES="t_favorites t_role_permissions t_post_categories t_post_tags \
t_resource_group_file_rels t_ai_generation_history t_view_history t_posts t_file \
t_comments t_permissions t_menus t_storage_config t_user_roles t_sys_dict_types \
t_sys_dict_items t_sys_role_menus t_async_tasks"
MISSING=""
for t in $REQUIRED_TABLES; do
    n=$(mysql_q "SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$t'")
    if [ "$n" = "0" ]; then
        MISSING="$MISSING $t"
    fi
done
if [ -n "$MISSING" ]; then
    echo "!! 以下表不存在，相关操作将跳过：$MISSING"
    echo "!! （表是实体定义的一部分，本脚本不负责建表；全新库请用 src/main/resources/schema.sql）"
else
    echo "    全部就位"
fi

# 表是否在缺失清单里
table_missing() {
    case " $MISSING " in
        *" $1 "*) return 0 ;;
        *) return 1 ;;
    esac
}

echo "==> 备份现有库到 target/db_backup_before_migration.sql"
# 备份里是整库数据（含账号、可能的敏感字段），先建好权限再写内容，
# 避免"创建时是 644、chmod 之前已经可读"的窗口期。
mkdir -p target
chmod 700 target 2>/dev/null || true
install -m 600 /dev/null target/db_backup_before_migration.sql 2>/dev/null \
    || : > target/db_backup_before_migration.sql
chmod 600 target/db_backup_before_migration.sql 2>/dev/null || true
"$DUMP_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --databases "$DB_NAME" \
    --routines --triggers > target/db_backup_before_migration.sql
echo "    备份完成 ($(wc -l < target/db_backup_before_migration.sql) 行, 权限 600)"

# =============================================================================
# 一、补齐实体声明但表中缺失的列
# =============================================================================
add_column() {
    local table="$1" col="$2" definition="$3"
    local n
    if table_missing "$table"; then
        echo "    表不存在，跳过: $table.$col"
        return 0
    fi
    n=$(mysql_q "SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$table' AND COLUMN_NAME='$col'")
    if [ "$n" != "0" ]; then
        echo "    已存在，跳过: $table.$col"
        return 0
    fi
    echo "    新增列: $table.$col"
    mysql_ddl "ALTER TABLE \`$table\` ADD COLUMN \`$col\` $definition"
}

DEL_FLAG="tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除'"
UPD_TIME="datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'"

echo
echo "==> 一、补列"
add_column t_favorites                 is_deleted    "$DEL_FLAG"
add_column t_role_permissions          is_deleted    "$DEL_FLAG"
add_column t_post_categories           update_time   "$UPD_TIME"
add_column t_post_categories           is_deleted    "$DEL_FLAG"
add_column t_post_tags                 update_time   "$UPD_TIME"
add_column t_post_tags                 is_deleted    "$DEL_FLAG"
add_column t_resource_group_file_rels  is_deleted    "$DEL_FLAG"
add_column t_ai_generation_history     update_time   "$UPD_TIME"
add_column t_view_history              user_id       "bigint DEFAULT NULL COMMENT '登录用户ID，匿名访客为NULL'"
add_column t_posts                     slug          "varchar(255) DEFAULT NULL COMMENT '文章别名（URL slug）'"
# 置顶标记。DTO/VO 与前端开关都有「置顶」，实体与表里却没有 ——
# 客户端传的置顶标记被 MapStruct 静默丢弃，开关永远停在「关」。
add_column t_posts                     is_sticky     "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是'"
# 延迟任务的计划执行时间。DB 是「到期」的权威来源：Redis 丢数据后，
# ScheduledTaskPromoter 靠这一列把任务重新捞回队列。
add_column t_async_tasks              execute_at    "datetime DEFAULT NULL COMMENT '计划执行时间（NULL=立即执行）'"

# =============================================================================
# 二、下线 t_menu_permissions
# =============================================================================
echo
echo "==> 二、下线 t_menu_permissions"
# 菜单可见性模型已改为 role → menu 直连（t_sys_role_menus），
# 「菜单 ↔ 权限」的对应关系改由前端按 permission identity 控制按钮显隐，
# t_menu_permissions 这张表连同 MenuPermission 实体 / MenuPermissionRepository
# 一并退场。先确认表里没有数据再删 —— 有数据就保留并告警，不擅自丢数据。
mp_rows=$(mysql_q "SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_menu_permissions'" || echo 0)
if [ "${mp_rows:-0}" = "0" ]; then
    echo "    表不存在，跳过"
else
    mp_data=$(mysql_q "SELECT COUNT(*) FROM \`t_menu_permissions\`" || echo 0)
    if [ "${mp_data:-0}" = "0" ]; then
        echo "    删除空表: t_menu_permissions"
        mysql_ddl "DROP TABLE IF EXISTS \`t_menu_permissions\`"
    else
        echo "    !! t_menu_permissions 还有 $mp_data 行数据 —— 保留，需要人工确认后再删"
    fi
fi

# =============================================================================
# 三、删除死代码遗留的空表
# =============================================================================
echo
echo "==> 三、删除死表"
for t in t_likes t_file_logs t_file_metadata t_file_permissions \
         t_file_shares t_file_thumbnails t_file_versions t_network_disk; do
    n=$(mysql_q "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$t'")
    if [ "$n" = "0" ]; then
        echo "    不存在，跳过: $t"
    else
        echo "    删除: $t"
        mysql_ddl "DROP TABLE IF EXISTS \`$t\`"
    fi
done

# =============================================================================
# 四、补齐索引
#
# 索引选择依据（对应 Repository 中的实际查询）：
#   t_file          is_deleted + create_time / creator_id / file_type / checksum / file_path
#   t_view_history  visitor_id + create_time / visitor_id + post_id / post_id / create_time
#   t_comments      post_id + parent_comment_id + status + is_deleted + create_time 等
#   t_posts         user_id + is_deleted + create_time / status + is_deleted + views
# =============================================================================
add_index() {
    local table="$1" index="$2" cols="$3"
    local n
    if table_missing "$table"; then
        echo "    表不存在，跳过: $table.$index"
        return 0
    fi
    n=$(mysql_q "SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$table' AND INDEX_NAME='$index'")
    if [ "$n" != "0" ]; then
        echo "    已存在，跳过: $table.$index"
        return 0
    fi
    echo "    新增索引: $table.$index"
    mysql_ddl "ALTER TABLE \`$table\` ADD INDEX \`$index\` ($cols)"
}

drop_index() {
    local table="$1" index="$2"
    local n
    if table_missing "$table"; then
        echo "    表不存在，跳过: $table.$index"
        return 0
    fi
    n=$(mysql_q "SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$table' AND INDEX_NAME='$index'")
    if [ "$n" = "0" ]; then
        echo "    不存在，跳过: $table.$index"
        return 0
    fi
    echo "    删除索引: $table.$index"
    mysql_ddl "ALTER TABLE \`$table\` DROP INDEX \`$index\`"
}

# 唯一键。定义放在这里（而不是第五节）是因为第四节也会用到 ——
# bash 里函数必须先定义后调用。
add_unique_index() {
    local table="$1" index="$2" cols="$3"
    local n
    if table_missing "$table"; then
        echo "    表不存在，跳过: $table.$index"
        return 0
    fi
    n=$(mysql_q "SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$table' AND INDEX_NAME='$index'")
    if [ "$n" != "0" ]; then
        echo "    已存在，跳过: $table.$index"
        return 0
    fi
    echo "    新增唯一键: $table.$index"
    mysql_ddl "ALTER TABLE \`$table\` ADD UNIQUE KEY \`$index\` ($cols)"
}

echo
echo "==> 四、补索引"

# ---- t_file：此前只有主键，所有文件列表都是全表扫描 ----
add_index t_file idx_file_deleted_time '`is_deleted`, `create_time`'
add_index t_file idx_file_creator      '`creator_id`, `is_deleted`'
add_index t_file idx_file_type         '`file_type`, `is_deleted`, `create_time`'
add_index t_file idx_file_path         '`file_path`'
add_index t_file idx_file_checksum     '`checksum`(64), `is_deleted`'

# ---- t_view_history：此前只有主键 ----
add_index t_view_history idx_vh_visitor_time '`visitor_id`, `create_time`'
add_index t_view_history idx_vh_visitor_post '`visitor_id`, `post_id`'
add_index t_view_history idx_vh_user_time    '`user_id`, `create_time`'
add_index t_view_history idx_vh_post         '`post_id`'
add_index t_view_history idx_vh_create_time  '`create_time`'

# ---- t_comments：原有单列索引无法覆盖 status/is_deleted/create_time 过滤 ----
add_index t_comments idx_comment_post   '`post_id`, `parent_comment_id`, `status`, `is_deleted`, `create_time`'
add_index t_comments idx_comment_user   '`user_id`, `status`, `is_deleted`, `create_time`'
add_index t_comments idx_comment_parent '`parent_comment_id`, `status`, `is_deleted`, `create_time`'
drop_index t_comments idx_post_id
drop_index t_comments idx_user_id

# ---- t_posts：idx_user 与 idx_user_id 完全重复；缺 user_id 排序与热门榜排序 ----
add_index t_posts idx_posts_user         '`user_id`, `is_deleted`, `create_time`'
add_index t_posts idx_posts_status_views '`status`, `is_deleted`, `views`'
# slug 唯一索引：应用层已有 checkSlugAvailable 校验（ArticleCrudService），
# 数据库再加一道兜底 —— 唯一键冲突会变成 500，比业务错误码难看，
# 但也只在应用层校验被绕过时才会发生。
add_unique_index t_posts uk_posts_slug    '`slug`'
drop_index t_posts idx_user
drop_index t_posts idx_user_id

# ---- 角色/权限/菜单/存储配置：此前均无二级索引 ----
add_index t_role_permissions         idx_rp_role       '`role_id`'
add_index t_role_permissions         idx_rp_permission '`permission_id`'
add_index t_permissions             idx_perm_identity '`identity`'
add_index t_permissions             idx_perm_active   '`is_active`'
add_index t_menus                    idx_menus_parent_sort '`parent_id`, `sort`'
add_index t_menus                    idx_menus_type       '`type`, `is_deleted`'
add_index t_storage_config           idx_sc_strategy      '`strategy_name`'
add_index t_storage_config           idx_sc_enable        '`is_enable`'
add_index t_resource_group_file_rels idx_rgfr_file        '`file_id`'
add_index t_user_roles               idx_ur_role          '`role_id`'
add_index t_sys_dict_types           idx_dt_status        '`status`, `is_deleted`'

# ---- 字典唯一键：第九节种子脚本的幂等性完全依赖这两条 UNIQUE ----
# schema.sql 建库时就有；但按**旧版** schema.sql 建出来的库可能没有。
# 没有唯一键 → ON DUPLICATE KEY UPDATE 不会触发 → 种子脚本每跑一次就多一份重复项
# （重复的 dict_type 会让字典接口返回重复选项，前端单选框出现两个「公开访问」）。
# 加唯一键遇到重复数据会失败 —— 只告警不中止，重复行由用户自己决定怎么清。
add_unique_index t_sys_dict_types uk_dict_type '`dict_type`' \
    || echo "    !! uk_dict_type 创建失败 —— t_sys_dict_types 里可能已有重复的 dict_type"
add_unique_index t_sys_dict_items uk_dict_type_key '`dict_type_id`, `dict_item_key`' \
    || echo "    !! uk_dict_type_key 创建失败 —— t_sys_dict_items 里可能已有重复的字典项"

# ---- t_async_tasks：延迟任务到期扫描（status=PENDING AND execute_at<=now）走这条索引 ----
add_index t_async_tasks              idx_async_tasks_status_execute '`status`, `execute_at`'

# idx_dict_type_id 是 uk_dict_type_key(dict_type_id, dict_item_key) 的左前缀冗余。
drop_index t_sys_dict_items idx_dict_type_id

# =============================================================================
# 五、RBAC 模型收口（role → menu 直连 + 权限标识唯一化）
#
# 背景：
#   1) t_sys_role_menus 此前只有 id/role_id/menu_id，缺项目约定的审计字段；
#   2) t_permissions.identity 是 @SaCheckPermission 的比对依据，却只有普通索引 ——
#      允许重复，鉴权结果不可预期；
#   3) t_role_permissions 缺 (role_id, permission_id) 唯一键（t_user_roles 有、
#      t_menu_permissions 有，唯独这张漏了）；
#   4) 整库没有任何 RBAC 种子数据 —— 全新部署一个角色都没有，
#      开发库的 is_default 也全是 NULL，注册出来的用户永远没有任何角色。
#
# 种子数据在 src/main/resources/db/03_rbac_seed.sql（幂等）。
# =============================================================================
echo
echo "==> 五、RBAC 模型收口"

add_column t_sys_role_menus create_time "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'"
add_column t_sys_role_menus update_time "$UPD_TIME"
add_column t_sys_role_menus is_deleted  "$DEL_FLAG"
add_index  t_sys_role_menus idx_srm_menu '`menu_id`'

# (role_id, menu_id) 唯一键 —— 种子脚本的 INSERT ... ON DUPLICATE KEY UPDATE 靠它才能幂等。
# 没有唯一键时 ON DUPLICATE 永远不会触发，脚本每跑一次就多一份重复绑定。
# schema.sql 里本来就有这条约束，但开发库未必是按当前 schema.sql 建的，所以这里补建。
# 先删重复行再建键，否则 ADD UNIQUE 会直接失败。
dup_role_menus=$(mysql_q "SELECT COUNT(*) FROM (
    SELECT \`role_id\`, \`menu_id\` FROM \`t_sys_role_menus\`
    GROUP BY \`role_id\`, \`menu_id\` HAVING COUNT(*) > 1) d")
if [ "${dup_role_menus:-0}" != "0" ]; then
    echo "    发现重复的 role-menu 绑定，删除多余的（保留 id 最小的）"
    mysql_ddl "DELETE rm1 FROM \`t_sys_role_menus\` rm1
               JOIN \`t_sys_role_menus\` rm2
                 ON rm1.\`role_id\` = rm2.\`role_id\`
                AND rm1.\`menu_id\` = rm2.\`menu_id\`
                AND rm1.\`id\` > rm2.\`id\`"
fi

add_unique_index t_sys_role_menus    uk_role_menu        '`role_id`, `menu_id`'

# identity 唯一化：先删掉重复的（保留 id 最小的），再建唯一键
dup_identities=$(mysql_q "SELECT COUNT(*) FROM (
    SELECT \`identity\` FROM \`t_permissions\`
    WHERE \`identity\` IS NOT NULL
    GROUP BY \`identity\` HAVING COUNT(*) > 1) d")
if [ "${dup_identities:-0}" != "0" ]; then
    echo "    发现重复的 permission identity，删除多余的（保留 id 最小的）"
    mysql_ddl "DELETE p1 FROM \`t_permissions\` p1
               JOIN \`t_permissions\` p2
                 ON p1.\`identity\` = p2.\`identity\` AND p1.\`id\` > p2.\`id\`"
fi

add_unique_index t_permissions      uk_perm_identity    '`identity`'
add_unique_index t_role_permissions uk_role_permission '`role_id`, `permission_id`'

# t_roles.code 唯一键 —— 种子脚本给角色做 upsert 时靠它。
# 开发库可能缺（表是更早的版本建的），补建；先删重复 code 再建键。
dup_role_codes=$(mysql_q "SELECT COUNT(*) FROM (
    SELECT \`code\` FROM \`t_roles\`
    WHERE \`code\` IS NOT NULL
    GROUP BY \`code\` HAVING COUNT(*) > 1) d")
if [ "${dup_role_codes:-0}" != "0" ]; then
    echo "    发现重复的 role code，删除多余的（保留 id 最小的）"
    mysql_ddl "DELETE r1 FROM \`t_roles\` r1
               JOIN \`t_roles\` r2
                 ON r1.\`code\` = r2.\`code\` AND r1.\`id\` > r2.\`id\`"
fi

add_unique_index t_roles           uk_code             '`code`'

# idx_perm_identity 是 uk_perm_identity 的左前缀冗余
drop_index t_permissions idx_perm_identity

echo
echo "==> 六、写入 RBAC 种子数据（db/03_rbac_seed.sql）"
SEED_FILE="src/main/resources/db/03_rbac_seed.sql"
if [ ! -f "$SEED_FILE" ]; then
    echo "    !! 找不到 $SEED_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME" \
        < "$SEED_FILE" && echo "    种子数据写入完成" || echo "    !! 种子数据写入失败"
fi

# =============================================================================
# 七、启用 RBAC 模型要求可用的权限
#
# 03_rbac_seed.sql 的 ON DUPLICATE KEY UPDATE 刻意不覆盖 is_active
# （「管理员手工停用的权限不该被种子脚本重新打开」），
# 于是 2025-06-18 那批种子里写成 is_active = 0 的权限至今仍是停用状态。
# 停用 = 解析器过滤掉 = @SaCheckPermission 永远不通过 = 端点对所有人 403。
# 已经踩中的：MonitorController / SysConfigController / StorageConfigController
# 三个类级 @SaCheckPermission 的控制器整类锁死（连管理员都进不去）。
#
# 这一步必须在第六步之后 —— 种子负责插入缺失的 identity，本步负责启用。
# =============================================================================
echo
echo "==> 七、启用被停用的权限（db/04_activate_permissions.sql）"
ACTIVATE_FILE="src/main/resources/db/04_activate_permissions.sql"
if [ ! -f "$ACTIVATE_FILE" ]; then
    echo "    !! 找不到 $ACTIVATE_FILE，跳过 —— 相关端点会对所有人 403"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME" \
        < "$ACTIVATE_FILE" && echo "    权限启用完成" || echo "    !! 权限启用失败"
fi

# =============================================================================
# 八、清理死任务类型的历史行
#
# EMAIL / REPORT / DATA_EXPORT / FILE_PROCESS 这四个 task_type 从来没有对应的
# TaskHandler —— 提交出去必然失败，等于「投进去就沉底」。
# 它们的枚举常量已从 TaskType 里删除，而 R2DBC 是按枚举名映射的：
# 库里若还留着这几行，读取时直接抛「未知枚举值」。
#
# 这些任务本来就全是失败的，删掉不损失任何有效数据。
# ⚠️ 必须先跑这一步再启动新版本，否则旧行会让 AsyncTask 的读取炸掉。
# =============================================================================
echo
echo "==> 八、清理死任务类型的历史行"
if table_missing "t_async_tasks"; then
    echo "    表不存在，跳过"
else
    dead_rows=$(mysql_q "SELECT COUNT(*) FROM \`t_async_tasks\`
                       WHERE \`task_type\` IN ('EMAIL','REPORT','DATA_EXPORT','FILE_PROCESS')" || echo 0)
    echo "    待清理的死任务行数: ${dead_rows:-0}"
    if [ "${dead_rows:-0}" = "0" ]; then
        echo "    无需清理"
    else
        # 清理失败不中止整次迁移（后面的校验还要跑），但必须显式告警 ——
        # 这几行留着，新版本读 AsyncTask 时会因未知枚举值直接炸。
        mysql_ddl "DELETE FROM \`t_async_tasks\`
                    WHERE \`task_type\` IN ('EMAIL','REPORT','DATA_EXPORT','FILE_PROCESS')" \
            && echo "    清理完成" \
            || echo "    !! 清理失败 —— 启动新版本前必须手工清掉这些行"
    fi
fi

# =============================================================================
# 九、文章模块的字典种子数据（db/05_article_dict_seed.sql）
#
# 前端编辑器的「访问权限」单选组是按字典渲染的：
#   GET /sysDictItem/type/article_visibility
# 而字典表从来没有种子数据 —— 接口返回空数组 → 单选组一个选项都没有 →
# 用户根本没法改可见性（而后端 PostCreateDTO 一直在校验
# ^(public|private|password)$，契约是存在的，只是前端拿不到选项）。
#
# 这一步必须在唯一键就位之后（apply-ddl.sh 第四节建了 uk_dict_type
# 与 uk_dict_type_key），否则 ON DUPLICATE KEY UPDATE 不会触发，
# 脚本每跑一次就多一份重复项。
# =============================================================================
echo
echo "==> 九、写入文章字典种子数据（db/05_article_dict_seed.sql）"
ARTICLE_DICT_FILE="src/main/resources/db/05_article_dict_seed.sql"
if [ ! -f "$ARTICLE_DICT_FILE" ]; then
    echo "    !! 找不到 $ARTICLE_DICT_FILE，跳过 —— 编辑器的「访问权限」会是空的"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME" \
        < "$ARTICLE_DICT_FILE" && echo "    文章字典写入完成" || echo "    !! 文章字典写入失败"
fi

# ---- 别名归一化：历史遗留的空串 slug ----
#
# t_posts.slug 上有唯一键 uk_posts_slug。编辑器里不填别名时前端会发 slug: ""，
# 而这条数据在「归一化之前」是直接落库的空串 —— 于是：
#   1) 第二篇不填别名的文章会撞唯一键，报
#      "Duplicate entry '' for key 't_posts.uk_posts_slug'"；
#   2) 空串占住了唯一键的位置，等于「这篇文章有别名」。
#
# 应用侧已改成「空白串归一成 NULL」（MySQL 的唯一键不区分 NULL，多个 NULL 可共存），
# 这里把历史遗留的空串也刷成 NULL，与应用侧的口径对齐。
# 幂等：只影响 slug = '' 的行，重复执行没有副作用。
if table_missing "t_posts"; then
    echo "    t_posts 不存在，跳过别名归一化"
else
    blank_slugs=$(mysql_q "SELECT COUNT(*) FROM \`t_posts\` WHERE \`slug\` = ''" || echo 0)
    echo "    待归一化的空串别名行数: ${blank_slugs:-0}"
    if [ "${blank_slugs:-0}" != "0" ]; then
        mysql_ddl "UPDATE \`t_posts\` SET \`slug\` = NULL WHERE \`slug\` = ''" \
            && echo "    别名归一化完成" \
            || echo "    !! 别名归一化失败 —— 不填别名的文章会继续撞唯一键"
    fi
fi

# =============================================================================
echo "==> 十、t_file 瘦身：删除从无写入的死列"
# =============================================================================
#
# 这 5 列在代码里从来没有写入过（只有实体声明），留着只会误导后来人：
# 看到 cdn_url 会以为有 CDN、看到 share_key 会以为有分享功能。
# MySQL 8 不支持 DROP COLUMN IF EXISTS，所以先查 information_schema，保证幂等。
drop_column() {
    local table="$1" column="$2"
    local exists
    exists=$(mysql_q "SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$table'
                          AND COLUMN_NAME='$column'")
    if [ "$exists" = "0" ]; then
        echo "    列不存在，跳过: $table.$column"
        return
    fi
    mysql_ddl "ALTER TABLE \`$table\` DROP COLUMN \`$column\`"
    echo "    已删除列: $table.$column"
}

drop_column t_file cdn_url
drop_column t_file share_key
drop_column t_file share_expire_time
drop_column t_file has_thumbnail
drop_column t_file thumbnail_path

# =============================================================================
# 十一、字典与后端契约对齐（db/08_dict_alignment.sql）
#
# article_status 的 key 曾经是 draft/published/pending/offline，而 t_posts.status
# 是 Integer 0..3 —— 前端把字典 value 原样当查询参数发出去，状态筛选根本用不了。
# 这一步把它改成数字键，并补上看板待办在用的 todo_priority（库里一直没有）。
#
# 同样必须在唯一键就位之后执行（第四节建 uk_dict_type / uk_dict_type_key），
# 否则 ON DUPLICATE KEY UPDATE 不触发，重跑会插出重复项。
# =============================================================================
echo
echo "==> 十一、字典对齐（db/08_dict_alignment.sql）"
DICT_ALIGN_FILE="src/main/resources/db/08_dict_alignment.sql"
if [ ! -f "$DICT_ALIGN_FILE" ]; then
    echo "    !! 找不到 $DICT_ALIGN_FILE，跳过 —— 文章状态筛选与看板优先级会是空的"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$DICT_ALIGN_FILE" && echo "    字典对齐完成" || echo "    !! 字典对齐失败"
fi

# =============================================================================
# 十二、清洗不生效的系统配置（db/09_purge_dead_config.sql）
#
# system.storage.* / system.cache.* / security.jwt.* / security.login.* /
# security.password.* 在代码里零引用：管理员在系统设置页改它们会显示「更新成功」，
# 但系统行为不会变。它们各自都有真正生效的配置来源（t_storage_config、
# CacheConfig 里的 Caffeine、application.yml 的 sa-token.*）。
#
# 留着比删掉危险 —— 删了至少没人以为自己改的生效了。
# =============================================================================
echo
echo "==> 十二、清洗不生效的系统配置（db/09_purge_dead_config.sql）"
DEAD_CONFIG_FILE="src/main/resources/db/09_purge_dead_config.sql"
if [ ! -f "$DEAD_CONFIG_FILE" ]; then
    echo "    !! 找不到 $DEAD_CONFIG_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$DEAD_CONFIG_FILE" && echo "    死配置清理完成" || echo "    !! 死配置清理失败"
fi

# =============================================================================
# 十三、字典 key 对齐（db/10_dict_key_alignment.sql）
#
# file_type / user_status / menu_type 三个字典的 key 与代码里实际用的值对不上
# （小写 vs 首字母大写、字符串 vs Integer），前端拿到也匹配不上。
# 这一步把 key 改成代码的契约值，并删掉已下线的 menu_type=button。
# =============================================================================
echo
echo "==> 十三、字典 key 对齐（db/10_dict_key_alignment.sql）"
DICT_KEY_FILE="src/main/resources/db/10_dict_key_alignment.sql"
if [ ! -f "$DICT_KEY_FILE" ]; then
    echo "    !! 找不到 $DICT_KEY_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$DICT_KEY_FILE" && echo "    字典 key 对齐完成" || echo "    !! 字典 key 对齐失败"
fi

# =============================================================================
# 十四、storage_type 字典与实现对齐（db/11_storage_type_alignment.sql）
#
# 字典原本是 local/aliyun/qiniu/tencent，而真实可用的是 local/minio：
# aliyun、tencent 是占位实现（调用即抛异常），qiniu/s3 没有实现。
# 前端把 6 个都列出来，配了看起来能用、上传时才失败 —— 这里收敛到真能用的两种。
# =============================================================================
echo
echo "==> 十四、存储类型字典对齐（db/11_storage_type_alignment.sql）"
STORAGE_DICT_FILE="src/main/resources/db/11_storage_type_alignment.sql"
if [ ! -f "$STORAGE_DICT_FILE" ]; then
    echo "    !! 找不到 $STORAGE_DICT_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$STORAGE_DICT_FILE" && echo "    存储类型字典对齐完成" || echo "    !! 存储类型字典对齐失败"
fi

# =============================================================================
# 十五、菜单路由名（db/12_menu_route_name.sql）
#
# vben 的「后端驱动菜单」把后端菜单直接转成 vue-router 路由，需要稳定的 ASCII name。
# 这里补 t_menus.route_name 列与唯一键；菜单数据由第十六节重建。
# =============================================================================
echo
echo "==> 十五、菜单路由名字段（db/12_menu_route_name.sql）"
MENU_ROUTE_FILE="src/main/resources/db/12_menu_route_name.sql"
if [ ! -f "$MENU_ROUTE_FILE" ]; then
    echo "    !! 找不到 $MENU_ROUTE_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$MENU_ROUTE_FILE" && echo "    路由名字段就绪" || echo "    !! 路由名字段创建失败"
fi

# =============================================================================
# 十六、按前端路由清单重建菜单树（db/13_menu_routes_seed.sql）
#
# 旧种子是另一套结构（个人中心/数据统计/评论管理/文档中心），组件路径也对不上真实文件。
# 切到后端驱动菜单后，缺一条菜单就等于少一个页面 —— 必须整树替换。
# =============================================================================
echo
echo "==> 十六、重建菜单树（db/13_menu_routes_seed.sql）"
MENU_SEED_FILE="src/main/resources/db/13_menu_routes_seed.sql"
if [ ! -f "$MENU_SEED_FILE" ]; then
    echo "    !! 找不到 $MENU_SEED_FILE，跳过 —— 后端驱动菜单模式下侧边栏会是空的"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$MENU_SEED_FILE" && echo "    菜单树重建完成" || echo "    !! 菜单树重建失败"
fi

# =============================================================================
# 十七、菜单组件路径改写为 page_key（db/14_menu_page_key.sql）
#
# 菜单里存完整组件路径时，管理员抄错一个字符就是 not-found 且没有任何提示。
# 改成约定 key（user/UserList）后，前端按约定解析，并能从下拉里选、保存前校验。
# =============================================================================
echo
echo "==> 十七、菜单 page_key 改写（db/14_menu_page_key.sql）"
MENU_KEY_FILE="src/main/resources/db/14_menu_page_key.sql"
if [ ! -f "$MENU_KEY_FILE" ]; then
    echo "    !! 找不到 $MENU_KEY_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$MENU_KEY_FILE" && echo "    菜单 page_key 改写完成" || echo "    !! 菜单 page_key 改写失败"
fi

# =============================================================================
# 十八、首页移出菜单表（db/15_remove_dashboard_menu.sql）
#
# 首页改为前端核心路由（core.ts），不再作为一条 hidden 菜单行出现在菜单管理里。
# =============================================================================
echo
echo "==> 十八、首页移出菜单表（db/15_remove_dashboard_menu.sql）"
DASHBOARD_FILE="src/main/resources/db/15_remove_dashboard_menu.sql"
if [ ! -f "$DASHBOARD_FILE" ]; then
    echo "    !! 找不到 $DASHBOARD_FILE，跳过"
else
    "$MYSQL_BIN" --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME"         < "$DASHBOARD_FILE" && echo "    首页已移出菜单表" || echo "    !! 首页移出失败"
fi

# =============================================================================
# 校验
# =============================================================================
echo
echo "==> 校验"
check() {
    local label="$1" sql="$2" expect="$3"
    local got
    got=$(mysql_q "$sql")
    if [ "$got" = "$expect" ]; then
        echo "    OK   $label = $got"
    else
        echo "    ！！ $label = $got（期望 $expect）"
    fi
}

# 「≥」语义的检查：种子数据是 upsert，最终数量取决于库里已有的行，
# 所以这类检查只能卡下限，不能卡等值。
check_min() {
    local label="$1" sql="$2" expect="$3"
    local got
    got=$(mysql_q "$sql")
    if [ "${got:-0}" -ge "$expect" ] 2>/dev/null; then
        echo "    OK   $label = $got（≥$expect）"
    else
        echo "    ！！ $label = $got（期望 ≥$expect）"
    fi
}

check "t_view_history.user_id 列" \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_view_history' AND COLUMN_NAME='user_id'" 1
check "t_posts.slug 列" \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_posts' AND COLUMN_NAME='slug'" 1
check "t_posts.is_sticky 列" \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_posts' AND COLUMN_NAME='is_sticky'" 1
check "t_menu_permissions 已下线（期望 0）" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_menu_permissions'" 0
check "死表残留（期望 0）" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME IN ('t_likes','t_file_logs','t_file_metadata','t_file_permissions',
                          't_file_shares','t_file_thumbnails','t_file_versions','t_network_disk')" 0
# 关键索引清单。idx_perm_identity 不在其中 —— 它是 uk_perm_identity 的左前缀冗余，
# 第五节建完唯一键后会被删掉；留着它会把计数算多 1。
check "关键索引就位（期望 33）" \
    "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA='$DB_NAME' AND INDEX_NAME IN (
         'idx_file_deleted_time','idx_file_creator','idx_file_type','idx_file_path',
         'idx_file_checksum',
         'idx_vh_visitor_time','idx_vh_visitor_post','idx_vh_user_time','idx_vh_post','idx_vh_create_time',
         'idx_comment_post','idx_comment_user','idx_comment_parent',
         'idx_posts_user','idx_posts_status_views','uk_posts_slug',
         'idx_rp_role','idx_rp_permission','idx_perm_active',
         'idx_menus_parent_sort','idx_menus_type','idx_sc_strategy','idx_sc_enable',
         'idx_rgfr_file','idx_ur_role','idx_dt_status',
         'uk_perm_identity','uk_role_permission','uk_role_menu','idx_srm_menu',
         'idx_async_tasks_status_execute',
         'uk_dict_type','uk_dict_type_key')" 34
check "t_async_tasks.execute_at 列" \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_async_tasks' AND COLUMN_NAME='execute_at'" 1
# 文章「访问权限」字典项：三个 key 必须与后端 @Pattern 和 Posts.Visibility 一致，
# 少一个前端单选组就少一个选项。
check "article_visibility 字典项（期望 3）" \
    "SELECT COUNT(*) FROM \`t_sys_dict_items\` di
       JOIN \`t_sys_dict_types\` dt ON dt.\`id\` = di.\`dict_type_id\`
       WHERE dt.\`dict_type\` = 'article_visibility'
         AND di.\`dict_item_key\` IN ('public','private','password')
         AND di.\`is_deleted\` = 0 AND di.\`status\` = 1" 3
# 表不存在时跳过这条（表缺了说明是全新库，t_async_tasks 由 schema.sql 建）
if ! table_missing "t_async_tasks"; then
    check "死任务类型残留（期望 0）" \
        "SELECT COUNT(*) FROM \`t_async_tasks\`
           WHERE \`task_type\` IN ('EMAIL','REPORT','DATA_EXPORT','FILE_PROCESS')" 0
fi
# 文章状态字典：只应有 0..3 四个数字键。旧的 draft/published/... 若还在，
# 说明迁移没生效，前端的状态筛选会重新坏掉。
check "article_status 字典项（期望 4）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'article_status'
         AND di.`dict_item_key` IN ('0','1','2','3')
         AND di.`is_deleted` = 0 AND di.`status` = 1" 4
check "article_status 旧字符串键残留（期望 0）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'article_status'
         AND di.`dict_item_key` IN ('draft','published','pending','offline')" 0
check "todo_priority 字典项（期望 3）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'todo_priority'
         AND di.`dict_item_key` IN ('high','medium','low')
         AND di.`is_deleted` = 0 AND di.`status` = 1" 3

# 死配置：与真实配置来源冲突的那几组必须清空，否则管理员会以为改了生效
check "死配置残留（期望 0）" \
    "SELECT COUNT(*) FROM `t_sys_config`
       WHERE `config_key` LIKE 'system.storage.%'
          OR `config_key` LIKE 'system.cache.%'
          OR `config_key` LIKE 'security.jwt.%'
          OR `config_key` LIKE 'security.login.%'
          OR `config_key` LIKE 'security.password.%'" 0

check "t_menus.route_name 列（期望 1）" \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_menus' AND COLUMN_NAME='route_name'" 1
check "t_menus 路由名唯一键（期望 1）" \
    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_menus' AND INDEX_NAME='uk_menus_route_name'" 1
# 后端驱动菜单模式下，每条可见菜单都必须有路由名 —— 缺一条就等于少一个页面
check "菜单缺路由名（期望 0）" \
    "SELECT COUNT(*) FROM `t_menus` WHERE `is_deleted` = 0 AND (`route_name` IS NULL OR `route_name` = '')" 0
check "可见菜单数量（期望 >= 25）" \
    "SELECT IF(COUNT(*) >= 25, 1, 0) FROM `t_menus` WHERE `is_deleted` = 0" 1
check "ADMIN 菜单绑定（期望 >= 25）" \
    "SELECT IF(COUNT(*) >= 25, 1, 0) FROM `t_sys_role_menus` rm
       JOIN `t_roles` r ON r.`id` = rm.`role_id`
       WHERE r.`code` = 'ADMIN' AND rm.`is_deleted` = 0" 1

# 菜单 component 必须是 page_key 或显式 .vue 路径：
# 不含 .vue 的值只允许字母/数字/下划线/斜杠
check "菜单 component 格式非法（期望 0）" \
    "SELECT COUNT(*) FROM `t_menus`
       WHERE `is_deleted` = 0
         AND `component` IS NOT NULL AND `component` <> ''
         AND `component` NOT LIKE '%.vue'
         AND `component` REGEXP '[^A-Za-z0-9_/-]'" 0
check "菜单 component 仍是完整 features 路径（期望 0）" \
    "SELECT COUNT(*) FROM `t_menus` WHERE `component` LIKE 'views/features/%'" 0

# 首页不再作为菜单行存在（它已是前端核心路由）
check "首页菜单行残留（期望 0）" \
    "SELECT COUNT(*) FROM `t_menus` WHERE `is_deleted` = 0 AND `route_name` = 'Dashboard'" 0

check "t_posts 冗余索引残留（期望 0）" \
    "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='t_posts'
         AND INDEX_NAME IN ('idx_user','idx_user_id')" 0
# 字典 key 与代码契约对齐：file_type 大写、user_status/menu_type 用数值
check "file_type 字典项（期望 5）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'file_type'
         AND di.`dict_item_key` IN ('Image','Video','Audio','Document','other')
         AND di.`is_deleted` = 0 AND di.`status` = 1" 5
check "user_status 字典项（期望 3）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'user_status'
         AND di.`dict_item_key` IN ('0','1','2')
         AND di.`is_deleted` = 0 AND di.`status` = 1" 3
check "menu_type 字典项（期望 2，button 已下线）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'menu_type'
         AND di.`dict_item_key` IN ('0','1')
         AND di.`is_deleted` = 0 AND di.`status` = 1" 2

# 存储类型字典只保留真能用的两种（local / minio）
check "storage_type 字典项（期望 2）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'storage_type'
         AND di.`dict_item_key` IN ('local','minio')
         AND di.`is_deleted` = 0 AND di.`status` = 1" 2
check "storage_type 未实现策略残留（期望 0）" \
    "SELECT COUNT(*) FROM `t_sys_dict_items` di
       JOIN `t_sys_dict_types` dt ON dt.`id` = di.`dict_type_id`
       WHERE dt.`dict_type` = 'storage_type'
         AND di.`dict_item_key` IN ('aliyun','tencent','qiniu','s3')" 0

check "t_sys_role_menus 审计字段（期望 3）" \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_sys_role_menus'
         AND COLUMN_NAME IN ('create_time','update_time','is_deleted')" 3
check "permission identity 唯一键（期望 1）" \
    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_permissions' AND INDEX_NAME='uk_perm_identity'" 1
check "role_permission 唯一键（期望 1）" \
    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_role_permissions' AND INDEX_NAME='uk_role_permission'" 1
check "role_menu 唯一键（期望 1）" \
    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_sys_role_menus' AND INDEX_NAME='uk_role_menu'" 1
check "role code 唯一键（期望 1）" \
    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DB_NAME'
       AND TABLE_NAME='t_roles' AND INDEX_NAME='uk_code'" 1
check_min "角色数（期望 ≥2）" \
    "SELECT COUNT(*) FROM \`t_roles\` WHERE \`is_deleted\` = 0" 2
# 种子脚本 upsert 51 条权限，其中 14 条是开发库里没有的新 identity
# （user:status / user:assign / user:manage / role:status / menu:assign
#   + permission:view/add/edit/remove + monitor:stats:view
#   + system:file:upload/view/edit/remove），
# 同时删掉垃圾数据 system:role:tttt。
check_min "权限数（期望 ≥51）" \
    "SELECT COUNT(*) FROM \`t_permissions\` WHERE \`is_deleted\` = 0" 51
check "默认角色数（期望 1）" \
    "SELECT COUNT(*) FROM \`t_roles\` WHERE \`is_default\` = 1 AND \`is_deleted\` = 0" 1
# ADMIN 拿到全部 24 个菜单，USER 拿到「个人中心」子树的 3 个 → 27
check_min "角色-菜单绑定数（期望 ≥27）" \
    "SELECT COUNT(*) FROM \`t_sys_role_menus\` WHERE \`is_deleted\` = 0" 27
# RBAC 模型要求可用、但初始种子里被写成 is_active = 0 的那批权限。
# 只要还有一条停用，对应的 @SaCheckPermission 端点就对所有人 403（含管理员）。
check "关键权限全部启用（停用数期望 0）" \
    "SELECT COUNT(*) FROM \`t_permissions\`
       WHERE \`is_deleted\` = 0 AND \`is_active\` = 0
         AND \`identity\` IN (
           'system:config:view','system:config:edit',
           'system:storage:view','system:storage:edit',
           'comment:view','comment:reply','comment:approve','comment:remove',
           'content:category:view','content:category:add',
           'content:tag:view','content:tag:edit',
           'monitor:log:view','monitor:log:clean',
           'monitor:online:view','monitor:online:logout','monitor:server:view',
           'monitor:stats:view',
           'system:file:upload','system:file:view',
           'system:file:edit','system:file:remove')" 0
check "system:config:* / system:storage:* 已存在（期望 4）" \
    "SELECT COUNT(*) FROM \`t_permissions\`
       WHERE \`identity\` IN ('system:config:view','system:config:edit',
                              'system:storage:view','system:storage:edit')
         AND \`is_deleted\` = 0 AND \`is_active\` = 1" 4
check "monitor:stats:view 已存在（期望 1）" \
    "SELECT COUNT(*) FROM \`t_permissions\`
       WHERE \`identity\` = 'monitor:stats:view'
         AND \`is_deleted\` = 0 AND \`is_active\` = 1" 1
check "system:file:* 已存在且启用（期望 4）" \
    "SELECT COUNT(*) FROM \`t_permissions\`
       WHERE \`identity\` IN ('system:file:upload','system:file:view',
                              'system:file:edit','system:file:remove')
         AND \`is_deleted\` = 0 AND \`is_active\` = 1" 4
check "USER 持有文件上传/查看（期望 2）" \
    "SELECT COUNT(DISTINCT p.\`identity\`)
       FROM \`t_role_permissions\` rp
       JOIN \`t_roles\` r ON r.\`id\` = rp.\`role_id\`
       JOIN \`t_permissions\` p ON p.\`id\` = rp.\`permission_id\`
       WHERE r.\`code\` = 'USER' AND rp.\`is_deleted\` = 0
         AND p.\`identity\` IN ('system:file:upload','system:file:view')" 2
check "ADMIN 持有关键权限（期望 ≥22）" \
    "SELECT COUNT(DISTINCT p.\`identity\`)
       FROM \`t_role_permissions\` rp
       JOIN \`t_roles\` r ON r.\`id\` = rp.\`role_id\`
       JOIN \`t_permissions\` p ON p.\`id\` = rp.\`permission_id\`
       WHERE r.\`code\` = 'ADMIN' AND rp.\`is_deleted\` = 0
         AND p.\`identity\` IN (
           'system:config:view','system:config:edit',
           'system:storage:view','system:storage:edit',
           'comment:view','comment:reply','comment:approve','comment:remove',
           'content:category:view','content:category:add',
           'content:tag:view','content:tag:edit',
           'monitor:log:view','monitor:log:clean',
           'monitor:online:view','monitor:online:logout','monitor:server:view',
           'monitor:stats:view',
           'system:file:upload','system:file:view',
           'system:file:edit','system:file:remove')" 22

echo
echo "==> 全部完成（备份: target/db_backup_before_migration.sql）"
