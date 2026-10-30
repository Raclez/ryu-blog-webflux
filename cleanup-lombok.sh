#!/usr/bin/env bash
# 清理 Lombok 的 @EqualsAndHashCode 警告
#
# 22 个实体类写着 @EqualsAndHashCode(callSuper = true, of = {"id"})，
# 但 "id" 是父类 BaseEntity 的字段，本类里并不存在 —— Lombok 会警告
# "This field does not exist" 并把 of 列表当成空集忽略掉。
#
# 而 callSuper = true 本来就已经把父类字段（含 id）算进 equals/hashCode，
# 所以去掉 of 参数后行为完全一致，只是没有警告了。幂等，可重复执行。
set -u
cd "$(dirname "$0")"

sed -i 's/@EqualsAndHashCode(callSuper = true, of = {"id"})/@EqualsAndHashCode(callSuper = true)/' \
    src/main/java/com/ryu/blog/entity/*.java

remaining=$(grep -rl 'of = {"id"}' src/main/java/com/ryu/blog/entity/ 2>/dev/null | wc -l)
echo "LOMBOK-CLEANED: 剩余 $remaining 个"
