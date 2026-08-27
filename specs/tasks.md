# Tasks: spark-udf-lp — 字符串处理函数（迭代 4）任务清单

> 状态: 已完成（迭代 4 收尾，UAT 全绿）
> 基于 `proposal.md`（REQ-UDF-1~9 / REQ-UDTF-1 / REQ-HELP-5 / REQ-BUILD-1）与 `design.md` 拆解。
> 约定: `- [x]` 已完成，`- [ ]` 待执行。
> 生命周期: 与当前分支开发周期绑定；阶段 0/1 为仓库基线历史事实，保留不动。
> 执行顺序: 阶段 2.3（api-spec 基线恢复，构建转绿前置）可先行，其余按 P0 → P1。

## 阶段 0: 项目骨架（基线，已完成）

- [x] GitHub 建仓（Chungtow/spark-udf-lp）+ 父项目 submodule 挂载
- [x] `pom.xml`（Java 8 / spark 3.3.1 / hive 2.3.9 / hadoop 3.1.4，均 provided）+ 目录结构 + README
- [x] `builder/` 构建镜像 + `build.sh`
- [x] 首个 UDF 实现 + JUnit 单测（L1 闸门）

## 阶段 1: 发布与注册链路（基线，已完成）

- [x] HDFS `/udf/spark-udf-lp-<VER>.jar` 版本化上传
- [x] `lpudf` 库统一注册（唯一注册地址，DROP+CREATE）
- [x] `scripts/` 移出版本控制（集群耦合，防泄露）

## 阶段 2: 本期迭代（字符串处理函数，10 个）

### 2.1 P0 函数（REQ-UDF-1~4 / REQ-UDTF-1）

- [x] `keyvalue`（REQ-UDF-1）：`KeyvalueUdf` 实现（2/4 参）+ 单测（2 参默认分隔符 / 4 参自定义 / key 不存在 / 空段 / NULL）
- [x] `keyvalue_tuple`（REQ-UDTF-1）：`KeyvalueTupleUDTF` 实现（可变 key 多列）+ 单测（多 key / key 缺失→NULL / 非 kv 结构 0 行 / NULL）
- [x] `url_encode`（REQ-UDF-2）：`UrlEncodeUdf` 实现（JDK URLEncoder）+ 单测（空格→+ / 保留字符 / 中文 UTF-8 / NULL）
- [x] `url_decode`（REQ-UDF-3）：`UrlDecodeUdf` 实现（JDK URLDecoder + 非法序列→NULL）+ 单测（对称性 / +→空格 / 非法 % / NULL）
- [x] `mask_hash`（REQ-UDF-4）：`MaskHashUdf` 实现（SHA-256 hex 64 字符）+ 单测（固定长度 / 同输入同输出 / 非字符串→NULL / NULL）

### 2.2 P1 函数（REQ-UDF-5~9）

- [x] `regexp_count`（REQ-UDF-5）：`RegexpCountUdf` 实现（2/3 参 + region 起始位置）+ 单测（全量计数 / fromPos / 越界→0 / NULL）
- [x] `regexp_extract_all`（REQ-UDF-6）：`RegexpExtractAllUdf` 实现（返回 ArrayType，group 参数）+ 单测（多值 / 贪婪分段 / group 提取 / 空数组 / NULL）
- [x] `regexp_substr`（REQ-UDF-7）：`RegexpSubstrUdf` 实现（2~4 参 + occurrence）+ 单测（默认 / fromPos / occurrence / 无匹配→NULL / NULL）
- [x] `regexp_replace_nth`（REQ-UDF-8）：`RegexpReplaceNthUdf` 实现（nth 替换 + `\1` 后向引用）+ 单测（nth / 后向引用 / 超出次数原样返回 / 含 `$` 原串 / NULL）
- [x] `find_in_set_ex`（REQ-UDF-9）：`FindInSetExUdf` 实现（2/3 参自定义分隔符）+ 单测（位置 / 自定义分隔符 / 找不到→0 / NULL→0）

### 2.3 帮助信息三件套 + 构建闸门（REQ-HELP-5 / REQ-BUILD-1）

- [x] `specs/api-spec.yaml` 恢复迭代 1~3 的 12 个基线函数条目 + 新增 10 条（共 22 条，status 全部 released）
- [x] `scripts/udf-manifest.txt` 追加 10 行（注册名|完整类名），总计 22 行
- [x] `LpudfFunctionRegistry.java` 追加 10 条（usage/arguments 与 api-spec 一致），`registryCoversAllFunctions` 更新为 22 个
- [x] 全量 `mvn test` 绿（258 用例全过，含 api-spec 勾稽）→ 构建闸门 `bash build.sh 1.1.3` 通过

## 阶段 3: 验证与回归

- [x] L1 单测全绿（构建闸门，`mvn test` 258 全过）
- [x] L2 构建产物检查（`jar tf` 抽查无 spark/hive/hadoop 类混入）
- [x] L3.1 集群注册冒烟：`DESC FUNCTION lpudf.<fn>` 22/22 显示帮助
- [x] L3.2 集群功能矩阵：10 个新函数 UAT SQL 结果与 api-spec examples 一致
- [x] L3.3 分布式提示：核心函数（`keyvalue` / `regexp_extract_all` / `regexp_replace_nth`）执行计划可下推
- [x] L3.4 持久性：`docker restart spark` 后 22 个函数仍可用
- [x] UAT 报告存档：`docs/uat/20260827-spark-udf-lp-UAT测试报告.md`

## 发布流水线（阶段 3 通过后执行）

```bash
bash build.sh <VER>
bash scripts/release_spark_udf_lp.sh <VER>
bash scripts/deploy_spark_udf_lp.sh <VER>
ssh hivespark03 "docker restart spark" && sleep 30
bash scripts/spark_udf_uat.sh <VER>
```
