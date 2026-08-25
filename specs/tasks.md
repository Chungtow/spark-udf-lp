# Tasks: spark-udf-lp — 任务清单

> 状态: 骨架（Draft）
> 基于 `proposal.md` 需求拆解，design 阶段细化后逐项勾选执行。
> 约定: `- [x]` 已完成，`- [ ]` 待执行。

## 阶段 0: 项目骨架（已完成）

- [x] GitHub 建仓（Chungtow/spark-udf-lp）+ 父项目 submodule 挂载
- [x] `pom.xml`（Java 8 / spark 3.3.1 / hive 2.3.9 / hadoop 3.1.4，均 provided）+ 目录结构 + README
- [x] `builder/` 构建镜像（`maven:3.9-eclipse-temurin-8` + `settings.xml` 阿里云源）+ `build.sh`
- [x] `PrefixUdf` 实现（GenericUDF，前 4 字符）+ JUnit 单测（L1 闸门）
- [x] 容器化构建出包 `target/spark-udf-lp-1.0.0.jar`（纯 UDF 类）

## 阶段 1: 发布与注册链路（已完成）

- [x] HDFS `/udf/spark-udf-lp-<VER>.jar` 版本化上传
- [x] `lpudf` 库统一注册（`CREATE OR REPLACE FUNCTION lpudf.<name> ... USING JAR`）
- [x] beeline/STS 冒烟验证（功能矩阵 6/6 通过）
- [x] 跨库全限定调用验证（lpdw_dev / gmall_dw）
- [x] 清理 8 库旧注册，保证 `lpudf` 唯一注册地址
- [x] `scripts/` 移出版本控制（集群耦合，防泄露）

## 阶段 2: UDAF（规划中）

- [ ] 需求细化（proposal `REQ-UDAF-*`）并评审
- [ ] design 阶段补充 UDAF 设计与契约（api-spec.yaml）
- [ ] 首个 UDAF 实现（GenericUDAFResolver2）+ 单测（空输入/单行/多行/含 NULL）
- [ ] 构建发布 + 集群聚合结果与原生 SQL 对比验证

## 阶段 3: UDTF（规划中）

- [ ] 需求细化（proposal `REQ-UDTF-*`）并评审
- [ ] design 阶段补充 UDTF 设计与契约（api-spec.yaml）
- [ ] 首个 UDTF 实现（GenericUDTF）+ 单测（输出行数/字段/类型）
- [ ] 构建发布 + 集群 `LATERAL VIEW` 用法验证

## 阶段 4: 验证与完善

- [ ] L3.3 分布式执行验证（大表/多分区，确认 executor 侧 jar 加载）
- [ ] Iceberg catalog（`spark_catalog` 代理）兼容性验证
- [ ] UDF 库性能/回归基线
