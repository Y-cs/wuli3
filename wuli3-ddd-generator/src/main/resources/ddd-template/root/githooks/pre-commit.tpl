#!/usr/bin/env bash
#
# 提交前钩子：格式化暂存文件，并执行不依赖编译的快速静态检查。
# 单元测试、Error Prone、NullAway 和架构门禁由 ./gradlew check 承担，放在 CI。
#
# 使用方式：git config core.hooksPath .githooks
# 跳过本次校验：git commit --no-verify

set -euo pipefail

staged="$(git diff --cached --name-only --diff-filter=ACMR)"
[ -n "$staged" ] || exit 0

# 同一文件既有暂存又有未暂存改动时，格式化后无法只重新暂存其中一部分，要求先归拢。
partial="$(git diff --name-only | grep -Fxf <(printf '%s\n' "$staged") || true)"
if [ -n "$partial" ]; then
    echo "以下文件同时存在暂存与未暂存改动，请先 git add 后再提交：" >&2
    printf '%s\n' "$partial" >&2
    exit 1
fi

./gradlew spotlessApply --quiet

# spotlessApply 就地改写文件，需把本次暂存的文件重新加入索引，保证提交的是格式化后的内容。
printf '%s\n' "$staged" | while IFS= read -r path; do
    [ -e "$path" ] || continue
    git add -- "$path"
done

./gradlew forbiddenApis --quiet
