# Design: spark-udf-lp — JSON 处理函数库（json_* 系列）

> 状态: 草稿（Draft）
> 关联: 基于 `proposal.md`（REQ-UDF-1~9 / REQ-INFRA-1~4）展开，为 SDD design 阶段产物。
> 生命周期: 与当前分支 `feat/json-processor` 开发周期绑定；跨周期稳定决策（Java8 编译/运行、hive-exec 2.3.9 provided、lpudf 唯一注册地址）见 skeleton 版 design 的 ADR，本期沿用不重写。

## 1. 架构总览

本期在既有"单 jar + lpudf 库"骨架内**新增 9 个 JSON 函数**，共享一个 JSON 解析层（JSONPath 封装）。无新增模块、无构建流水线变更（仅 pom 加依赖）。

```
[本地 Maven 源码] --build.sh(builder 容器, 单测闸门)--> [spark-udf-lp-<VER>.jar(含 fastjson2 shaded)]
    --deploy--> [HDFS /udf/] --manifest 驱动 DROP+CREATE--> [lpudf 库 9 个函数]
    --> [STS 任意库调用: 库内裸名 json_xxx / 跨库 lpudf.json_xxx] --> 解析层 com.liangpu.json.JsonSupport(JSONPath)
```

**输入形态映射**（本期核心设计决策）：MC 函数入参为强类型 `JSON`；Spark 3.3.1 无 JSON 类型，**全部函数以 STRING（JSON 文本）为入参、STRING/BOOLEAN/BIGINT 为出参**，函数内部解析。语义对齐 MC，入参形态适配（详见 ADR-3）。

## 2. 模块划分

### 2.1 `src/main/java/com/liangpu/` — 函数实现

**新增共享层 `com.liangpu.json`**（本期内新增包）：

| 类 | 职责 |
|---|---|
| `JsonSupport` | fastjson2 解析入口 + 统一异常分类（`JsonSyntaxException`=非法 JSON 文本 / `JsonPathException`=path 语法非法），屏蔽引擎细节 |
| `JsonPathSupport` | JSONPath 编译（缓存）与求值；path 子集校验（ADR-1）；区分"path 语法非法"与"目标不存在" |

**函数类**（`udf` / `udtf` 包，类名语义化，注册名无前缀）：

| 注册名 | 类名 | 包 | 签名（Spark 侧） | 对齐 MC |
|---|---|---|---|---|
| `json_valid` | `JsonValidUdf` | udf | `(string) → boolean` | JSON_VALID |
| `json_extract` | `JsonExtractUdf` | udf | `(string json, string path) → string` | JSON_EXTRACT |
| `json_length` | `JsonLengthUdf` | udf | `(string json [, string path]) → bigint` | JSON_LENGTH |
| `json_type` | `JsonTypeUdf` | udf | `(string json) → string` | JSON_TYPE |
| `json_exists` | `JsonExistsUdf` | udf | `(string json, string path) → boolean` | JSON_EXISTS |
| `json_contains` | `JsonContainsUdf` | udf | `(string json, string candidate [, string path]) → boolean` | JSON_CONTAINS |
| `json_pretty` | `JsonPrettyUdf` | udf | `(string json) → string` | JSON_PRETTY |
| `json_strip_nulls` | `JsonStripNullsUdf` | udf | `(string json [, bool include_arrays] [, bool remove_empty] [, string path]) → string` | JSON_STRIP_NULLS |
| `json_explode` | `JsonExplodeUDTF` | udtf | `(string json) → (key string, value string)` | JSON_EXPLODE |

> 注册名说明（REQ-INFRA-1）：**新函数不带 `udf_`/`udtf_` 前缀**，用 MC 原生名；既有示例函数（`udf_prefix` 等）保持原名不动。

### 2.2 `builder/` — 构建镜像

无变更（沿用 skeleton 镜像 maven:3.9-eclipse-temurin-8 + 阿里云源）。

### 2.3 `scripts/` — 发布注册（本地保留，不入库）

`udf-manifest.txt` 追加 9 行（注册名|完整类名）；其余脚本（release/deploy/uat）逻辑不变。

## 3. 构建设计

- **新增依赖**：`com.alibaba.fastjson2:fastjson2:2.0.x`（最新稳定，以中央仓库为准）——**非 provided**，shade 打入最终 jar
- **shade relocation**：`com.alibaba.fastjson2` → `com.liangpu.shaded.fastjson2`，避免与集群其他组件（含未来依赖 fastjson 的作业）类冲突
- 单测闸门照旧：`mvn test` 全绿才出 jar；`build.sh <VER>` 不变
- 既有骨架避坑项沿用：spark/hive/hadoop 均 provided，`jar tf` 抽查无 spark/hive 类；确认 shaded 后无 `com.alibaba.fastjson2` 顶包残留

## 4. 发布与注册设计

- HDFS 版本化路径 `/udf/spark-udf-lp-<VER>.jar` 不变；升级必换新 VER（不可覆盖）
- manifest 驱动 lpudf 库 `DROP FUNCTION IF EXISTS + CREATE FUNCTION`（沿用坑 A 对策）
- **jar 升级后必须重启 STS**（坑 B：classloader 类缓存）
- 调用约定（写入 README）：lpudf 库内裸名 `json_valid(...)`；跨库 `lpudf.json_valid(...)`

## 5. 验证设计

### L1 单测（构建闸门，每函数 ≥8 例）

| 函数 | 必测边界 |
|---|---|
| json_valid | 合法对象/数组/标量、裸词(非法)、空串、NULL、嵌套、数字字符串 `'123'` |
| json_extract | `$.a` / `$.a.b` / `$[2]` / `$[2].a`、键不存在→NULL、path 语法非法→抛错、键含点（`$['a.b']`）、中文键、NULL 输入 |
| json_length | 数组/对象/标量=1/JSON null=1、path 定位、嵌套不递归、NULL 输入 |
| json_type | 六种类型枚举（小写）、NULL 输入 |
| json_exists | 存在/不存在/值为 null 仍 true/数组下标/越界 false/path 非法抛错 |
| json_contains | 数组元素命中/未命中、path 值相等、path 不存在 false、json/candidate NULL→NULL、candidate 对象元素 |
| json_pretty | 对象/数组/嵌套、NULL、非法 JSON→NULL |
| json_strip_nulls | 1~4 参全组合、数组 null 删/不删、remove_empty 空对象/数组、path 限定、JSON null 输入、NULL 参数 |
| json_explode | 数组拆 N 行(key=null)/对象拆行(key=键名)、顺序保持、NULL→0 行、非法 JSON→0 行、合法非数组/对象→0 行 |

### L2 契约检查

`api-spec.yaml` 与实现/单测一一对应，签名、边界、示例 SQL 无缺漏。

### L3 集群 UAT（基于 `lpudf.trade_order`，300 订单）

| 函数 | UAT 示例 | 期望 |
|---|---|---|
| json_valid | `json_valid(to_json(struct(*)))` | true |
| json_length | `json_length(to_json(orders))` | 300 |
| json_explode | `LATERAL VIEW json_explode(to_json(orders)) t AS k,v` | 300 行 |
| json_extract | `json_extract(to_json(struct(*)),'$.meta.account')` | Had064f... |
| json_type | `json_type(to_json(struct(*)))` | object |
| json_exists | `json_exists(to_json(orders),'$[0].orderId')` | true |
| json_contains | `json_contains(to_json(orders),'"282684930001025415"')` | true |
| json_pretty | `json_pretty(to_json(meta))` | 美化多行 |
| json_strip_nulls | `json_strip_nulls(to_json(struct(*)))` | 同输入 |

L3.1 注册冒烟（`SHOW FUNCTIONS IN lpudf` 见 9 函数）→ L3.2 功能矩阵（上表）→ L3.3 分布式提示（必要时大表，关 AQE coalesce）→ L3.4 STS 重启后持久性。

### L4 回归

既有 3 个示例函数冒烟（确认无影响）。

## 6. 关键决策记录（ADR）

| # | 决策 | 理由 | 日期 |
|---|---|---|---|
| ADR-1 | **JSONPath 子集**：支持 `$` / `$.key` / `$.k1.k2` / `$[n]` / `$.k[n]` / `$[n].k` / `$['key']`（特殊字符键）；**不支持**通配符 `*`、递归 `..`、过滤 `[?()]` | 对齐 MC 文档列出的 path 写法；`$['key']` 为超集扩展（键含点/中文/空格必需）；复杂 path 本期不做，预留扩展 | 2026-08-26 |
| ADR-2 | **解析引擎 fastjson2**（shaded）：单 jar、无强依赖、JSONPath 能力匹配、阿里同源语义最贴近 MC | 备选 jackson 无原生 JSONPath（自写解析器工作量大）；gson 无 JSONPath；fastjson1 有 autoType 历史问题（fastjson2 已重构修复，选 2.0.x 最新） | 2026-08-26 |
| ADR-3 | **"数据宽容、写法严格"**：非法 JSON 文本（数据问题）→ NULL / UDTF 0 行，**不炸任务**；非法 path 语法（SQL 写法问题）→ 抛异常，与 MC 报错对齐 | MC 以 JSON 强类型在解析期拦截非法数据；Spark STRING 入参无法在类型层拦截，脏数据在日志/kafka 场景是常态，抛错会炸整任务 | 2026-08-26 |
| ADR-4 | **`json_contains` 命名与全语义**（含可选 path）：覆盖 MC JSON_CONTAINS 数组元素匹配 + path 节点相等两场景（proposal REQ-UDF-7 由 `json_array_contains` 升级，**已确认**） | 命名决议"MC 原生名"；MC 无 json_array_contains，json_contains 可迁移零改动 | 2026-08-26 |
| ADR-5 | `json_pretty` 输出对齐 MC 示例：4 空格缩进、键值冒号后无空格；非法 JSON → NULL | 对齐 MC 展示格式；NULL 遵循数据宽容 | 2026-08-26 |
| ADR-6 | `json_strip_nulls` 支持 1~4 参：`include_arrays` 默认 true、`remove_empty` 默认 false、`path` 仅限第 4 参；JSON `null` 输入返回 `null`；移除后空返回 `null` | 对齐 MC 签名与参数规则 | 2026-08-26 |
| ADR-7 | `json_explode` 固定输出两列 `(key STRING, value STRING)`，value 为 JSON 文本；同条内顺序保持 | 对齐 MC 输出形态（MC value 为 JSON 类型，Spark 侧以 STRING 文本呈现） | 2026-08-26 |
| ADR-8 | **注册名无前缀**（本期 9 函数）：lpudf 库即命名空间，函数名只管语义 | 命名决议（inception §3.1）；与 MC 一致降低迁移成本 | 2026-08-26 |

## 7. 风险与对策

| 风险 | 状态 | 结论/对策 |
|---|---|---|
| fastjson2 与 MC 的 JSONPath 语义细节差异（如 `$a` 非法 path 是否报错、键含点处理） | 评估 | POC 先行：编码前用单测验证 path 子集行为，差异项在 api-spec 边界中显式声明 |
| fastjson2 安全/版本 | 评估 | 选 2.0.x 最新稳定（autoType 默认关闭），shade 重定位，规避供应链与类冲突 |
| shade 后与 Spark 自带 jackson 等类冲突 | 低 | relocation 隔离；`jar tf` 抽查无 `com.alibaba.fastjson2` 顶包 |
| UDTF 对脏数据抛错炸任务（若选严格模式） | 已消解 | ADR-3 数据宽容：非法 JSON → 0 行 |
| 函数名与未来 Spark 内置同名（json_exists 等） | 低 | 注册于 lpudf 命名空间，裸名仅限库内；README 声明调用约定（inception §3.1） |
| MC 文档部分边界未明示（json_valid 的 NULL、json_contains 对象候选等） | 确认 | 以 ADR-3 原则 + 常识语义补全，并在 api-spec 显式声明；集群 UAT 复核 |
