# spark-udf-lp

Spark 自定义 UDF（Hive 风格 `GenericUDF`）项目。**本仓库能力终点 = 构建产出 `target/spark-udf-lp-<VER>.jar`**；集群发布（HDFS `/udf/`）、注册、部署与 UAT 均由父仓库负责（测试槽 / 生产槽脚本见父仓库 `scripts/`）。函数经 **SparkSessionExtensions 会话级注入**（ADR-9，迭代 3 起）注册：`spark.sql.extensions=com.liangpu.help.LpudfExtensions`，任意 STS 会话可直接调用，且 `DESC FUNCTION` 显示与内置函数一致的三段式帮助（Function/Class/Usage）。

## 版本基线

| 组件 | 版本 |
|------|------|
| Spark | 3.3.1-bin-hadoop3（hivespark03 spark 容器） |
| Hadoop | 3.1.4 |
| Java | 8（编译与运行时统一，builder 镜像 `maven:3.9-eclipse-temurin-8`） |
| Hive 兼容层 | 2.3.9（与 Spark 3.3.1 内置一致，provided） |

## 目录结构

```
├── pom.xml                    # Java 8；spark-sql/spark-hive/hive-exec/hadoop-client 均 provided
├── src/main/java/com/liangpu/udf/    # UDF 源码（继承 GenericUDF）
├── src/main/java/com/liangpu/help/   # 帮助注入：LpudfExtensions / LpudfFunctionRegistry / LpudfFunction
├── src/test/java/com/liangpu/udf/    # UDF 单测（构建闸门）
├── src/test/java/com/liangpu/help/   # 注册清单/扩展加载单测
├── builder/                   # 构建镜像（maven:3.9-eclipse-temurin-8）+ settings.xml（阿里云源）
└── build.sh                   # 容器化构建入口

部署 / UAT / 制品入库脚本位于**父仓库** `scripts/spark-udf-lp/`（测试槽，与生产槽
`scripts/spark-udf/` 双槽分离，唯一交接点 `software/spark-udf/`）：
- `deploy_spark_udf_lp.sh`  # HDFS 上传 + cp current + 幂等配置 spark-defaults.conf（ADR-9，不再 DROP/CREATE）
- `spark_udf_uat.sh`        # 集群 UAT（L3，含 DESC FUNCTION）
- `release_spark_udf_lp.sh` # 制品入库 → 父仓库 software/spark-udf/
- `udf-manifest.txt`        # UDF 注册清单：注册名|类名（UAT 遍历）
- `uat/`                    # UAT SQL 用例（l32_*.sql / l33_distributed.sql）
```

## 快速开始

```bash
# 1. 构建（builder 容器内跑单测 + 打包，258 单测为闸门）
bash build.sh 1.0.1                          # 产物 target/spark-udf-lp-1.0.1.jar

# 2-5 在父仓库根执行（脚本位于父仓库 scripts/spark-udf-lp/）

# 2. 制品入库（写入父仓库 software/spark-udf/）
bash scripts/spark-udf-lp/release_spark_udf_lp.sh 1.0.1

# 3. 发布 + 注入配置（ADR-9：HDFS 上传 + cp current + 幂等更新 spark-defaults.conf，不再 DROP/CREATE）
bash scripts/spark-udf-lp/deploy_spark_udf_lp.sh 1.0.1

# 4. 整容器重启 STS（容器缺 ps，stop-thriftserver.sh 杀不掉旧进程，必须 docker restart spark 才能单实例加载新配置）
ssh hivespark03 "docker restart spark" && sleep 30

# 5. 集群 UAT（L3，含 DESC FUNCTION）
bash scripts/spark-udf-lp/spark_udf_uat.sh 1.0.1
```

验证（STS）:

```bash
beeline -u jdbc:hive2://hivespark03:10015/default
0: jdbc:hive2://hivespark03:10015> DESC FUNCTION lpudf.json_valid;
# Function: lpudf.json_valid
# Class: com.liangpu.udf.JsonValidUdf
# Usage: json_valid(json) - 校验输入字符串是否为合法 JSON，返回 boolean（NULL 输入返回 NULL）
0: jdbc:hive2://hivespark03:10015> SELECT lpudf.json_valid('{"a":1}');
```

## 新增一个 UDF

1. 在 `com.liangpu.udf` 包新增类，继承 `org.apache.hadoop.hive.ql.udf.generic.GenericUDF`（参考 `PrefixUdf`），类声明前加 `@ExpressionDescription` 注解（clion 锚点，usage 直写函数名）；
2. 新增 JUnit 单测（`src/test/java`，覆盖正常/null/边界/入参错误）；
3. 父仓库 `scripts/spark-udf-lp/udf-manifest.txt` 追加一行：`注册名|完整类名`（UAT 遍历）；
4. **`src/main/java/com/liangpu/help/LpudfFunctionRegistry.java` 追加清单条目**（name/className/usage/arguments，注入路径的 DESC 帮助数据源）；
5. 重新构建发布：`bash build.sh <VER>`（子仓库）→ 父仓库 `bash scripts/spark-udf-lp/release_spark_udf_lp.sh <VER> && bash scripts/spark-udf-lp/deploy_spark_udf_lp.sh <VER>`，`docker restart spark` 重启 STS；
6. 跑 UAT 并留档报告（归档于父仓库 `docs/spark-udf-lp/uat/`）。

## 分支策略

- `master`：稳定主干（GitHub 默认分支）
- `dev`：开发集成分支（`feat/*` / `fix/*` 的 PR 目标）
- `feat/<desc>` / `fix/<desc>`：工作分支，从 `dev` 拉出，两阶段流程（本地开发+UAT → GitHub PR 合入 dev）

详见父项目 `docs/plan/20260825-spark-udf-lp新项目开发与UDF注册计划.md`。

## 注意事项

- **关键依赖均 provided**：jar 只含 UDF 类，禁止携带 spark/hive/hadoop 类（构建后 `jar tf` 抽查）；
- **jar 版本化**：HDFS `/udf/spark-udf-lp-<VER>.jar` 不可覆盖，升级 = 新版本 + `deploy`（deploy 脚本 cp 为 current，配置固定引用 `spark.jars`，升级后**必须** `docker restart spark` 才加载新类）；
- **注册方式（迭代 3 起，ADR-9）**：函数经 `spark.sql.extensions=...,com.liangpu.help.LpudfExtensions` 会话级注入，**禁止再执行 CREATE/DROP FUNCTION**（注入条目使同名 CREATE 抛 `FunctionAlreadyExistsException`）；metastore 旧记录冗余无害（registry 注入条目优先）；
- **帮助信息**：注入条目的 help 文本直写函数名（注入路径无 `_FUNC_` 占位替换机制），数据源为 `LpudfFunctionRegistry`（usage/arguments）；22 个函数类上的 `@ExpressionDescription` 注解为文档锚点（注解对注入路径不生效）；
- 注册名：迭代 1 函数统一 `udf_` 前缀；迭代 2 起新函数**无前缀**（`lpudf` 库即命名空间，用 MC 原生名如 `json_valid`，ADR-8）；22 个函数统一注入 `lpudf` 库（含 udf_prefix，历史 default 库记录冗余无害）。
