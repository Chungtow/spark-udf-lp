# Proposal: spark-udf-lp — desc function 帮助信息（lpudf-help）

> 状态: 草稿（Draft）
> 来源: `specs/inception.md`（§7 反编译研究：内置函数帮助信息实现链路 + UDF 注册路径限制）
> 关联: 迭代 2（9 个 JSON 函数）已合入 dev；本期为纯元数据增强，不改函数行为
> 生命周期: 与当前分支 `feat/lpudf-help` 开发周期绑定

## Goal

让 lpudf 库全部函数（迭代 1 示例 3 个 + 迭代 2 JSON 函数 9 个）在 `DESC FUNCTION` 下显示与 Spark 内置函数一致的三段式帮助信息（Function / Class / Usage），`DESC FUNCTION EXTENDED` 下显示 Arguments / Examples / Note，沉淀为函数库的**标准元数据规范**。

## 背景与动机

- **现状差异**：`DESC FUNCTION ilike`（内置）显示完整 Usage；`DESC FUNCTION lpudf.json_pretty` 仅 Function / Class，无 Usage
- **内置链路已查明**（反编译 spark-catalyst_2.12-3.3.1.jar，详见 inception §7）：
  1. 编译期：函数类标注 `@ExpressionDescription`（usage / arguments / note 字段，`_FUNC_` 占位符）
  2. 注册期：`FunctionRegistryBase.expressionInfo(name, db, ClassTag)` 反射读取注解，`_FUNC_` 替换为实际函数名，构造 `ExpressionInfo` 存入内存注册表
  3. 展示期：`DescribeFunctionCommand` 从注册表取 `ExpressionInfo` 渲染
- **UDF 注册路径限制**：hive 风格 `CREATE FUNCTION` 注册走 `SimpleFunctionRegistry` 的 `makeExprInfoForHiveFunction`，其 usage 参数**硬编码为 null**（反编译字节码 offset 34 `aconst_null` 铁证）→ 帮助信息为空
- **复用结论**：`@ExpressionDescription` 注解是内置函数的**编译期元数据**，注册宏在注册时读取；UDF 注册路径不读注解。**直接复用不可行**，已通过 POC 实测确认并选定注入方案（详见 design ADR-9 与 inception §7.4）

## Requirements

- REQ-HELP-1：12 个函数类均标注 `@ExpressionDescription`（usage 必填；arguments 可选）；帮助文本**直接书写函数名**（注入路径无 `_FUNC_` 占位替换机制，ADR-11）
- REQ-HELP-2：`DESC FUNCTION lpudf.<fn>` 显示 Function / Class / Usage 三段（与内置对齐）
- REQ-HELP-3：`DESC FUNCTION EXTENDED lpudf.<fn>` 显示 Arguments / Examples / Note（与内置对齐）
- REQ-HELP-4：帮助文本与 `api-spec.yaml` 契约一致（同一事实来源，人工勾稽）
- REQ-HELP-5：纯元数据增强——不改变函数签名 / 行为 / 注册名 / 调用约定；既有功能矩阵回归全绿
- REQ-HELP-6：实现方案不改动 Spark 源码；集群侧配置变更最小化且可脚本化——仅新增 `spark.sql.extensions=com.liangpu.lpudf.LpudfExtensions`（经部署脚本/文档明示，ADR-9），函数注册通道由 SQL `CREATE FUNCTION` 切换为 extensions 注入

## 验收标准

- **L1 构建闸门**：注解编译通过，`mvn test` 全绿（无行为回归）
- **L2 契约**：`api-spec.yaml` 帮助文本与注解内容逐字一致（抽取比对脚本或人工核对）
- **L3 集群验证**（STS，lpudf 库）：
  - L3.1 `DESC FUNCTION lpudf.<fn>` 每个函数均显示 Usage（12/12）
  - L3.2 `DESC FUNCTION EXTENDED lpudf.<fn>` 显示 Arguments / Examples（抽查 3 个）
  - L3.3 功能回归：迭代 2 功能矩阵（l32_json_functions.sql 19 例）全绿
- 与 `tasks.md` 勾选对应，完成后 PR 合入 `dev`
