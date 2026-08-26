# Design: spark-udf-lp — desc function 帮助信息（lpudf-help）

> 状态: 已定稿（Approved）
> 关联: 基于 `proposal.md`（REQ-HELP-1~6）展开，为 SDD design 阶段产物。
> 生命周期: 与当前分支 `feat/lpudf-help` 开发周期绑定。
> 前置: POC 结论（inception §7.4）——注解对 hive 注册路径不生效；`registerFunction` 4 参注入可行；`SparkSessionExtensions.injectFunction` 为官方会话级注入钩子。本设计据此定稿。

## 1. 架构总览

在既有"单 jar + lpudf 库"骨架内**新增函数元数据（帮助信息）注入层**，不新增模块、不改变构建流水线；部署增加一处配置（`spark.sql.extensions`）。

```
[LpudfExtensions（jar 内）] ──spark.sql.extensions 配置──> [会话启动注入 injectFunction]
      │  12 个函数 × (FunctionIdentifier, ExpressionInfo, FunctionBuilder)
      ▼
[SimpleFunctionRegistry 内存注册表] ──(desc 命令)──> 三段式帮助文本
```

关键约束（POC §7.4 实测）：hive `CREATE FUNCTION` 注册路径的 ExpressionInfo.usage 硬编码 null（注解不生效）；`registerFunction(name, info, builder)` 4 参注入与 `SparkSessionExtensions.injectFunction` 为两条可行注入路径；注入条目使同名 `CREATE FUNCTION` 抛 `FunctionAlreadyExistsException`。

## 2. 模块划分

### 2.1 元数据层

- 每个函数一个 `LpudfFunctionDescription`（函数标识、`ExpressionInfo`、真实类名）——描述信息在**一处集中定义**（`LpudfFunctionRegistry` 常量清单），供扩展类与测试共用
- 函数类同时标注 `@ExpressionDescription`（文档化 + 未来 Spark 版本兼容；本期 hive 注册路径不读取）
- usage 文本格式对齐内置：`<签名> - <一句话语义>`；函数名**直接书写**——注入路径无 `_FUNC_` 占位替换机制（内置函数的 `_FUNC_` 由注册宏替换，extensions 注入路径不替换，见 ADR-12）
- 帮助文本内容以 `api-spec.yaml` 为唯一事实来源（REQ-HELP-4），单测勾稽

### 2.2 注入层（定稿：方案 A——官方 extensions 注入）

| 方案 | 描述 | 结论 |
|---|---|---|
| A. 注解直接生效 | hive 注册路径读注解 | ✗ POC 实测不生效（Usage: N/A.） |
| **B. SparkSessionExtensions.injectFunction** | `spark.sql.extensions=com.liangpu.lpudf.LpudfExtensions`，会话启动注入 12 个函数（带完整 ExpressionInfo） | ✓ **采用**（POC 实测 DESC 显示完整 Usage；官方 API、声明式、无集群源码改动） |
| C. 文档化兜底 | 仅注解 + api-spec | 不采用（不满足 REQ-HELP-2/3） |

**builder 构造（行为与 hive 注册一致）**：

- 标量函数：`(exprs) => new HiveSimpleUdf(new HiveFunctionWrapper(className), exprs)`（spark-hive 提供，provided 依赖）
- UDTF（json_explode）：`HiveGenericUDTF(new HiveFunctionWrapper(className), exprs)`（Generator 表达式，经 `injectFunction` 注册同样可被 desc 查询）
- 实现阶段验证 `HiveFunctionWrapper` 构造器签名（3.3.1）

**与既有注册的共存**（POC 实测 `FunctionAlreadyExistsException`）：

- 注入条目在 registry 中"已存在"，metastore 记录冗余无害（`functionExists` 命中即不加载 metastore）
- 部署脚本**不再执行 DROP/CREATE 这 12 个函数**（仅首次上线时清理旧 metastore 记录可选执行）；`spark.sql.extensions` 为唯一注册通道
- 会话级特性：新会话自动注入；同一会话内 DROP 后需重启会话恢复（文档说明）

## 3. 构建设计

- 无新依赖；`ExpressionDescription` 为 spark-catalyst（provided）自带
- 单测闸门照旧：`mvn test` 全绿才出 jar

## 4. 发布与注册设计

- 版本化路径 / 部署脚本 / manifest 不变（本期无新函数注册）
- 若采用注入层方案 B，需在部署说明中补充初始化步骤

## 5. 验证设计

### L1 单测

- 注解编译期生效性：反射读取 12 个类的 `ExpressionDescription`，校验 usage 非空、arguments 格式
- 行为回归：既有 139 单测全绿

### L2 契约检查

- 注解 usage/arguments 文本与 `api-spec.yaml` 描述逐条勾稽

### L3 集群 UAT（STS）

- L3.1 `DESC FUNCTION lpudf.<fn>` 12/12 显示 Usage
- L3.2 `DESC FUNCTION EXTENDED` 抽查 3 个显示 Arguments / Examples
- L3.3 功能回归 l32_json_functions.sql 19 例全绿
- L4 回归：迭代 1 函数冒烟

## 6. 关键决策记录（ADR）

| # | 决策 | 理由 | 日期 |
|---|---|---|---|
| ADR-9 | **帮助信息注入采用方案 B：`SparkSessionExtensions.injectFunction`（spark.sql.extensions）**，builder 用 `HiveSimpleUdf`/`HiveGenericUDTF` 包装真实 UDF 类；部署脚本不再 DROP/CREATE 这 12 个函数 | POC 实测：注解对 hive 注册路径不生效；injectFunction 为官方会话级钩子，DESC 显示完整帮助；注入优先与 metastore 旧记录兼容 | 2026-08-26 |
| ADR-10 | 帮助文本唯一事实来源为 `api-spec.yaml`，`LpudfFunctionRegistry` 常量清单与注解文本由契约生成/勾稽 | 避免双源漂移；REQ-HELP-4 | 2026-08-26 |
| ADR-11 | 函数类同时保留 `@ExpressionDescription` 注解（文档化 + 未来兼容），但**不以**其为生效机制 | 注解读取依赖内置注册宏，hive 注册路径不读（POC 实测）；双保险成本低 | 2026-08-26 |
| ADR-12 | 实现期偏差确认：builder 统一用 `HiveGenericUDF`/`HiveUDAFFunction`/`HiveGenericUDTF` 包装（design §2.2 原写 `HiveSimpleUdf`，§7 风险对策"必要时改用 HiveGenericUDF"已预留）；help 文本直写函数名（注入路径无 `_FUNC_` 替换）；`udf_prefix` 统一注入 lpudf 库（历史 default 库记录冗余无害） | 行为与 hive 注册路径一致（单测 142 全绿 + 集群 UAT L3.1-L3.5 全过）；保证 `DESC FUNCTION lpudf.<fn>` 12/12 契约（REQ-HELP-3）；metastore 旧记录由 registry 注入条目优先覆盖 | 2026-08-26 |

## 7. 风险与对策

| 风险 | 状态 | 结论/对策 |
|---|---|---|
| 注解对 hive 注册路径不生效 | 已消除（POC 实测） | 方案 B 注入兜底，DESC 显示帮助 |
| 注入与 metastore 注册冲突（CREATE FUNCTION 抛 FunctionAlreadyExistsException） | 已消除（POC 实测） | 部署脚本不再 SQL 注册；metastore 旧记录冗余无害（registry 优先） |
| `HiveSimpleUdf`/`HiveFunctionWrapper` 构造器签名与 3.3.1 不符 | 实现期验证 | 容器构建内单测冒烟；必要时改用 `HiveGenericUDF` 统一包装 |
| 同一会话内 DROP 函数后注入条目丢失 | 接受 | 会话级特性，重启会话恢复；文档说明（REQ-HELP-6 边界） |
| 注解随 jar 升级而失效（classloader 缓存，坑 B） | 低 | 沿用 STS 重启流程 |
