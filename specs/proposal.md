# Proposal: spark-udf-lp — JSON 处理函数库（json_* 系列）

> 状态: 草稿（Draft）
> 来源: `specs/inception.md`（调研结论 §2、命名决议 §3.1、测试数据 §3.3）
> 关联: 父项目 `hadoop-cluster-physical`（以 git submodule 挂载）
> 生命周期: 与当前分支 `feat/json-processor` 开发周期绑定

## Goal

开发 **9 个 JSON 处理函数**（P0×4 + P1×5），补齐 Spark 3.3.1 缺失的 JSON 能力，语义对齐 MaxCompute，服务于 web 日志处理、kafka 消息处理等场景，沉淀为长期复用的函数库。

## 背景与动机

- **现状缺口**：集群 Spark 3.3.1 内置 JSON 函数仅 7 个（`from_json`/`to_json`/`get_json_object`/`json_tuple`/`json_array_length`/`json_object_keys`/`schema_of_json`），而 MaxCompute 提供 20 个；对比差异 **14 个**（详见 inception §2.2）
- **长期性**：Spark 全版本（含 4.x）从未实现 SQL:2016 标准 JSON 函数（Jira SPARK-58686 于 2026-08 刚立项，目标 4.4.0 未发布）→ 自研 UDF 为**长期方案**，非过渡（inception §2.3 勘误）
- **约束**：集群 Spark 固定 3.3.1 不可升级；函数需统一注册 `lpudf` 库（唯一注册地址），跨库 `lpudf.<fn>` 全限定引用
- **命名决议**：函数名**不带类型前缀**，直接采用与 MaxCompute 一致的原生名（`json_valid` 等）；不重复造 Spark 已有轮子；裸名限库内调用（inception §3.1）

## Requirements

### 新增函数（9 个：UDF × 8 + UDTF × 1）

**P0（核心，4 个）**

- REQ-UDF-1 `json_valid(str)`：校验字符串是否为合法 JSON，返回 `true`/`false`。验收：合法/非法/空串/NULL/嵌套对象均符合预期
- REQ-UDF-2 `json_extract(json, path)`：按 JSON path 提取，返回字符串或 JSON 文本（对齐 MC `JSON_EXTRACT`）。验收：多级路径、数组下标、键含特殊字符、路径不存在返回 NULL
- REQ-UDF-3 `json_length(json[, path])`：标量/数组/对象通用长度（对齐 MC `JSON_LENGTH`）。验收：标量=1、数组=元素数、对象=键数、空结构=0
- REQ-UDF-4 `json_explode(json)`：JSON 数组拆多行 / 对象拆 key-value（对齐 MC `JSON_EXPLODE`，UDTF）。验收：数组 300 条拆 300 行；对象拆 `(key, value)` 两列

**P1（增强，5 个）**

- REQ-UDF-5 `json_type(json)`：返回 JSON 值类型名（OBJECT/ARRAY/STRING/NUMBER/BOOLEAN/NULL）。验收：六种类型 + NULL 输入
- REQ-UDF-6 `json_exists(json, path)`：JSON path 是否存在（对齐 MC `JSON_EXISTS`，返回布尔）。验收：存在/不存在/路径非法
- REQ-UDF-7 `json_contains(json, candidate[, path])`：JSON 数据是否包含指定元素（对齐 MC `JSON_CONTAINS`：数组元素匹配 / path 节点值相等）。验收：数组元素命中/未命中、path 值相等/不存在/非法、candidate 为对象、NULL
- REQ-UDF-8 `json_pretty(json)`：美化输出（缩进换行）。验收：合法输入美化、非法输入行为明确
- REQ-UDF-9 `json_strip_nulls(json)`：递归移除值为 null 的字段。验收：单层/嵌套/数组内对象/null 顶层值

### 函数变更

- 无（既有 `udf_prefix`/`udaf_string_agg`/`udtf_split_rows` 为 dev 历史事实，不改动）

### 非函数项

- REQ-INFRA-1：注册命名规范落地——manifest 注册名**无 `udf_`/`udtf_` 前缀**，与 MC 原生名一致
- REQ-INFRA-2：`api-spec.yaml` 补齐 9 个函数契约（签名/语义/边界/示例 SQL）
- REQ-INFRA-3：UAT 基线复用 `lpudf.trade_order` 表（300 订单，multiLine 建表，见 inception §3.3），不适配再补造数据
- REQ-INFRA-4：README 部署说明同步更新调用约定（库内裸名 / 跨库 `lpudf.<fn>`）

## 验收标准

- **L1 单测**（构建闸门）：每个函数单测覆盖 正常值 / NULL / 空输入 / 边界 / 特殊字符（正则元字符、中文、嵌套结构），`mvn test` 全绿
- **L2 契约**：`api-spec.yaml` 与实现一一对应，无缺漏
- **L3 集群验证**（基于 `lpudf.trade_order` UAT）：
  - L3.1 注册冒烟：`SHOW FUNCTIONS IN lpudf` 可见 9 个新函数
  - L3.2 功能矩阵：逐函数跑契约示例，结果与预期一致
  - L3.3 分布式提示：小表冒烟 + 分布式路径验证（必要时造大表）
  - L3.4 持久性：重启 STS 后函数仍可用
- 与 `tasks.md` 阶段勾选对应，全部完成后 PR 合入 `dev`
