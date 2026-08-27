# Tasks: spark-udf-lp — 任务清单

> 状态: 进行中（In Progress，迭代 5：聚合函数，目标 1.1.5）
> 基于 `proposal.md` 需求拆解，design 阶段细化后逐项勾选执行。
> 约定: `- [x]` 已完成，`- [ ]` 待执行。
> 生命周期: 与当前分支开发周期绑定；阶段 0/1 为仓库基线历史事实，保留不动。

## 阶段 0: 项目骨架（基线，已完成）

- [x] GitHub 建仓（Chungtow/spark-udf-lp）+ 父项目 submodule 挂载
- [x] `pom.xml`（Java 8 / spark 3.3.1 / hive 2.3.9 / hadoop 3.1.4，均 provided）+ 目录结构 + README
- [x] `builder/` 构建镜像 + `build.sh`
- [x] 首个 UDF 实现 + JUnit 单测（L1 闸门）

## 阶段 1: 发布与注册链路（基线，已完成）

- [x] HDFS `/udf/spark-udf-lp-<VER>.jar` 版本化上传
- [x] `lpudf` 库统一注册（唯一注册地址，DROP+CREATE）
- [x] `scripts/` 移出版本控制（集群耦合，防泄露）

## 阶段 2: 本期迭代（聚合函数族，目标 1.1.5）

### 编码

- [x] **S1.1 实现 8 个 UDAF 类**（REQ-UDAF-01~08）— `src/main/java/com/liangpu/udaf/`：`AnyValueUDAF` / `MapAggUDAF` / `MedianUDAF` / `ArgMaxUDAF` / `ArgMinUDAF` / `HistogramUDAF` / `MultimapAggUDAF` / `WmConcatUDAF`（+ 包内工具 `UdafSerialization`）
  - 全部继承 `GenericUDAFResolver2`（§9.2 blueprint），`getParameters()` 返回 `TypeInfo[]`、实现 `isWindowing()`
  - `init` 依据入参类型动态构造 ObjectInspector（map/array 用 Standard 系列）
  - 每个类标注 `@ExpressionDescription(usage=..., arguments=...)`（ADR-11）
  - 关键语义：any_value 任取非 NULL / map_agg 重复 key 后者覆盖、NULL key 忽略 / median 偶数取均值、支持 double / arg_max·arg_min 参数序 `(v_max, v_ret)` 对齐 MC / histogram 输出 `map<k,bigint>` / multimap_agg NULL value 保留进数组 / wm_concat 不去重、merge 时 `A.buf + B.sep + B.buf`（ADR-14）
- [x] **S1.2 单测**（REQ-UDAF-01~08 验收）— 8 个测试类 + 共享基类 `UdafTestBase`；每个 UDAF ≥6 用例：正常 / 空输入 / 单行 / 多行 / NULL 忽略 / 全 NULL → NULL / 类型边界（bigint/double/string）/ **PARTIAL1→PARTIAL2 分片 merge 链**（§9.2 强制项）；直接驱动 `iterate`/`merge`（§10.2）
- [x] **S1.3 帮助文本登记**（REQ-HELP-1）— `LpudfFunctionRegistry.ALL` 追加 8 条（Kind.UDAF，usage/arguments 对齐 api-spec description）
- [x] **S1.4 注册三连同步**（REQ-REG-1）— `scripts/udf-manifest.txt` 追加 8 行 `<裸名>|com.liangpu.udaf.<类名>`（any_value / map_agg / median / arg_max / arg_min / histogram / multimap_agg / wm_concat）+ `LpudfFunctionRegistryTest.EXPECTED_NAMES` 同步 22→30（裸名对齐 ADR-8/ADR-12）

### 构建

- [ ] **S2.1 构建**（L2 检查）— `bash build.sh 1.1.5`（容器化，§13.1）：L1 单测全绿 → `target/spark-udf-lp-1.1.5.jar`；`jar tf` 抽查无 spark/hive 类

### 发布注册

- [ ] **S3.1 部署** — `deploy_spark_udf_lp.sh 1.1.5`：HDFS 版本化路径 `/udf/spark-udf-lp-1.1.5.jar`（不可覆盖旧 URI，§10.4 坑 C）；manifest 驱动 DROP+CREATE 注册 8 个新函数（§10.4 坑 A）
- [ ] **S3.2 重启 STS**（§10.4 坑 B：classloader 缓存旧类）— 重启后等待就绪

## 阶段 3: 验证与回归

- [ ] **L3.1 注册冒烟** — `spark_udf_uat.sh 1.1.5`：`SHOW FUNCTIONS IN lpudf` 见 8 个新函数；`DESC FUNCTION lpudf.<fn>` 抽查输出含 usage + arguments（REQ-HELP-1 验收）
- [ ] **L3.2 功能矩阵** — 8 函数 × 正常/边界 SQL，结果对齐 `api-spec.yaml` 契约（对标示例直接复用 `lpudf.emp`，inception §3.3.1）
- [ ] **L3.3 分布式验证** — merge 链：≥5000 万行多文件大表 + `spark.sql.adaptive.coalescePartitions.enabled=false`（§10.8），观察 median 全量缓冲内存风险（可降级为近似实现）
- [ ] **L3.4 持久性** — STS 再次重启后 8 个新函数仍可用
- [ ] **L4 回归** — 既有 22+ 函数 `SHOW FUNCTIONS IN lpudf` 全量核对 + `uda_string_agg` 回归（wm_concat 并存不冲突，ADR-13）
- [ ] **收尾**（REQ-DOC-1）— `docs/inception/20260827-feat-aggregate-functions-聚合函数.md` 存档；git squash merge → dev、tag 1.1.5、更新版本轨迹；更新阶段 2/3 勾选状态
