# Tasks: spark-udf-lp — 任务清单

> 状态: 本期迭代（Draft）
> 基于 `proposal.md` 需求拆解，design 阶段细化后逐项勾选执行。
> 约定: `- [x]` 已完成，`- [ ]` 待执行。
> 生命周期: 与当前分支 `feat/json-processor` 开发周期绑定；阶段 0/1 为仓库基线历史事实，保留不动。

## 阶段 0: 项目骨架（基线，已完成）

- [x] GitHub 建仓（Chungtow/spark-udf-lp）+ 父项目 submodule 挂载
- [x] `pom.xml`（Java 8 / spark 3.3.1 / hive 2.3.9 / hadoop 3.1.4，均 provided）+ 目录结构 + README
- [x] `builder/` 构建镜像 + `build.sh`
- [x] 首个 UDF 实现 + JUnit 单测（L1 闸门）

## 阶段 1: 发布与注册链路（基线，已完成）

- [x] HDFS `/udf/spark-udf-lp-<VER>.jar` 版本化上传
- [x] `lpudf` 库统一注册（唯一注册地址，DROP+CREATE）
- [x] `scripts/` 移出版本控制（集群耦合，防泄露）

## 阶段 2: 本期迭代（REQ-UDF-1~9 + REQ-INFRA-1~4）

### 2.1 共享 JSON 解析层（com.liangpu.json）

- [x] `JsonSupport`: fastjson2 解析入口 + 异常分类（JsonSyntaxException / JsonPathException）
- [x] `JsonPathSupport`: JSONPath 编译缓存 + 求值 + path 子集校验（ADR-1）
- [x] POC 单测: fastjson2 对 path 子集行为验证（`$a` 非法、键含点、数组下标越界）→ 差异项回写 api-spec 边界

### 2.2 P0 函数（REQ-UDF-1~4）

- [x] `JsonValidUdf` + 单测（REQ-UDF-1）
- [x] `JsonExtractUdf` + 单测（REQ-UDF-2）
- [x] `JsonLengthUdf` + 单测（REQ-UDF-3）
- [x] `JsonExplodeUDTF` + 单测（REQ-UDF-4）

### 2.3 P1 函数（REQ-UDF-5~9）

- [x] `JsonTypeUdf` + 单测（REQ-UDF-5）
- [x] `JsonExistsUdf` + 单测（REQ-UDF-6）
- [x] `JsonContainsUdf` + 单测（REQ-UDF-7）
- [x] `JsonPrettyUdf` + 单测（REQ-UDF-8）
- [x] `JsonStripNullsUdf` + 单测（REQ-UDF-9）

### 2.4 基础设施（REQ-INFRA-1~4）

- [x] pom 增加 fastjson2 依赖 + shade relocation（REQ-INFRA-2 配套）
- [x] `scripts/udf-manifest.txt` 追加 9 行注册（无前缀注册名，REQ-INFRA-1）
- [x] api-spec.yaml 9 函数条目与实现/单测一致（REQ-INFRA-2，已完成初稿）
- [x] README 调用约定同步（库内裸名 / 跨库 `lpudf.<fn>`，REQ-INFRA-4）
- [x] 变更记录更新 inception.md

## 阶段 3: 构建、部署与集群验证

- [x] `build.sh <VER>` 构建通过（L1 单测全绿 + shade 后 `jar tf` 抽查无顶包残留）
- [x] `scripts/release_spark_udf_lp.sh <VER>` 制品入库（software/spark-udf/）
- [x] `scripts/deploy_spark_udf_lp.sh <VER>` 部署 + lpudf 注册
- [ ] 重启 STS（classloader 类缓存，坑 B）——与 L3.4 一并人工执行
- [x] L3.1 注册冒烟: `SHOW FUNCTIONS IN lpudf` 见 9 函数
- [x] L3.2 功能矩阵: 常量矩阵全函数验证（scripts/uat/l32_json_functions.sql，19 例）+ trade_order 表场景可补
- [ ] L3.4 持久性: 重启 STS 后函数仍可用（人工确认）
- [x] L4 回归: 迭代 1 函数冒烟（l32_function_matrix.sql，lpudf.udf_prefix 全限定）
- [ ] （如需要）L3.3 分布式验证（tmp_udf_big 大表 GROUP BY）

## 阶段 4: 收尾

- [ ] commit + push `feat/json-processor`
- [ ] PR 合入 `dev`（评审通过）
