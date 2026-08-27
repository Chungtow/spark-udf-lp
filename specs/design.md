# Design: spark-udf-lp — 字符串处理函数（迭代 4）架构设计

> 状态: 待评审（Draft）
> 关联: 基于 `proposal.md` 展开，为 SDD design 阶段产物。
> 生命周期: 与当前分支开发周期绑定；跨周期稳定决策（Java8 编译/运行、hive-exec 2.3.9 provided、lpudf 唯一注册地址、ADR-3 边界 / ADR-8 命名 / ADR-9 会话级注入 / ADR-11 文档化）沿用既有 skeleton 版 design，本节仅增补本期 ADR-12~15。

## 1. 架构总览

本期**不引入任何新机制**，10 个函数全部复用既有链路（构建 → 发布 → 会话级注入 → 任意库调用），唯一的新技术点是 `regexp_extract_all` 返回 `ArrayType`（SPIKE 已验证可行）。

```
[src/main/java/com/liangpu/{udf,udtf}/] 10 个新类 + help 注册条目
        │ build.sh（builder 容器 maven，provided 依赖）
        ▼
[target/spark-udf-lp-<VER>.jar] --release_spark_udf_lp.sh--> [父仓库 software/spark-udf/]
        │ deploy_spark_udf_lp.sh（HDFS 上传 /udf/ + cp current + 幂等更新 spark-defaults.conf）
        ▼
[spark.sql.extensions=...,com.liangpu.help.LpudfExtensions] --docker restart spark--> [lpudf 库 22 函数注入]
        │
        ▼
[STS 任意库: SELECT lpudf.<fn>(...) ; DESC FUNCTION lpudf.<fn>]
```

## 2. 模块划分

### 2.1 `src/main/java/com/liangpu/{udf,udtf}/` — 函数实现（10 个新类）

| # | 注册名 | 类型 | 类 | 继承 | 备注 |
|---|---|---|---|---|---|
| 1 | `keyvalue` | UDF | `com.liangpu.udf.KeyvalueUdf` | `GenericUDF` | 2/4 参变体（`args.length` 分支） |
| 2 | `keyvalue_tuple` | UDTF | `com.liangpu.udtf.KeyvalueTupleUDTF` | `GenericUDTF` | 可变 key 参数，先例 `JsonExplodeUDTF` |
| 3 | `url_encode` | UDF | `com.liangpu.udf.UrlEncodeUdf` | `GenericUDF` | 单参 |
| 4 | `url_decode` | UDF | `com.liangpu.udf.UrlDecodeUdf` | `GenericUDF` | 单参，非法序列→NULL |
| 5 | `mask_hash` | UDF | `com.liangpu.udf.MaskHashUdf` | `GenericUDF` | SHA-256，64 字符 hex |
| 6 | `regexp_count` | UDF | `com.liangpu.udf.RegexpCountUdf` | `GenericUDF` | 2/3 参 |
| 7 | `regexp_extract_all` | UDF | `com.liangpu.udf.RegexpExtractAllUdf` | `GenericUDF` | 返回 `StandardListObjectInspector` |
| 8 | `regexp_substr` | UDF | `com.liangpu.udf.RegexpSubstrUdf` | `GenericUDF` | 2~4 参 |
| 9 | `regexp_replace_nth` | UDF | `com.liangpu.udf.RegexpReplaceNthUdf` | `GenericUDF` | 3/4 参 |
| 10 | `find_in_set_ex` | UDF | `com.liangpu.udf.FindInSetExUdf` | `GenericUDF` | 2/3 参 |

通用约定（复用迭代 2/3）：
- 所有类声明前标注 `@ExpressionDescription`（ADR-11 文档化约定，clion 锚点，usage 直写函数名）；
- 注册名无前缀（ADR-8，`lpudf` 库即命名空间）；
- NULL 入参（含 NULL 字面量的 `VoidObjectInspector`）一律放行并返回 NULL（SPIKE 发现点）；
- 参数类型检查：非预期类型 → `UDFArgumentTypeException`（写法严格，ADR-3）。

### 2.2 `src/main/java/com/liangpu/help/` — 注册与帮助（更新 2 文件）

- `LpudfFunctionRegistry.java`：`ALL` 追加 10 条（name/database=lpudf/className/kind/usage/arguments），总计 22 条；类注释同步更新；
- `LpudfExtensions.java`：**无需改动**（builderFor 按 registry 遍历，自动覆盖新函数）。

### 2.3 `builder/` / `scripts/` — 无本期变更

- `scripts/udf-manifest.txt`：追加 10 行（注册名|完整类名），UAT 遍历用；
- UAT 用例目录 `scripts/uat/`：新增 10 个函数的功能矩阵用例文件。

## 3. 构建设计

- 命令：`bash build.sh <VER>`（builder 容器内 `mvn clean package`，Java 8）；
- 依赖策略：与既有一致——spark-sql/spark-hive/hive-exec/hadoop-client 均 provided，产物仅含 UDF 类；
- 单测闸门：全量 `mvn test` 必须绿（142 基线 + 本期新增 ≈ 90 用例）——**含 api-spec 勾稽**（`jsonFunctionsMatchApiSpec`），故 api-spec 基线恢复（REQ-HELP-5）是构建转绿的前置；
- 新增依赖：无（10 个函数全部基于 JDK8 + hive-exec 2.3.9 API，无需 fastjson2 等第三方）。

## 4. 发布与注册设计

- 版本化：HDFS `/udf/spark-udf-lp-<VER>.jar`，升级 = 新版本 + deploy（不可覆盖）；
- 注入配置：`deploy_spark_udf_lp.sh` 幂等更新 `spark-defaults.conf` 的 `spark.jars` 引用至 `current`，**禁 CREATE/DROP FUNCTION**（ADR-9）；
- 重启：`docker restart spark` 后单实例加载新类（旧进程无法被 stop-thriftserver.sh 杀掉）；
- 22 个函数统一注入 `lpudf` 库，metastore 旧记录冗余无害（registry 注入条目优先）。

## 5. 验证设计

| 层级 | 内容 | 位置 |
|---|---|---|
| L1 | 每函数单测（正常/NULL/空串/边界/入参错误，≥8 用例）+ `LpudfFunctionRegistryTest` 勾稽绿 | `src/test/java`（新增 `com.liangpu.udf.*UdfTest` + `com.liangpu.udtf.KeyvalueTupleUDTFTest`） |
| L2 | 构建产物 jar 抽查（`jar tf` 无 spark/hive/hadoop 类） | `bash build.sh` |
| L3.1 | 注册冒烟：`DESC FUNCTION lpudf.<fn>` 22/22 | `scripts/spark_udf_uat.sh` |
| L3.2 | 功能矩阵：10 函数 UAT SQL 与 api-spec examples 一致 | `scripts/uat/` + UAT 报告 |
| L3.3 | 分布式提示：确定性 UDF 下推检查（Spark 日志执行计划） | 集群 UAT |
| L3.4 | 持久性：`docker restart spark` 后 22 函数可用 | 集群 UAT |

功能矩阵 UAT SQL 直接复用 api-spec examples（同源），结果与 api-spec `result` 字段对照。

## 6. 关键决策记录（ADR）

### 6.1 本期新增

| # | 决策 | 理由 | 日期 |
|---|---|---|---|
| ADR-12 | 增强型函数一律换名：`regexp_replace_nth` / `find_in_set_ex` | 同名注入无法覆盖内置裸名解析（SPIKE 实证：裸名 `regexp_replace` 解析到内置全替换）；同名增强造成语义歧义且无法实现 MC 迁移零改动 | 2026-08-27 |
| ADR-13 | `mask_hash` 自研契约：SHA-256 hex（64 字符小写），不要求与 MC 结果一致 | MC 未公开哈希算法；脱敏场景仅需"不可逆 + 固定 64 字符 + 同输入同输出"，无需跨平台对齐 | 2026-08-27 |
| ADR-14 | URL 编解码采用 JDK `URLEncoder`/`URLDecoder`（x-www-form-urlencoded），禁止手工实现 | POC 实证：手工按 char 直编非 ASCII 产出错误字节序列；JDK 实现与 MC 契约（空格→`+`、UTF-8 字节编码）一致 | 2026-08-27 |
| ADR-15 | `regexp_extract_all` 返回 `ArrayType`（`StandardListObjectInspector`）可行 | SPIKE 实证：`local[1]` 引擎注册 + SELECT 输出 `[1,2,3]`、NULL→NULL、空数组、DESC 显示均正常；风险消除 | 2026-08-27 |

### 6.2 沿用（非新增，引用既有 ADR）

- **ADR-3 边界总则**：数据宽容（非法输入→NULL/0 行）、写法严格（参数写法错误→抛错）；`url_decode` 非法百分号序列按数据问题 → NULL。
- **ADR-8 命名**：注册名无前缀，MC 原生名；`lpudf` 库即命名空间。
- **ADR-9 会话级注入**：`spark.sql.extensions` 注入，禁 CREATE/DROP。
- **ADR-11 文档化**：`@ExpressionDescription` + 帮助三件套，api-spec 唯一事实来源。

## 7. 风险与对策

| 风险 | 状态 | 结论/对策 |
|---|---|---|
| UDF 返回 ArrayType 注册/输出异常 | **已消除** | SPIKE 实证（inception §4.3），实现按 SPIKE 验证路径 |
| 同名函数解析冲突 | **已消除** | SPIKE 实证裸名解析内置 → 换名方案（ADR-12） |
| URL 编码契约偏差 | **已消除** | POC 对照 JDK 行为与 x-www-form-urlencoded 契约（inception §4.2-A） |
| `regexp_replace_nth` 后向引用/`$` 误解析 | 已收敛 | POC 实证实现要点：`repl` 原样传 `appendReplacement`、非命中段 `sb.append(m.group())`（inception §4.2-B） |
| api-spec 基线红阻塞构建 | 已知 | REQ-HELP-5 恢复 12 个已有函数条目后勾稽转绿（前置项） |
| `keyvalue` 边界语义（空段/分隔符相邻）与 MC 细微差异 | 中 | 单测锁定契约；UAT 功能矩阵对照 api-spec 验收；差异不影响主场景 |
| 22 函数注入性能/启动影响 | 低 | 注册条目仅元数据（22 条），无实例化成本，与迭代 3 12 条同机制 |
