#!/usr/bin/env bash
#===============================================================================
# spark-udf-lp 集群 UAT（L3 层，参考 scripts/spark/spark_sql_uat.sh 模式）
#
# 用法:
#   bash scripts/spark_udf_uat.sh <VER> [SPARK_HOST=hivespark03]
#
# 覆盖:
#   L3.1 注册与冒烟（SHOW FUNCTIONS + SELECT）
#   L3.2 功能矩阵（null / 空串 / 短值 / 中文 / 数字）
#   L3.3 分布式执行提示（大表 GROUP BY 验证 executor 加载 jar）
#   L3.4 持久性提示（STS 重启后函数仍在）
#
# 注意: L3.3/L3.4 需人工配合（Spark UI 观察 executors / 重启 STS），脚本给出指引。
#===============================================================================
set -euo pipefail

LP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VER="${1:?usage: bash scripts/spark_udf_uat.sh <VER> [SPARK_HOST]}"
HOST="${2:-hivespark03}"
BEELINE_URL="jdbc:hive2://${HOST}:10015/default"
SQL_DIR="${LP_DIR}/scripts/uat"

[ -d "${SQL_DIR}" ] || { echo "ERROR: ${SQL_DIR} 不存在"; exit 1; }

echo "===== spark-udf-lp UAT VER=${VER} HOST=${HOST} ====="
echo "STS: ${BEELINE_URL}"

echo ""
echo "[L3.1] 注册与冒烟"
beeline -u "${BEELINE_URL}" -e "SHOW FUNCTIONS LIKE 'udf_%'; SELECT udf_prefix('helloworld');" || echo "FAIL: L3.1"

echo ""
echo "[L3.2] 功能矩阵（SQL 文件: ${SQL_DIR}/）"
for f in "${SQL_DIR}"/l32_*.sql; do
  [ -e "$f" ] || continue
  echo "  -- $(basename "$f")"
  beeline -u "${BEELINE_URL}" -f "$f" || echo "FAIL: $(basename "$f")"
done

echo ""
echo "[L3.3] 分布式执行（人工确认）"
echo "  在 beeline/Spark UI 执行并观察:"
echo "    SELECT count(*), udf_prefix(cast(id as string)) FROM tmp_udf_big GROUP BY udf_prefix(cast(id as string));"
echo "  判定: Spark UI (${HOST}:4040) 该 SQL executors 数 > 1，日志无 ClassNotFoundException"

echo ""
echo "[L3.4] 持久性（人工确认）"
echo "  docker restart spark 后重连 STS，SHOW FUNCTIONS LIKE 'udf_%' 仍可见 udf_prefix"

echo ""
echo "===== UAT 结束，结果汇总见 docs/uat/spark_udf_uat_report_YYYYMMDD.md ====="
