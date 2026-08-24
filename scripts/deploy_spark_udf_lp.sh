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
#   bash scripts/deploy_spark_udf_lp.sh <VER> [SPARK_HOST=hivespark03] [DB_SCOPE=default|ALL]
#   - 默认只注册到 default 库（其他库需 default.udf_prefix 全限定名调用）
#   - 传 ALL 则遍历 SHOW DATABASES 逐库注册，所有库无前缀可直接调用
#     （新库出现后重跑本脚本即可，CREATE OR REPLACE 幂等）
#
# 依赖:
#   - 可 ssh 到 SPARK_HOST，且其上 spark 容器可用 hdfs / beeline 命令
#   - 若 spark 容器内无 hdfs 命令，可先用宿主机 hadoop fs -put 上传（见 §5 计划）
#===============================================================================
set -euo pipefail

LP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VER="${1:?usage: bash scripts/deploy_spark_udf_lp.sh <VER> [SPARK_HOST] [DB_SCOPE]}"
HOST="${2:-hivespark03}"
DB_SCOPE="${3:-default}"
JAR="spark-udf-lp-${VER}.jar"
HDFS_DIR="/udf"
HDFS_URL="hdfs://mycluster"
MANIFEST="${LP_DIR}/scripts/udf-manifest.txt"
BEELINE="docker exec -i spark /opt/spark/bin/beeline -u jdbc:hive2://localhost:10015/default -n root --silent=true"

SRC="${LP_DIR}/target/${JAR}"
[ -f "${SRC}" ] || { echo "ERROR: ${SRC} 不存在，请先 bash build.sh ${VER}"; exit 1; }
[ -f "${MANIFEST}" ] || { echo "ERROR: ${MANIFEST} 不存在"; exit 1; }

echo "[1/3] 上传 HDFS ${HDFS_URL}${HDFS_DIR}/${JAR}"
# 注意: scp 目标为宿主机 /tmp；hdfs 命令在 spark 容器内执行，需 docker cp 进容器
scp "${SRC}" "${HOST}:/tmp/${JAR}"
ssh "${HOST}" "docker cp /tmp/${JAR} spark:/tmp/ && docker exec spark hdfs dfs -mkdir -p ${HDFS_DIR} && docker exec spark hdfs dfs -put -f /tmp/${JAR} ${HDFS_DIR}/${JAR} && docker exec spark hdfs dfs -ls ${HDFS_DIR}/${JAR}"
ssh "${HOST}" "rm -f /tmp/${JAR}; docker exec spark rm -f /tmp/${JAR}"

# 确定注册库列表
if [ "${DB_SCOPE}" = "ALL" ]; then
  echo "[2/3] 注册 Hive 永久函数（清单 ${MANIFEST}，范围=全部库）"
  DBS="$(ssh "${HOST}" "${BEELINE} -e 'SHOW DATABASES'" 2>/dev/null | sed -n 's/^| *\([^| ]*\) *|$/\1/p' | grep -vxE '^$|^namespace$')"
  [ -n "${DBS}" ] || { echo "ERROR: SHOW DATABASES 无结果，请检查 STS"; exit 1; }
  echo "  目标库: $(echo ${DBS} | tr '\n' ' ')"
else
  DBS="${DB_SCOPE}"
  echo "[2/3] 注册 Hive 永久函数（清单 ${MANIFEST}，范围=${DB_SCOPE}）"
fi

for db in ${DBS}; do
  while IFS='|' read -r fname fclass; do
    case "${fname}" in ""|\#*) continue ;; esac
    # default 库注册为无前缀名（兼容原用法），其他库注册为 <db>.<name>
    if [ "${db}" = "default" ]; then fqname="${fname}"; else fqname="${db}.${fname}"; fi
    echo "  -> ${db}: ${fqname} AS ${fclass}"
    ssh "${HOST}" "${BEELINE} -e \"CREATE OR REPLACE FUNCTION ${fqname} AS '${fclass}' USING JAR '${HDFS_URL}${HDFS_DIR}/${JAR}';\"" 2>&1 | grep -vE 'WARNING|log4j|slf4j|load-spark-env|^$' | tail -3
  done < "${MANIFEST}"
done

echo "[3/3] 注册完成，STS 冒烟验证命令:"
cat <<EOF
  beeline -u jdbc:hive2://${HOST}:10015/default \\
    -e "SHOW FUNCTIONS LIKE 'udf_prefix'; SELECT udf_prefix('helloworld');"
  其他库: beeline -u jdbc:hive2://${HOST}:<库名> \\
    -e "SELECT udf_prefix('helloworld');"
  # 注意: Spark 3.3 永久函数显示为 <库>.<注册名>，SHOW LIKE 需用精确名
  完整 UAT 用例见: bash scripts/spark_udf_uat.sh ${VER} ${HOST}
EOF
