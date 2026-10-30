#!/usr/bin/env bash
# 编译校验脚本
#
# 优先用 mvn（会跑 MapStruct/Lombok 注解处理器，最接近真实构建）；
# mvn 不可用时退回到 javac 直接编译全部源码。
#
# 注意：不要用 mvn clean —— IntelliJ 里正有应用在跑并持有 target/classes，
# clean 会失败并把运行中的类抽走。
#
# 编译失败时以非零码退出（供 run-all.sh 判断）。
set -u
cd "$(dirname "$0")"

MVN="/d/idea/IntelliJ IDEA 2026.1/plugins/maven/lib/maven3/bin/mvn"
LOG="target/compile-check.log"

if [ -x "$MVN" ]; then
    echo "==> 使用 mvn 编译"
    mkdir -p target
    MAVEN_OPTS="${MAVEN_OPTS:-} -Dfile.encoding=UTF-8" \
        "$MVN" -B -o compile -DskipTests > "$LOG" 2>&1
    rc=$?
    # 日志统一成 UTF-8 才能直接阅读/grep。
    # MAVEN_OPTS 里指定了 -Dfile.encoding=UTF-8，输出通常已经是 UTF-8；
    # 但如果 JVM 没采纳，就还是 GBK —— 先探测是不是合法 UTF-8，不是再转。
    if [ -f "$LOG" ]; then
        if ! iconv -f UTF-8 -t UTF-8 "$LOG" > /dev/null 2>&1; then
            iconv -f GBK -t UTF-8 -c "$LOG" > "$LOG.utf8" 2>/dev/null && mv "$LOG.utf8" "$LOG"
        fi
    fi
    grep -E "\[ERROR\]|BUILD SUCCESS|BUILD FAILURE" "$LOG" | head -60
    # 离线构建最常见的失败：某个版本没进过本地 .m2
    if grep -q "Could not resolve dependencies" "$LOG"; then
        echo
        echo "!! 依赖解析失败（离线模式）。上面的 artifact 说明该版本没进过本地 .m2："
        echo "!!   · 改成本地已有的版本，或"
        echo "!!   · 去掉 -o 参数联网下载（mvn -B compile -DskipTests）"
    fi
    echo "=== COMPILE CHECK DONE (mvn, exit=$rc) ==="
    echo "=== 完整日志: $LOG ==="
    exit $rc
fi

echo "==> mvn 不可用，退回 javac"
if [ ! -f target/cp.txt ]; then
    echo "target/cp.txt 不存在，尝试用 mvn 生成依赖 classpath"
    "$MVN" -B dependency:build-classpath -Dmdep.outputFile=target/cp.txt -q || {
        echo "生成 classpath 失败，请先执行：mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt"
        exit 1
    }
fi

CP="$(cat target/cp.txt)"
find src/main/java -name "*.java" > target/srcs.txt
mkdir -p target/javac-out
javac -nowarn -encoding UTF-8 -proc:full \
      -cp "target/classes;$CP" \
      -processorpath "$CP" \
      -d target/javac-out @target/srcs.txt > "$LOG" 2>&1
rc=$?
grep -E "error|错误" "$LOG" | head -60
echo "=== COMPILE CHECK DONE (javac, exit=$rc) ==="
exit $rc
