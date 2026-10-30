#!/usr/bin/env bash
# 删除已确认零引用的死代码
#
# -f: 这些文件里有本地改动（例如把 unmappedTargetPolicy 从 IGNORE 改成 WARN），
#      不加 -f 的话 git rm 会拒绝删除并报 "has local modifications"。
#      它们本来就要删掉，改动随之丢弃是预期行为。
# --ignore-unmatch: 允许重复执行（已经删掉的文件不再报错）。
set -u
cd "$(dirname "$0")"
git rm -q -f --ignore-unmatch \
  src/main/java/com/ryu/blog/utils/QrCodeUtil.java \
  src/main/java/com/ryu/blog/utils/ImageUtils.java \
  src/main/java/com/ryu/blog/utils/CaptchaUtil.java \
  src/main/java/com/ryu/blog/utils/CryptoUtil.java \
  src/main/java/com/ryu/blog/utils/CacheUtil.java \
  src/main/java/com/ryu/blog/strategy/StorageStrategyFactory.java \
  src/main/java/com/ryu/blog/exception/ValidatorExceptionHandler.java \
  src/main/java/com/ryu/blog/exception/ExceptionUtils.java \
  src/main/java/com/ryu/blog/enums/RefinementOperation.java \
  src/main/java/com/ryu/blog/enums/ContentTone.java \
  src/main/java/com/ryu/blog/enums/ContentStyle.java \
  src/main/java/com/ryu/blog/constant/PermissionConstants.java \
  src/main/java/com/ryu/blog/constant/RegexConstants.java \
  src/main/java/com/ryu/blog/vo/PostStatisticsVO.java \
  src/main/java/com/ryu/blog/vo/PermissionMenusVO.java \
  src/main/java/com/ryu/blog/vo/PermissionsVO.java \
  src/main/java/com/ryu/blog/vo/MenusVO.java \
  src/main/java/com/ryu/blog/vo/GCInfoVO.java \
  src/main/java/com/ryu/blog/dto/SystemConfigListDTO.java \
  src/main/java/com/ryu/blog/dto/MenuTreeNodeDTO.java \
  src/main/java/com/ryu/blog/dto/AiRefinementRequest.java \
  src/main/java/com/ryu/blog/mapper/CommentMapper.java \
  src/main/java/com/ryu/blog/vo/CommentVO.java \
  src/main/java/com/ryu/blog/vo/CommentTreeVO.java
echo "DELETED $(git status --short | grep -c '^D')"
