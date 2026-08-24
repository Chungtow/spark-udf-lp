#!/usr/bin/env bash
#===============================================================================
# spark-udf-lp 发布 + 注册：
#   ① jar 上传 HDFS /udf/spark-udf-lp-<VER>.jar（版本化，不可覆盖）
#   ② 按 scripts/udf-manifest.txt 遍历注册 Hive 永久函数
#      CREATE OR REPLACE FUNCTION <注册名> AS '<类名>'
#        USING JAR 'hdfs://mycluster/udf/spark-udf-lp-<VER>.jar'
#   ③ 提示验证命令（STS 冒烟）
#
# 用法:
#   bash scripts/deploy_spark_udf_lp.sh <VER> [SPARK_HOST=hivespark03]
#
# 依赖:
#   - 可 ssh 到 SPARK_HOST，且其上 spark 容器可用 hdfs / spark-sql 命令
#   - 若 spark 容器内无 hdfs 命令，可先用宿主机 hadoop fs -put 上传（见 §5 计划）
#===============================================================================
set -euo pipefail

LP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VER="${1:?usage: bash scripts/deploy_spark_udf_lp.sh <VER> [SPARK_HOST]}"
HOST="${2:-hivespark03}"
JAR="spark-udf-lp-${VER}.jar"
HDFS_DIR="/udf"
HDFS_URL="hdfs://mycluster"
MANIFEST="${LP_DIR}/scripts/udf-manifest.txt"

SRC="${LP_DIR}/target/${JAR}"
[ -f "${SRC}" ] || { echo "ERROR: ${SRC} 不存在，请先 bash build.sh ${VER}"; exit 1; }
[ -f "${MANIFEST}" ] || { echo "ERROR: ${MANIFEST} 不存在"; exit 1; }

echo "[1/3] 上传 HDFS ${HDFS_URL}${HDFS_DIR}/${JAR}"
# 注意: scp 目标为宿主机 /tmp；hdfs 命令在 spark 容器内执行，需 docker cp 进容器
scp "${SRC}" "${HOST}:/tmp/${JAR}"
ssh "${HOST}" "docker cp /tmp/${JAR} spark:/tmp/ && docker exec spark hdfs dfs -mkdir -p ${HDFS_DIR} && docker exec spark hdfs dfs -put -f /tmp/${JAR} ${HDFS_DIR}/${JAR} && docker exec spark hdfs dfs -ls ${HDFS_DIR}/${JAR}"
ssh "${HOST}" "rm -f /tmp/${JAR}; docker exec spark rm -f /tmp/${JAR}"

echo "[2/3] 注册 Hive 永久函数（清单 ${MANIFEST}，beeline 连已运行的 STS）"
while IFS='|' read -r fname fclass; do
  case "${fname}" in ""|\#*) continue ;; esac
  echo "  -> ${fname} AS ${fclass}"
  ssh "${HOST}" "docker exec -i spark /opt/spark/bin/beeline -u 'jdbc:hive2://localhost:10015/default' -n root --silent=true -e \"CREATE OR REPLACE FUNCTION ${fname} AS '${fclass}' USING JAR '${HDFS_URL}${HDFS_DIR}/${JAR}';\"" 2>&1 | grep -vE 'WARNING|log4j|slf4j|load-spark-env|^$' | tail -5
done < "${MANIFEST}"

echo "[3/3] 注册完成，STS 冒烟验证命令:"
cat <<EOF
  beeline -u jdbc:hive2://${HOST}:10015/default \\
    -e "SHOW FUNCTIONS LIKE 'udf_prefix'; SELECT udf_prefix('helloworld');"
  # 注意: Spark 3.3 永久函数显示为 default.<注册名>，SHOW LIKE 需用精确名
  完整 UAT 用例见: bash scripts/spark_udf_uat.sh ${VER} ${HOST}
EOF
