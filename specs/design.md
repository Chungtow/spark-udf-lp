# Design: spark-udf-lp — 架构设计

> 状态: 待填充（Draft）
> 关联: 基于 `proposal.md` 展开，为 SDD design 阶段产物，当前仅骨架。
> 填写时机: proposal 评审通过后进入 design 阶段时逐节填充。

## 1. 架构总览

（待填：分层架构、组件图、构建 → 发布 → 注册 → 调用 数据流）

```
[本地 Maven 源码] --build.sh(builder 容器)--> [jar] --deploy--> [HDFS /udf/] --CREATE FUNCTION--> [lpudf 库] --> [STS 任意库调用]
```

## 2. 模块划分

### 2.1 `src/main/java/com/chungtow/udf/` — 函数实现
（待填：UDF / UDAF / UDTF 包结构、类职责、命名规范、注册名规范 `udf_`/`uda_`/`udtf_`）

### 2.2 `builder/` — 构建镜像
（待填：Dockerfile 设计、`settings.xml` 阿里云源、`.m2` 缓存卷、镜像版本 `maven:3.9-eclipse-temurin-8`）

### 2.3 `scripts/` — 发布注册（本地保留，不入库）
（待填：`build.sh` / `deploy_spark_udf_lp.sh` / `release_spark_udf_lp.sh` / `spark_udf_uat.sh` 职责与调用关系）

## 3. 构建设计

（待填：`mvn` 命令与阶段、provided 依赖策略、jar 瘦身检查 `jar tf`、单测作为构建闸门）

## 4. 发布与注册设计

（待填：HDFS 版本化路径 `/udf/spark-udf-lp-<VER>.jar`、`lpudf` 库唯一注册地址、
`udf-manifest.txt` 驱动、`CREATE OR REPLACE FUNCTION lpudf.<name> ... USING JAR 'hdfs://mycluster/...'`）

## 5. 验证设计

（待填：四层验证 L1 单测 / L2 构建检查 / L3 集群 UAT / L4 回归，功能矩阵样例）

## 6. 关键决策记录（ADR）

| # | 决策 | 理由 | 日期 |
|---|---|---|---|
| 1 | Java 8 编译/运行统一（builder `maven:3.9-eclipse-temurin-8`） | 对齐 spark 容器 `openjdk:8-jre-slim` | 2026-08-24 |
| 2 | `hive-exec` 2.3.9（provided） | 与 Spark 3.3.1 内置 Hive 兼容层一致 | 2026-08-24 |
| 3 | 关键依赖均 provided | jar 不携带 spark/hive/hadoop 类，避免类冲突 | 2026-08-24 |
| 4 | 统一注册 `lpudf` 库，全限定名引用 | 单一注册地址、任意库天然可用、UDF/UDAF/UDTF 统一管理 | 2026-08-25 |
| 5 | beeline 连 STS 注册，替代 `spark-sql --master yarn` | 复用已运行会话，轻量可靠 | 2026-08-25 |
| （待增补） | ... | ... | ... |

## 7. 风险与对策

（待填：Iceberg catalog 代理 `spark_catalog` 对函数加载的影响、YARN 资源、跨库兼容等）
