# Proposal: spark-udf-lp — Spark 自定义函数库（UDF / UDAF / UDTF）

> 状态: 初稿（Draft）
> 来源: `specs/inception.md`（Inception 阶段收敛结论，事实来源）
> 关联: 父项目 `hadoop-cluster-physical`（以 git submodule 挂载）

## Goal

建立一套以 **容器化构建 + HDFS 发布 + `lpudf` 库统一注册** 为标准的 Spark 自定义函数库，
覆盖 **UDF / UDAF / UDTF** 三类函数，使集群任意库、任意 Spark ThriftServer（STS）会话
均能以 `lpudf.<函数名>` 全限定名引用。

## 背景与动机

- 集群内业务 SQL（gmall_dw / lpdw / lpods 等）需要自定义处理逻辑，手工编译 jar + 逐节点分发不可持续；
- 函数作用域缺乏规范：曾出现注册在 default 库导致其他库不可用、逐库注册导致维护困难的问题（见 inception §4.4）；
- 需要为 UDAF / UDTF 预留统一的注册与管理入口。

## Requirements

### UDF（MVP，已完成）

- **REQ-UDF-1**：支持 Hive 风格 `GenericUDF` 实现，注册为 Hive 永久函数（写入 Metastore，重启仍可用）
- **REQ-UDF-2**：构建产物版本化发布至 HDFS `/udf/spark-udf-lp-<VER>.jar`，不可覆盖
- **REQ-UDF-3**：函数统一注册至 `lpudf` 库，任意库以 `lpudf.<函数名>` 全限定引用
- **REQ-UDF-4**：提供示例函数 `lpudf.udf_prefix`（取前 4 字符），并具备完整验证矩阵（L1 单测 + L3 集群）

### UDAF（规划中）

- **REQ-UDAF-1**：支持 `GenericUDAFResolver2` 实现聚合类函数（如去重计数、自定义统计等），注册名规范 `uda_` 前缀
- **REQ-UDAF-2**：复用与 UDF 相同的构建 / 发布 / 注册链路（builder 容器 → HDFS → `lpudf` 库注册）
- **REQ-UDAF-3**：单测覆盖聚合语义：空输入、单行、多行、含 NULL 行
- **REQ-UDAF-4**：集群验证：聚合结果与原生 SQL 等价，且支持 `GROUP BY`

### UDTF（规划中）

- **REQ-UDTF-1**：支持 `GenericUDTF` 实现表函数（一进多出，如行转列、展开），注册名规范 `udtf_` 前缀
- **REQ-UDTF-2**：复用与 UDF 相同的构建 / 发布 / 注册链路
- **REQ-UDTF-3**：单测覆盖输出行数、输出字段数与类型
- **REQ-UDTF-4**：集群验证：配合 `LATERAL VIEW` 使用的正确性

## Non-Requirements

- 不做函数热更新 / 动态注册（注册一律走 deploy 脚本，`CREATE OR REPLACE` 幂等）
- 不内置权限控制（依赖集群既有权限体系，如 Ranger 或 Hive 授权）
- jar 不携带 spark / hive / hadoop 类（依赖均 provided）
- `scripts/` 目录不入库（与集群高度耦合，本地维护）

## Implementation Notes

- **版本基线**：Spark 3.3.1-bin-hadoop3 / Hadoop 3.1.4 / Java 8 / hive-exec 2.3.9（provided）
- **构建**：builder 容器（`maven:3.9-eclipse-temurin-8`，阿里云源）+ `bash build.sh <VER>`；`mvn test` 为本地闸门
- **发布**：`scripts/deploy_spark_udf_lp.sh <VER>`（本地脚本）——建 `lpudf` 库、HDFS 上传、按 `udf-manifest.txt` 注册
- **注册契约**：
  ```sql
  CREATE OR REPLACE FUNCTION lpudf.<注册名> AS '<完整类名>'
    USING JAR 'hdfs://mycluster/udf/spark-udf-lp-<VER>.jar';
  ```
- **新增函数流程**：实现类 + JUnit 单测 + `udf-manifest.txt` 追加行 + `build.sh` + `deploy.sh`
- **注册名规范**：`udf_` / `uda_` / `udtf_` 前缀，避免与内置函数冲突

## Acceptance Criteria

1. UDF / UDAF / UDTF 三类函数均可经上述链路发布、注册并被 STS 会话调用；
2. 集群任意库以 `lpudf.<函数名>` 全限定名引用成功（新库零额外操作）；
3. 每类函数的验证矩阵（单测 + 集群功能用例）100% 通过；
4. 发布、注册、验证过程可重复执行（幂等），并有留档记录。

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-08-25 | 初稿：基于 inception 收敛结论；UDF 需求由 MVP 实证支撑，UDAF/UDTF 为规划需求 |
