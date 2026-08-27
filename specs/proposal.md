# Proposal: spark-udf-lp — 聚合函数族（迭代 5，目标 1.1.5）

> 状态: 草稿（Draft）
> 来源: `specs/inception.md`（历史事实来源）/ 本期迭代需求
> 关联: 父项目 `hadoop-cluster-physical`（以 git submodule 挂载）
> 生命周期: 与当前分支开发周期绑定，新周期从空模板重新填充

## Goal

对标阿里云 MaxCompute 聚合函数，开发 Spark 3.3.1 缺失的 8 个聚合 UDAF（`any_value` / `map_agg` / `median` / `arg_max` / `arg_min` / `histogram` / `multimap_agg` / `wm_concat`），补齐聚合能力空缺并降低 MC 用户迁移成本，目标版本 1.1.5。

## 背景与动机

- 库内 UDAF 仅 `uda_string_agg`（迭代 1）1 个，聚合能力存在明显空缺：取任一样本、构造字典、中位数、极值关联行、值分布计数等高频场景均无直接函数。
- 采用既有对标法（json/string 同源）：MC 聚合函数文档 39 个 vs Spark 3.3.1 内置（集群实测 24 项），差异 = 候选开发清单（详见 `inception.md` §2/§5）。
- 约束：Spark 3.3.1 + Hive 2.3.9 API；分布式聚合**不保证组内输入顺序**，所有 UDAF 语义必须对顺序不确定健壮（§9.2/§10.5）。

## Requirements

### 新增函数

- **REQ-UDAF-01：`any_value(col)`** — 任选一个非 NULL 值返回；全 NULL 组返回 NULL；空组返回 NULL。
  - 验收：单测覆盖 多行/全 NULL/混合/单行/空输入；merge 链（PARTIAL1→PARTIAL2）；行为与 MC any_value 一致（任取非 NULL 其一）。
- **REQ-UDAF-02：`map_agg(k, v)`** — 两列构造 `map<k,v>`；重复 key 后者覆盖；NULL key 忽略；NULL value 保留。
  - 验收：单测覆盖 正常/重复 key 覆盖/NULL key 忽略/NULL value 保留/空输入；merge 链。
- **REQ-UDAF-03：`median(col)`** — 数值列精确中位数：排序取中间值，偶数取中间两值均值；NULL 忽略；全 NULL 返回 NULL；支持 double 输入。
  - 验收：单测覆盖 奇数/偶数/含 NULL/全 NULL/double 精度/空输入；merge 链；文档注明大输入下内存风险与 percentile_approx 替代。
- **REQ-UDAF-04：`arg_max(v_max, v_ret)`** — 返回 v_max 最大时对应的 v_ret（参数顺序对齐 MC：先比较列后返回列）；NULL v_max 忽略；并列（tie）取其一（顺序不确定 → 文档声明非确定性）；全 NULL 返回 NULL。
  - 验收：单测覆盖 普通/并列/含 NULL/全 NULL/空输入；merge 链；与 `max_by(v_ret, v_max)` 参数序反证。
- **REQ-UDAF-05：`arg_min(v_min, v_ret)`** — 同 REQ-UDAF-04 取最小。
  - 验收：同 REQ-UDAF-04 镜像。
- **REQ-UDAF-06：`histogram(col)`** — 值分布计数，返回 `map<k,bigint>`；NULL 不计；key 为输入值类型。
  - 验收：单测覆盖 频次正确/NULL 不计/单值/空输入；merge 链（map 逐键累加）；与 MC histogram 语义一致（区别于 Spark histogram_numeric 数值分箱）。
- **REQ-UDAF-07：`multimap_agg(k, v)`** — 构造 `map<k,array<v>>`，同 key 多值并入数组；NULL key 忽略；NULL value 保留进数组。
  - 验收：单测覆盖 多值并入顺序(组内按 iterate 顺序，文档声明不确定)/NULL key/NULL value/空输入；merge 链（数组 concat）。
- **REQ-UDAF-08：`wm_concat(sep, col)`** — 按 sep 连接字符串（不去重、不排序）；NULL 忽略；sep 为常量或列值；空组/全 NULL 返回 NULL。
  - 验收：单测覆盖 多行拼接/NULL 忽略/自定义 sep/单行/空输入；merge 链；与 `uda_string_agg`（去重+字典序+逗号）语义并存不冲突。

### 函数变更

- 无（不修改已发布函数签名；`wm_concat` 为新增，与 `uda_string_agg` 并存）。

### 非函数项

- **REQ-HELP-1：8 个新函数帮助文本登记** — 延续既有帮助机制（§9.4）：`@ExpressionDescription` 标注 + `LpudfFunctionRegistry.ALL` + `api-spec.yaml` 帮助文本同步；`DESC FUNCTION lpudf.<fn>` 必须显示 usage + arguments。
  - 验收：`LpudfFunctionRegistryTest` 全绿（含 `registryCoversAllFunctions` / `everyEntryHasUsableMetadata`）；UAT L3.1 抽查 DESC 输出含用法与参数。
- **REQ-REG-1：注册三连同步** — `scripts/udf-manifest.txt` 追加 8 行（`<注册名>|类名`，**裸名对齐 ADR-8**：`any_value`/`map_agg`/`median`/`arg_max`/`arg_min`/`histogram`/`multimap_agg`/`wm_concat`）+ `LpudfFunctionRegistry.ALL` + `LpudfFunctionRegistryTest.EXPECTED_NAMES`。
  - 验收：S2.2 部署后 `SHOW FUNCTIONS IN lpudf` 可见全部 8 个新函数。
- **REQ-DOC-1：迭代收尾文档** — Stage 3 前 `docs/inception/20260827-feat-aggregate-functions-聚合函数.md` 存档；Stage 4 用户指南按需。

## 验收标准

- 与 `tasks.md` 阶段勾选、`api-spec.yaml` 契约对应（S0.7 契约 = 验收基准）。
- L1 单测全绿（构建闸门，`build.sh`）→ L2 构建产出 `target/spark-udf-lp-1.1.5.jar` → L3 集群 UAT 全绿（L3.1 注册冒烟 / L3.2 功能矩阵含 DESC / L3.3 分布式 merge 链 / L3.4 持久性）。
