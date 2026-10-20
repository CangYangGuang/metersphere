#!/bin/sh
# 镜像构建期模块合并/去重（版本字符串无关）：
#   本地构建的模块 JAR（NEW_DIR）按 artifact 覆盖基础镜像 lib（LIB_DIR）中的同 artifact JAR，
#   无论双方版本字符串是否相同（本仓库本地构建为 -3.x，基础镜像 stock 为 -3.6.8-lts）。
# 目的：消除同 artifact 多版本并存，避免
#   - MyBatis "XML fragments parsed from previous mappers"
#   - Spring 组件/控制器映射异常
# 规则：
#   1. 对 NEW_DIR 中的每个 JAR，按 artifact 删除 LIB_DIR 中同 artifact 的所有版本，再放入本地版本；
#   2. 基础镜像独有、本地未构建的模块（如 general-xpack-impl）保持不动；
#   3. 打印最终模块 JAR 清单与重复检测结果；
#   4. 合并后若仍存在同 artifact 多版本并存，使构建失败（exit 1）。
set -eu

LIB_DIR="${1:-/metersphere/lib}"
NEW_DIR="${2:-/tmp/newlib}"
MODULE_FILTER='^(metersphere-|general-)'

artifact_of() { basename "$1" | sed -E 's/-[0-9].*\.jar$//'; }

echo "==================== base module jars (LIB_DIR) BEFORE merge ===================="
ls -1 "$LIB_DIR" | grep -E "$MODULE_FILTER" | sort || true
echo "==================== local module jars (NEW_DIR) ================================"
ls -1 "$NEW_DIR" | grep -E "$MODULE_FILTER" | sort || true
echo "================================================================================"

for nj in "$NEW_DIR"/*.jar; do
  [ -e "$nj" ] || continue
  fn="$(basename "$nj")"
  art="$(artifact_of "$nj")"
  for ex in "$LIB_DIR/$art"-*.jar; do
    [ -e "$ex" ] || continue
    exfn="$(basename "$ex")"
    exart="$(artifact_of "$ex")"
    if [ "$exart" = "$art" ] && [ "$exfn" != "$fn" ]; then
      echo "[merge] remove base jar: $exfn   (replaced by local $fn)"
      rm -f "$ex"
    fi
  done
done

cp -f "$NEW_DIR"/*.jar "$LIB_DIR/"
rm -rf "$NEW_DIR"

echo "==================== module jars AFTER merge ===================="
ls -1 "$LIB_DIR" | grep -E "$MODULE_FILTER" | sort || true
echo "================================================================"

DUP_MODULES="$(ls -1 "$LIB_DIR" | grep -E "$MODULE_FILTER" | sed -E 's/-[0-9].*\.jar$//' | sort | uniq -d || true)"
if [ -n "$DUP_MODULES" ]; then
  echo "ERROR: duplicate module artifact versions detected in $LIB_DIR:" >&2
  for a in $DUP_MODULES; do
    echo "  artifact: $a" >&2
    ls -1 "$LIB_DIR" | grep -E "^${a}-[0-9]" | sed 's/^/    - /' >&2
  done
  exit 1
fi

ALL_DUP="$(ls -1 "$LIB_DIR"/*.jar | xargs -n1 basename | sed -E 's/-[0-9].*\.jar$//' | sort | uniq -d || true)"
if [ -n "$ALL_DUP" ]; then
  echo "WARN: possible multi-version (third-party) artifacts (not blocking):"
  for a in $ALL_DUP; do
    echo "  artifact: $a"
    ls -1 "$LIB_DIR" | grep -E "^${a}-[0-9]" | sed 's/^/    - /'
  done
fi

echo "OK: local module jars merged; no duplicate project module artifacts in $LIB_DIR"
