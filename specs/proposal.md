# Proposal: spark-udf-lp — 字符串处理函数（迭代 4）

> 状态: 待评审（Draft → Approved 后进入 design/api-spec/tasks）
> 来源: `specs/inception.md`（历史事实来源，含 POC/SPIKE 实证）/ 本期迭代需求
> 关联: 父项目 `hadoop-cluster-physical`（以 git submodule 挂载）
> 生命周期: 与当前分支开发周期绑定，新周期从空模板重新填充
> 分支: `feat/string-processor`（迭代 4）

## Goal

为 Spark 3.3.1（锁定版本）补齐 MaxCompute 有而 Spark 缺失的**字符串处理函数**：本轮 MVP 交付 10 个函数（9 UDF + 1 UDTF），语义对齐 MaxCompute（降低 MC → LP 迁移成本），服务 web 日志 / kafka 消息 / 数仓脱敏场景，并同步登记帮助信息三件套（`udf-manifest.txt` / `LpudfFunctionRegistry` / `api-spec.yaml`）。

## 背景与动机

- **现状缺口**：Spark 3.3.1 内置字符串函数约 30 个，对照 MC 53 个函数清单，`keyvalue` / `url_encode` / `url_decode` / `mask_hash` 等完全缺失；`regexp_count` / `regexp_extract_all` / `regexp_substr` 在 Spark 3.5 才引入、`regexp_instr` 4.0 才引入（锁定版本 3.3.1 下均为缺失）。
- **场景刚需**：web 日志 URL 编解码与 query 串 kv 提取、kafka 消息字段清洗（正则计数/多值提取）、父项目数仓脱敏（`mask_hash`）。
- **约束条件**：Spark 3.3.1 不可升级；Java 8 编译/运行；注册走 `LpudfExtensions` 会话级注入（ADR-9，迭代 3 机制就绪，禁 CREATE/DROP）；边界总则复用 ADR-3（数据宽容、写法严格）。
- **可行性已实证**：POC + 引擎级 SPIKE 完成（见 `inception.md` §4），最高风险项（UDF 返回 ArrayType）已验证可行；换名决策获同名解析实证背书。

## Requirements

> 编号规则：REQ-UDF-*（UDF）/ REQ-UDTF-*（UDTF）；非函数项沿用迭代 3 的 REQ-HELP-* 序列 + REQ-BUILD-*。
> 契约细节（参数默认值、NULL/边界行为）以 `api-spec.yaml` 为唯一事实来源，此处为需求级定义。
> 所有函数注册名统一无前缀（ADR-8，`lpudf` 库即命名空间），注册于 `lpudf` 库，任意库以 `lpudf.<函数名>` 引用。

### 新增函数（10 个）

#### REQ-UDF-1：`keyvalue`（P0）—— 半结构化 kv 提取

- 功能：从 kv 串中提取指定 key 的值。两种形式：2 参 `keyvalue(str, key)` 用默认分隔符（`&` 分隔键值对、`=` 分隔 key/value）；4 参 `keyvalue(str, split1, split2, key)` 自定义分隔符。
- 验收：命中 key 返回对应 value；key 不存在 / 分隔符不合法 → NULL；str 为 NULL → NULL；单测覆盖 2 参/4 参/多值串/空段。

#### REQ-UDF-2：`url_encode`（P0）—— URL 百分号编码

- 功能：按 `application/x-www-form-urlencoded` 契约编码（JDK `URLEncoder.encode(s, "UTF-8")`，勿手工实现——POC 证实按 char 直编产出错误结果）。
- 验收：空格→`+`、`-_.` 保留、`*` 不编码、`~`→`%7E`、中文按 UTF-8 字节编码；NULL → NULL。

#### REQ-UDF-3：`url_decode`（P0）—— URL 百分号解码

- 功能：`url_encode` 的逆操作（JDK `URLDecoder.decode(s, "UTF-8")`），`+`→空格。
- 验收：编码-解码对称；非法百分号序列（数据问题）→ NULL（数据宽容，捕获 `IllegalArgumentException`）；NULL → NULL。

#### REQ-UDF-4：`mask_hash`（P0）—— 脱敏 hash

- 功能：对字符串做不可逆脱敏哈希，输出固定 64 字符十六进制。MC 未公开算法 → 自研契约：SHA-256 的 hex 表示（32 字节 → 64 字符，小写），**不要求与 MC 结果一致**（ADR-13）。
- 验收：同输入同输出、不同输入输出不同（碰撞概率低）；输出恒为 64 字符小写 hex；NULL → NULL；非字符串类型入参 → NULL。

#### REQ-UDF-5：`regexp_count`（P1）—— 正则匹配计数

- 功能：统计正则匹配次数，可指定起始位置（1-based）。2 参 `regexp_count(str, pattern)`；3 参 `regexp_count(str, pattern, fromPos)`。
- 验收：全量计数正确；fromPos 从 1-based 起始（POC 实证：`'a1b2c3'` 位置 3 起计数 = 2）；无匹配 → 0；fromPos 越界不抛错 → 0；NULL → NULL。

#### REQ-UDF-6：`regexp_extract_all`（P1）—— 正则全量提取（返回 array）

- 功能：返回所有匹配子串的 `array<string>`。3 参形式 `regexp_extract_all(str, pattern[, group])` 按组提取，group 默认 0（全匹配）。
- 验收：返回 array 类型注册 + SELECT 输出正常（SPIKE 实证）；多值/贪婪分段匹配正确；无匹配 → 空数组；NULL → NULL。

#### REQ-UDF-7：`regexp_substr`（P1）—— 正则子串（起始位置/出现次数）

- 功能：返回正则匹配的子串，支持起始位置与第 n 次出现。`regexp_substr(str, pattern[, fromPos[, occurrence]])`，fromPos 默认 1、occurrence 默认 1。
- 验收：`regexp_substr('abc123def456','[0-9]+')` → `123`；occurrence=2 → `456`；无匹配 → NULL；NULL → NULL。

#### REQ-UDF-8：`regexp_replace_nth`（P1，增强换名）—— 只替换第 nth 次匹配

- 功能：MC `regexp_replace` occurrence 参数的增强实现。因与 Spark 内置 `regexp_replace` 同名无法共存覆盖（SPIKE 实证裸名解析内置），**换名**为 `regexp_replace_nth`。`regexp_replace_nth(str, pattern, repl[, occurrence])`，occurrence 默认 1。
- 验收：只替换第 nth 次匹配，其余保留；`repl` 支持 `\1` 后向引用（原样传入 `appendReplacement`，禁 `quoteReplacement`——POC 实证）；非命中段手工追加（防 `$` 误解析）；超出匹配次数 → 原样返回；NULL → NULL。

#### REQ-UDF-9：`find_in_set_ex`（P1，增强换名）—— 自定义分隔符的 find_in_set

- 功能：MC `find_in_set` 支持自定义 delimiter 的增强实现。因与内置 `find_in_set` 同名冲突，**换名**为 `find_in_set_ex`。`find_in_set_ex(str, str_list[, delimiter])`，delimiter 默认逗号（与内置行为一致）。
- 验收：返回 str 在 str_list 中的位置（1-based）；找不到 → 0；第 3 参自定义分隔符生效；str / str_list 为 NULL → 0（与内置一致）；单测锁定边界行为。

#### REQ-UDTF-1：`keyvalue_tuple`（P0）—— kv 多键一次提取

- 功能：UDTF，一次提取多个 key 的值，每 key 一列。`keyvalue_tuple(str, split1, split2, key1, key2, ...)`（至少 4 参，split1/split2 必填）。
- 验收：多 key 输出为多列（每 key 一列，列序与参数一致）；找不到的 key → NULL；str 为 NULL / 非 kv 结构 → 0 行；复用迭代 2 UDTF 链路（`JsonExplodeUDTF` 先例）。

### 非函数项

#### REQ-HELP-5：新函数帮助信息登记 + api-spec 基线恢复

- 功能：10 个新函数同步登记帮助三件套（`udf-manifest.txt` / `LpudfFunctionRegistry` / `api-spec.yaml`）；同时**恢复 api-spec.yaml 中迭代 3 的 12 个已有函数条目**（分支重置时被清空），使 `LpudfFunctionRegistryTest.jsonFunctionsMatchApiSpec` 勾稽转绿（当前为已知基线红）。
- 验收：`udf-manifest.txt` 22 行（12 + 10）；`LpudfFunctionRegistry.ALL` 22 条且 `registryCoversAllFunctions` 绿；api-spec 22 条与 registry 勾稽绿；集群 `DESC FUNCTION lpudf.*` 22/22。

#### REQ-BUILD-1：新增单测并入构建闸门

- 功能：10 个函数每函数 JUnit 单测（正常/null/边界/入参错误），并入 L1 构建闸门。
- 验收：全量单测绿（142 + 新增），`bash build.sh <VER>` 通过。

## 验收标准

- **L1 单测**：10 个函数全部单测覆盖（每函数 ≥ 8 用例：正常/NULL/空串/边界/入参错误），全量单测绿（构建闸门）。
- **契约一致性**：`api-spec.yaml` 为唯一事实来源；`LpudfFunctionRegistryTest` 三件套勾稽绿（含 api-spec 基线恢复）。
- **L2 构建**：`bash build.sh <VER>` 产物 `target/spark-udf-lp-<VER>.jar`，`jar tf` 抽查无 spark/hive/hadoop 类混入。
- **L3 集群验证**（`bash scripts/spark_udf_uat.sh <VER>`）：
  - L3.1 注册冒烟：`DESC FUNCTION lpudf.<fn>` 22/22 显示帮助；
  - L3.2 功能矩阵：10 个新函数各 ≥ 1 条 UAT SQL 结果与 api-spec examples 一致；
  - L3.3 分布式提示：核心函数（`keyvalue` / `regexp_extract_all` / `regexp_replace_nth`）在 Spark SQL 执行计划中可下推（确定性函数提示）；
  - L3.4 持久性：`docker restart spark` 后 22 个函数仍可用（注入配置幂等）。
- 验收口径与 `tasks.md` 阶段勾选、`api-spec.yaml` 契约一一对应。
