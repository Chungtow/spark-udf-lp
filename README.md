# spark-udf-lp

Spark 自定义 UDF（Hive 风格 `GenericUDF`）新项目。构建产物（jar）发布到集群 HDFS `/udf/` 并注册为 **Hive 永久函数**（Hive Metastore），任意 Spark ThriftServer（STS）会话可直接调用。

## 版本基线

| 组件 | 版本 |
|------|------|
| Spark | 3.3.1-bin-hadoop3（hivespark03 spark 容器） |
| Hadoop | 3.1.4 |
| Java | 8（编译与运行时统一，builder 镜像 `maven:3.9-eclipse-temurin-8`） |
| Hive 兼容层 | 2.3.9（与 Spark 3.3.1 内置一致，provided） |

## 目录结构

```
├── pom.xml                    # Java 8；spark-sql/hive-exec/hadoop-client 均 provided
├── src/main/java/com/chungtow/udf/   # UDF 源码（继承 GenericUDF）
├── src/test/java/com/chungtow/udf/   # JUnit 单测（构建闸门）
├── builder/                   # 构建镜像（maven:3.9-eclipse-temurin-8）+ settings.xml（阿里云源）
├── build.sh                   # 容器化构建入口
└── scripts/
    ├── udf-manifest.txt       # UDF 注册清单：注册名|类名
    ├── release_spark_udf_lp.sh # 制品入库 → 父项目 software/spark-udf/
    ├── deploy_spark_udf_lp.sh  # HDFS 上传 + 永久函数注册
    ├── spark_udf_uat.sh        # 集群 UAT（L3）
    └── uat/                    # UAT SQL 用例
```

## 快速开始

```bash
# 1. 构建（builder 容器内跑单测 + 打包）
bash build.sh 1.0.0                          # 产物 target/spark-udf-lp-1.0.0.jar

# 2. 制品入库（写入父仓库 software/spark-udf/）
bash scripts/release_spark_udf_lp.sh 1.0.0

# 3. 发布 + 注册（HDFS /udf + CREATE OR REPLACE FUNCTION）
bash scripts/deploy_spark_udf_lp.sh 1.0.0

# 4. 集群 UAT（L3）
bash scripts/spark_udf_uat.sh 1.0.0
```

验证（STS）:

```bash
beeline -u jdbc:hive2://hivespark03:10015/default \
  -e "SHOW FUNCTIONS LIKE 'udf_%'; SELECT udf_prefix('helloworld');"
```

## 新增一个 UDF

1. 在 `com.chungtow.udf` 包新增类，继承 `org.apache.hadoop.hive.ql.udf.generic.GenericUDF`（参考 `PrefixUdf`）；
2. 新增 JUnit 单测（`src/test/java`，覆盖正常/null/边界/入参错误）；
3. `scripts/udf-manifest.txt` 追加一行：`注册名|完整类名`；
4. 重新构建发布：`bash build.sh <VER> && bash scripts/deploy_spark_udf_lp.sh <VER>`；
5. 跑 UAT 并留档 `docs/uat/spark_udf_uat_report_YYYYMMDD.md`。

## 分支策略

- `master`：稳定主干（GitHub 默认分支）
- `dev`：开发集成分支（`feat/*` / `fix/*` 的 PR 目标）
- `feat/<desc>` / `fix/<desc>`：工作分支，从 `dev` 拉出，两阶段流程（本地开发+UAT → GitHub PR 合入 dev）

详见父项目 `docs/plan/20260825-spark-udf-lp新项目开发与UDF注册计划.md`。

## 注意事项

- **关键依赖均 provided**：jar 只含 UDF 类，禁止携带 spark/hive/hadoop 类（构建后 `jar tf` 抽查）；
- **jar 版本化**：HDFS `/udf/spark-udf-lp-<VER>.jar` 不可覆盖，升级 = 新版本 + `CREATE OR REPLACE`；
- 注册名统一 `udf_` 前缀，避免函数名冲突。
