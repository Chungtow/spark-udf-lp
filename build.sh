#!/usr/bin/env bash
#===============================================================================
# spark-udf-lp 容器化构建入口（对齐 dataagent-lp/build.sh，无前端）
#
# 用法:
#   bash build.sh [VER]      # VER 默认 1.0.0，产物 target/spark-udf-lp-<VER>.jar
#
# 前置:
#   docker 可用；网络可访问阿里云 maven 镜像（builder/settings.xml）
#
# 流程:
#   [1/3] docker build builder 镜像（maven:3.8.7-openjdk-8）
#   [2/3] docker run 挂载源码 + .m2 缓存卷 + settings.xml，执行 mvn package（含单测）
#   [3/3] 产物检查 target/spark-udf-lp-<VER>.jar
#===============================================================================
set -euo pipefail

LP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILDER_IMAGE="${BUILDER_IMAGE:-spark-udf-builder:1.0}"
VER="${1:-1.0.0}"
M2_CACHE_VOL="${M2_CACHE_VOL:-spark-udf-m2}"
SETTINGS="${LP_DIR}/builder/settings.xml"

echo "[1/3] 构建 builder 镜像: ${BUILDER_IMAGE}"
docker build -t "${BUILDER_IMAGE}" -f "${LP_DIR}/builder/Dockerfile" "${LP_DIR}/builder"

echo "[2/3] 容器化构建（单测 + 打包）VER=${VER}"
docker run --rm \
  -v "${LP_DIR}:/workspace/src" \
  -v "${M2_CACHE_VOL}:/workspace/.m2-cache" \
  -v "${SETTINGS}:/workspace/settings.xml:ro" \
  --user "$(id -u):$(id -g)" \
  -w /workspace/src \
  "${BUILDER_IMAGE}" \
  mvn clean package -Drevision="${VER}" \
    -s /workspace/settings.xml \
    -Dmaven.repo.local=/workspace/.m2-cache

echo "[3/3] 产物检查"
JAR="${LP_DIR}/target/spark-udf-lp-${VER}.jar"
if [ ! -f "${JAR}" ]; then
  echo "ERROR: ${JAR} 未生成"
  exit 1
fi
echo "OK: ${JAR} ($(du -h "${JAR}" | awk '{print $1}'))"

echo ""
echo "构建完成：target/spark-udf-lp-${VER}.jar 已生成。"
echo "后续流程（发布/部署/UAT）统一见 .agents/skills/spark-udf-dev/SKILL.md。"
