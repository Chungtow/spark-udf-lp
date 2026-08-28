# Tasks: spark-udf-lp — 任务清单

> 状态: 进行中（In Progress，迭代 6：地理函数，目标 1.1.8）
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

## 阶段 2: 本期迭代（地理函数族，目标 1.1.8）

### 编码

- [x] **S1.1 实现 GeoUtils + 16 个 UDF**（REQ-GEO-01~16）— `src/main/java/com/liangpu/udf/geo/`：`GeoUtils`（parseWkt/parseWkb/toWkt/normalizeLon/haversineMeters/ensureClosed/hex↔bytes）+ `StGeogpointUdf` / `StGeogfromtextUdf` / `StGeogfromwkbUdf` / `StAstextUdf` / `StAsbinaryUdf` / `StXUdf` / `StYUdf` / `StBoundingboxUdf` / `StDistanceUdf` / `StDwithinUdf` / `StContainsUdf` / `StCoversUdf` / `StIntersectsUdf` / `StWithinUdf` / `StMakelineUdf` / `StMakepolygonUdf`
  - 全部继承 `GenericUDF`；每个类标注 `@ExpressionDescription(usage=..., arguments=...)`（ADR-11）
  - 关键语义：经度归一化（270→-90）/ contains 不含边界 vs covers 含边界 / 空几何 → GEOMETRYCOLLECTION EMPTY / haversine 米制距离（锚点 ±100m）/ makepolygon 自动闭合 + ≥3 顶点校验 / st_x·st_y 非 POINT 报错 / st_makeline 退化 Point / BINARY 入参 BytesWritable（§design §2.1）
  - 修订：`GeoUtils.parseWkt/parseWkb` 捕获 checked `ParseException`（JTS read 签名）；`StGeogpointUdf`/`StMakelineUdf` 退化输出统一走 `toWkt`（对齐契约 "POINT (2 4)" 整数化）
- [x] **S1.2 单测**（REQ-GEO-01~16 验收）— 16 个测试类 + `GeoUtilsTest`；每个 UDF ≥6 用例：正常 / 空输入 / NULL 传播 / 边界语义 / 归一化 / 锚点 / 报错；直接驱动 `evaluate`（§10.2）；`GeoPocTest` 收敛或删除
  - 495 用例全绿（含既有 30+ 函数回归）：`mvn test` BUILD SUCCESS
  - 已删除 `GeoPocTest`（9 项验证全部由正式测试覆盖）
- [x] **S1.3 帮助文本登记**（REQ-HELP-1）— `LpudfFunctionRegistry.ALL` 追加 16 条（Kind.UDF，usage/arguments 对齐 api-spec description）
- [x] **S1.4 注册三连同步**（REQ-REG-1）— `scripts/udf-manifest.txt` 追加 16 行 `<裸名>|com.liangpu.udf.geo.<类名>`（st_geogpoint / st_geogfromtext / st_geogfromwkb / st_astext / st_asbinary / st_x / st_y / st_boundingbox / st_distance / st_dwithin / st_contains / st_covers / st_intersects / st_within / st_makeline / st_makepolygon）+ `LpudfFunctionRegistryTest.EXPECTED_NAMES` 同步 30→46（勾稽测试 `iteration6GeoFunctionsMatchApiSpec` 通过）

### 构建

- [x] **S2.1 构建**（L2 检查）— `bash build.sh 1.1.8`（容器化，§13.1）：L1 单测全绿（498 例）→ `target/spark-udf-lp-1.1.8.jar`（3.3M）；`jar tf` 抽查含 org/locationtech/jts（723 类）+ 16 个 geo UDF + GeoUtils，无 spark/hive 类
  - 修订：数值入参统一 `GeoUtils.toDouble`（兼容 Spark Writable：集群报 IntWritable CCX）；st_boundingbox struct 返回改 writable OI + DoubleWritable（集群报 java Double→DoubleWritable CCX）

### 发布注册

- [x] **S2.2 部署** — `deploy_spark_udf_lp.sh 1.1.8`：HDFS 版本化路径 `/udf/spark-udf-lp-1.1.8.jar` + `current.jar` 重指；spark-defaults.conf 幂等更新（spark.jars / spark.sql.extensions）
- [x] **S2.3 重启 STS**（§10.4 坑 B：classloader 缓存旧类）— 首轮 `stop-thriftserver.sh` 因容器缺 `ps` 未真正杀进程（旧类仍加载），改用 **`docker restart spark` 整容器重启**（deploy 方式 1）后生效；就绪连接端口 **10015**

## 阶段 3: 验证与回归

- [x] **L3.1 注册冒烟** — UAT `spark_udf_uat.sh 1.1.8` 全绿（exit=0）：46/46 `DESC FUNCTION lpudf.<fn>` 三段式帮助（Function/Class/Usage）
- [x] **L3.2 功能矩阵** — UAT L3.3 功能回归 88 例全绿（含 `l32_geo_functions.sql` 29 例：st_distance 锚点 157249.38 ±100m；contains 边界 FALSE / covers 边界 TRUE；geogpoint 270→-90；LE/BE WKB；bbox struct 四字段）
- [x] **L3.3 分布式验证** — `scripts/uat/l33_distributed.sql` 全绿（制品 1.1.9）：500 万行 POINT 由 `range(5000000)` 分布式生成（count 与 distinct 均 5000000，无丢失重复），对 `st_dwithin` 空间连接（AQE/coalesce 关闭 + `autoBroadcastJoinThreshold=-1` 强制 SMJ + 整数格索引 lon_idx/lat_idx 等值键触发真实 shuffle + dwithin 精过滤）；**64 分区（分布式）与 1 分区（单机串行口径）结果 checksum 完全一致** `(12497500000, 5000, 1, 1)`；锚点可解析：原点 8km 邻域命中 9 格点、5000 探针每枚命中 1（TOTALHIT=5000）；500 万行逐点 `st_distance` 聚合 min=0.0 / max=6727437.14 / avg=3786663.14。期间修复（1.1.9）：`GeoUtils.toDouble` 兼容 `HiveDecimalWritable`——嵌套 UDF 调用（`st_dwithin(st_geogpoint(...), ..., 8000)`）触发 Spark 常量折叠路径时数值字面量被 Hive 包装为该类型，修复前 `IllegalArgumentException: 无法转换为 double`
- [x] **L3.4 持久性** — UAT 前 `docker restart spark` 后 46/46 函数仍可用（UAT L3.5 ✓）
- [x] **L4 回归** — UAT L3.3 旧函数矩阵（字符串 24 / JSON 19 / UDAF 11 / 函数矩阵 5）全量回归通过，无回归
- [ ] **收尾**（REQ-DOC-1）— `docs/inception/20260828-feat-geo-functions-地理函数.md` 存档；git squash merge → dev、tag 1.1.8（含 1.1.9 修复）、更新版本轨迹；L3.3 分布式验证已勾选完成
