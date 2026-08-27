# Design: spark-udf-lp — 聚合函数族（迭代 5，目标 1.1.5）

> 状态: 已定稿（Approved）
> 关联: 基于 `proposal.md` 展开，为 SDD design 阶段产物。
> 生命周期: 与当前分支开发周期绑定；跨周期稳定决策（Java8 编译/运行、hive-exec 2.3.9 provided、lpudf 唯一注册地址、注册名裸名 ADR-8 等）见既有 design 的 ADR。

## 1. 架构总览

本期**只新增 UDAF 实现类**，不引入新依赖、不改构建/部署链路：

```
[src/main/java/com/liangpu/udaf/*.java] --build.sh 1.1.5(builder 容器)--> [spark-udf-lp-1.1.5.jar]
  --> deploy_spark_udf_lp.sh --> [HDFS /udf/] --DROP+CREATE--> [lpudf 库(裸名注册)]
  --> LpudfExtensions 会话级注入帮助信息 --> [STS 任意库 lpudf.<fn> 调用]
```

## 2. 模块划分

### 2.1 `src/main/java/com/liangpu/udaf/` — 8 个新 UDAF

| 注册名（裸名，ADR-8） | 实现类 | 缓冲（AbstractAggregationBuffer） | 关键语义 |
|---|---|---|---|
| `any_value` | `AnyValueUDAF` | 单值 + hasValue 标志 | 任选非 NULL 值；全 NULL → NULL |
| `map_agg` | `MapAggUDAF` | `LinkedHashMap<k,v>` | 重复 key 后者覆盖；NULL key 忽略 |
| `median` | `MedianUDAF` | 值列表（全量缓冲） | 精确中位数；偶数取均值；支持 double |
| `arg_max` | `ArgMaxUDAF` | (maxVal, retVal) | 参数序对齐 MC `(v_max, v_ret)`；NULL v_max 忽略 |
| `arg_min` | `ArgMinUDAF` | (minVal, retVal) | 同上镜像 |
| `histogram` | `HistogramUDAF` | `Map<k,Long>` | 频次计数 `map<k,bigint>`；NULL 不计 |
| `multimap_agg` | `MultimapAggUDAF` | `Map<k,List<v>>` | `map<k,array<v>>`；NULL value 保留 |
| `wm_concat` | `WmConcatUDAF` | (sep, StringBuilder) | sep 自定义、不去重；NULL 忽略 |

- 类名风格与既有 `StringAggUDAF` 一致（语义化，无 `uda_` 前缀）；**注册名裸名**（ADR-8，仅迭代 1 遗留 `udaf_string_agg` 等带前缀，本轮不加）。
- 全部继承 `GenericUDAFResolver2`（§9.2 blueprint），`getParameters()` 返回 `TypeInfo[]`、实现 `isWindowing()`；输出 ObjectInspector 由 `initialize` 依据入参类型动态构造（map/array 用 `StandardMapObjectInspector` / `StandardListObjectInspector`）。
- 每个类必须标注 `@ExpressionDescription(usage=..., arguments=...)`（ADR-11，registry 单测强制校验）。

### 2.2 `builder/` — 构建镜像

无变更（沿用 `maven:3.9-eclipse-temurin-8` + 阿里云源 + .m2 缓存卷）。

### 2.3 `scripts/` — 发布注册

`udf-manifest.txt` 追加 8 行 `<裸名>|com.liangpu.udaf.<类名>`；其余脚本无改动。

## 3. 构建设计

- `bash build.sh 1.1.5`（容器化，禁止宿主机 mvn，§13.1）：单测闸门 → `target/spark-udf-lp-1.1.5.jar`。
- 无新依赖（fastjson2 / hive-exec 2.3.9 provided 不变）；构建后 `jar tf` 抽查无 spark/hive 类。

## 4. 发布与注册设计

- HDFS 版本化路径 `/udf/spark-udf-lp-1.1.5.jar`（新版本号，不可覆盖旧 URI，§10.4 坑 C）。
- `deploy_spark_udf_lp.sh 1.1.5`：manifest 驱动 **DROP FUNCTION IF EXISTS + CREATE FUNCTION**（§10.4 坑 A）注册 8 个新函数到 lpudf 库。
- 帮助文本：`LpudfFunctionRegistry.ALL` 追加 8 条（带 ExpressionInfo，usage/arguments）→ `DESC FUNCTION lpudf.<fn>` 可读（§9.4 双轨）。
- jar 升级后**必须重启 STS**（§10.4 坑 B：classloader 缓存旧类）。

## 5. 验证设计

- **L1 单测**（构建闸门，每个 UDAF ≥6 用例）：正常值 / 空输入 / 单行 / 多行 / NULL 忽略 / 全 NULL → NULL / 类型边界（bigint/double/string）/ **PARTIAL1→PARTIAL2 分片 merge 链**（§9.2 强制项）；`eval.aggregate` 驱动 `iterate`/`merge`（§10.2）。
- **L2 构建检查**：build.sh 全绿 + jar tf 抽查。
- **L3 集群 UAT**（`spark_udf_uat.sh 1.1.5`）：L3.1 注册冒烟（`SHOW FUNCTIONS IN lpudf` 8 个新函数 + DESC 抽查）；L3.2 功能矩阵（8 函数 × 正常/边界 SQL，结果对齐 api-spec；**对标示例直接复用 `lpudf.emp`（MC 文档示例表，见 inception §3.3.1）**）；L3.3 分布式（merge 链：≥5000 万行多文件大表 + `spark.sql.adaptive.coalescePartitions.enabled=false`，§10.8）；L3.4 持久性（STS 重启后仍可用）。
- **L4 回归**：既有 22+ 函数 `SHOW FUNCTIONS IN lpudf` 全量核对 + `uda_string_agg` 回归（wm_concat 并存不冲突）。

## 6. 关键决策记录（ADR）

| # | 决策 | 理由 | 日期 |
|---|---|---|---|
| ADR-12 | 本轮 8 个函数**注册名用裸名**（`any_value` 等），不用 `uda_` 前缀 | 对齐既有 ADR-8（json/string 均裸名，MC 原生名）；仅迭代 1 遗留带前缀；修正立项阶段 uda_* 笔误 | 2026-08-27 |
| ADR-13 | `wm_concat` **新增**（不去重、sep 自定义），不修改 `uda_string_agg` 签名 | 两者语义不同（string_agg=去重+字典序+逗号，确定性）；改已发布签名破坏兼容性 | 2026-08-27 |
| ADR-14 | `wm_concat` 分布式拼接：缓冲存 (sep, 串)；iterate 时非首值先 append 当前行 sep；**merge 时 `A.buf + B.sep + B.buf`** | 常量 sep 时与 MC 完全一致；列值 sep 时语义文档声明（建议常量）；避免 merge 阶段拿不到 sep 的问题 | 2026-08-27 |
| ADR-15 | `arg_max(v_max, v_ret)` / `arg_min(v_min, v_ret)` 参数序对齐 MC（先比较列后返回列），与 Spark `max_by(v_ret, v_max)` 反序并存 | MC 用户迁移零成本；同名同参序 | 2026-08-27 |
| ADR-16 | `median` 采用**精确中位数**（全量缓冲排序，偶数取均值），支持 double | MC 语义对齐；文档注明大输入内存风险与 `percentile_approx` 替代 | 2026-08-27 |
| ADR-17 | `histogram` 输出 `map<k,bigint>` 频次计数 | 与 Spark `histogram_numeric`（数值分箱 array<struct<bin,count>>）语义区分，对齐 MC | 2026-08-27 |
| ADR-18 | `map_agg` 重复 key 后者覆盖；`multimap_agg` NULL key 忽略、NULL value 保留 | 对齐 MC 文档语义 | 2026-08-27 |

## 7. 风险与对策

| 风险 | 状态 | 结论/对策 |
|---|---|---|
| `median` 全量缓冲在大输入下 OOM | 待验证 | 文档声明适用范围与 `percentile_approx` 替代；UAT 大表实测观察（可降级为近似实现） |
| `wm_concat` 列值 sep 时分布式结果不确定 | 已决策 | ADR-14 语义（分片内各自 sep、分片间后分片 sep）；文档声明 sep 建议常量 |
| map/array 类型 ObjectInspector 序列化（terminatePartial）跨节点不一致 | 待验证 | 单测 merge 链强制覆盖；L3.3 大表实测 |
| `histogram`/`multimap_agg` 返回值类型复杂（map 套 array），Spark 类型系统兼容 | 待验证 | initialize 动态构造 ObjectInspector；UAT 验证 SELECT 返回可被 SQL 消费 |
| 帮助文本遗漏（漏登记 registry） | 已控制 | REQ-HELP-1 验收：registryTest 三勾稽用例（registryCoversAllFunctions / everyEntryHasUsableMetadata）+ UAT DESC 抽查（§9.4） |
| 注册名与 ADR-8 不一致（uda_* 前缀） | 已修正 | ADR-12 固化裸名；manifest/registry/registryTest/api-spec 四处同步（S1.3 注册三连 + S0.7 契约） |
