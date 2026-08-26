# Tasks: spark-udf-lp — 任务清单

> 状态: 本期迭代（Draft）
> 基于 `proposal.md` 需求拆解，design 阶段细化后逐项勾选执行。
> 约定: `- [x]` 已完成，`- [ ]` 待执行。
> 生命周期: 与当前分支 `feat/lpudf-help` 开发周期绑定；阶段 0/1 为仓库基线历史事实，保留不动。

## 阶段 0: 项目骨架（基线，已完成）

- [x] GitHub 建仓（Chungtow/spark-udf-lp）+ 父项目 submodule 挂载
- [x] `pom.xml`（Java 8 / spark 3.3.1 / hive 2.3.9 / hadoop 3.1.4，均 provided）+ 目录结构 + README
- [x] `builder/` 构建镜像 + `build.sh`
- [x] 首个 UDF 实现 + JUnit 单测（L1 闸门）

## 阶段 1: 发布与注册链路（基线，已完成）

- [x] HDFS `/udf/spark-udf-lp-<VER>.jar` 版本化上传
- [x] `lpudf` 库统一注册（唯一注册地址，DROP+CREATE）
- [x] `scripts/` 移出版本控制（集群耦合，防泄露）

## 阶段 2: 上一周期（迭代 2，9 个 JSON 函数，已合入 dev）

- [x] 全部开发任务已勾选完成并经 PR #2 合入 `dev`（详见 dev 分支历史），本期不再重复

## 阶段 3: 本期迭代（REQ-HELP-1~6）

### 3.1 调研与 POC

- [x] 反编译 spark-catalyst_2.12-3.3.1.jar：内置函数帮助信息三层链路（注解→注册宏→desc 渲染）
- [x] 确认 UDF 注册路径限制：`makeExprInfoForHiveFunction` usage 硬编码 null（字节码 offset 34 aconst_null）
- [x] POC（inception §7.4，临时文件已清理）：
  - P0 注解对 hive 注册路径不生效（DESC 显示 Usage: N/A.）
  - P1 `registerFunction(name, info, builder)` 4 参注入可行，覆盖语义=后注册覆盖
  - P2 `SparkSessionExtensions.injectFunction` 存在且生效；注入条目使同名 CREATE FUNCTION 抛 FunctionAlreadyExistsException

### 3.2 需求与设计

- [x] proposal.md 定稿（REQ-HELP-1~6，含 POC 结论回写）
- [x] design.md 定稿（ADR-9：injectFunction 注入方案 + ADR-10/11/12）
- [x] api-spec.yaml 补 desc function 输出契约（帮助文本规范）

### 3.3 实现

- [x] 12 个函数类标注 `@ExpressionDescription`（usage/arguments/note；帮助文本**直写函数名**——注入路径无 `_FUNC_` 占位替换机制）
- [x] `LpudfFunctionRegistry`：12 个函数描述常量清单（FunctionIdentifier + ExpressionInfo，文本源 api-spec；`udf_prefix` 统一注入 lpudf 库）
- [x] `LpudfExtensions`（spark.sql.extensions 入口）：injectFunction 注入 12 个函数，builder 用 HiveGenericUDF/HiveUDAFFunction/HiveGenericUDTF 包装
- [x] L1 单测：描述清单完整性（12 个、usage 非空）+ 注解反射断言 + 扩展类加载冒烟（LpudfFunctionRegistryTest 4 例）

### 3.4 构建、部署与集群验证

- [x] `build.sh 1.0.1` 构建通过（142 单测全绿；修复 spark-hive 传递 log4j-1.2-api 2.6.2 降版 log4j-api 问题）
- [x] `scripts/release_spark_udf_lp.sh 1.0.1` 制品入库（software/spark-udf/）
- [x] `scripts/deploy_spark_udf_lp.sh 1.0.1` 部署（ADR-9：HDFS 上传 + cp current + 幂等配置 spark-defaults.conf，不再 DROP/CREATE）
- [x] 重启 STS（坑 B：容器缺 ps 致 stop 无效，`docker restart spark` 整容器重启，单实例加载新配置）
- [x] L3.1 `DESC FUNCTION lpudf.<fn>` 12/12 显示三段式帮助（Function/Class/Usage）
- [x] L3.2 `DESC FUNCTION EXTENDED` 抽查 3 个显示 Extended Usage
- [x] L3.3 功能回归：l32_function_matrix.sql + l32_json_functions.sql 25 例全绿 + L3.4 迭代 1 冒烟 3/3 + L3.5 持久性
- [x] README 补充帮助信息/注入部署说明；inception.md 变更记录；docs/uat 报告迭代 3 章节

## 阶段 4: 收尾

- [x] commit + push `feat/lpudf-help`
- [ ] PR 合入 `dev`（评审通过）
