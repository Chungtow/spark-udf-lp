#!/usr/bin/env bash
#===============================================================================
# spark-udf-lp 制品入库：target/spark-udf-lp-<VER>.jar → 父项目 software/spark-udf/
#   + sha256 校验文件 + manifest.txt（VER/SHA256/构建时间）
#
# 用法:
#   bash scripts/release_spark_udf_lp.sh <VER> [--force]
#
# 注意:
#   - 本脚本会在父仓库 software/spark-udf/ 下生成制品文件（不执行任何 git 操作）
#   - 父仓库 submodule 指针的更新由开发者自行 commit（遵守主项目规则）
#===============================================================================
set -euo pipefail

LP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "${LP_DIR}/.." && pwd)"

VER="${1:?usage: bash scripts/release_spark_udf_lp.sh <VER> [--force]}"
FORCE="${2:-}"

SRC="${LP_DIR}/target/spark-udf-lp-${VER}.jar"
DST_DIR="${REPO_ROOT}/software/spark-udf"
DST="${DST_DIR}/spark-udf-lp-${VER}.jar"

[ -f "${SRC}" ] || { echo "ERROR: ${SRC} 不存在，请先 bash build.sh ${VER}"; exit 1; }
if [ -f "${DST}" ] && [ "${FORCE}" != "--force" ]; then
  echo "ERROR: ${DST} 已存在（使用 --force 覆盖）"
  exit 1
fi

mkdir -p "${DST_DIR}"
sha256sum "${SRC}" > "${DST}.sha256"
cp "${SRC}" "${DST}"
{
  echo "VER=${VER}"
  echo "SHA256=$(awk '{print $1}' "${DST}.sha256")"
  echo "BUILD_TIME=$(date '+%Y-%m-%d %H:%M:%S')"
} > "${DST_DIR}/manifest.txt"

echo "制品已入库:"
echo "  ${DST}"
echo "  ${DST}.sha256"
echo "  ${DST_DIR}/manifest.txt"
