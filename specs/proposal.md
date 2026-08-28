# Proposal: spark-udf-lp — 地理函数族（迭代 6，目标 1.1.8）

> 状态: 已定稿（Approved，S0.5 通过）
> 来源: `specs/inception.md`（历史事实来源，含弯路/坑记录）/ 本期迭代需求
> 关联: 父项目 `hadoop-cluster-physical`（以 git submodule 挂载）
> 生命周期: 与当前分支开发周期绑定，新周期从空模板重新填充

## Goal

对标阿里云 MaxCompute 地理函数（ST_* 系列），开发 Spark 3.3.1 缺失的 16 个地理 UDF（`st_geogpoint` / `st_geogfromtext` / `st_geogfromwkb` / `st_astext` / `st_asbinary` / `st_x` / `st_y` / `st_boundingbox` / `st_distance` / `st_dwithin` / `st_contains` / `st_covers` / `st_intersects` / `st_within` / `st_makeline` / `st_makepolygon`），为 MC 用户提供开箱即用的空间计算能力，目标版本 1.1.8。

## 背景与动机

- **能力空白**：库内现有 59 个函数（json/string/聚合族），**无任何地理空间函数**；Spark 3.3.1 内置亦无 ST_*（`SHOW FUNCTIONS LIKE '*st*'/'*geo*'` 集群实测复核，inception §2.3），ST_* 生态在 Sedona/GeoSpark 等第三方库。
- 采用既有对标法（json/string/aggregate 同源）：MC 地理函数文档 16 个 → 集群实测差异 = 候选开发清单（**16 个全部缺失，无排除项**，详见 `inception.md` §2）。
- 约束：Spark 3.3.1 + Hive 2.3.9 API；**Spark 无 GEOGRAPHY 类型**，地理对象以 STRING(WKT) 为载体（BINARY(WKB) 为二进制接口）；空间关系语义对齐 OGC/DE-9IM（MC 同标准）。

## Requirements

### 新增函数（16 个，全部 UDF，裸名注册 ADR-8）

- **REQ-GEO-01：`st_geogpoint(lon, lat)`** — 经/纬度构造点（返回 WKT）。lon 超 [-180,180] 按 360° 归一化（270→-90）；lat 超 [-90,90] 报错。
- **REQ-GEO-02：`st_geogfromtext(wkt)`** — WKT 解析为地理对象。支持 POINT/LINESTRING/POLYGON/EMPTY，不支持 Z/M；`POINT EMPTY` 输出 `GEOMETRYCOLLECTION EMPTY`；非法 WKT 报错。
- **REQ-GEO-03：`st_geogfromwkb(wkb)`** — WKB 解析为地理对象（返回 WKT）。非法 WKB 报错。
- **REQ-GEO-04：`st_astext(geog)`** — 地理对象转 WKT。NULL→NULL；空几何→`GEOMETRYCOLLECTION EMPTY`。
- **REQ-GEO-05：`st_asbinary(geog)`** — 地理对象转 WKB 二进制。NULL→NULL。
- **REQ-GEO-06/07：`st_x(geog)` / `st_y(geog)`** — 取点对象经/纬度。非 POINT 报错；NULL→NULL。
- **REQ-GEO-08：`st_boundingbox(geog)`** — 外接矩形 `STRUCT<xmin,ymin,xmax,ymax>`。NULL/空→NULL；平面近似（微小精度差异）。
- **REQ-GEO-09：`st_distance(g1, g2)`** — 最短距离（米，haversine 球面近似 ~0.01%）。任一 NULL/空→NULL。
- **REQ-GEO-10：`st_dwithin(g1, g2, dist)`** — 最短距离 ≤ dist（米）→ TRUE。任一 NULL/空→FALSE；dist=0 判断点在多边形内。
- **REQ-GEO-11：`st_contains(g1, g2)`** — g1 包含 g2 全部点（**不含边界**）。任一 NULL/空→FALSE。
- **REQ-GEO-12：`st_covers(g1, g2)`** — g1 覆盖 g2 全部点（**含边界**）。任一 NULL/空→FALSE。
- **REQ-GEO-13：`st_intersects(g1, g2)`** — 有公共点→TRUE（hole 内不算）。任一 NULL/空→FALSE。
- **REQ-GEO-14：`st_within(g1, g2)`** — g1 全部点在 g2 内（**不含边界**）。任一 NULL/空→FALSE。
- **REQ-GEO-15：`st_makeline(g1, g2)` / `(array<geog>)`** — 构造线。任一 NULL（含数组元素 NULL）→NULL；两点相同/单元素→退化为 Point；数组 ≥1 元素。
- **REQ-GEO-16：`st_makepolygon(shell[, holes])`** — 构造多边形。shell ≥3 个不同顶点，首尾不同自动补闭合；任一 NULL（含 holes 元素 NULL）→NULL；空对象报错。

> 各函数验收锚点与示例 SQL 见 `api-spec.yaml`（S0.7 契约 = 验收基准）。

### 非函数项

- **REQ-HELP-1：16 个新函数帮助文本登记** — `@ExpressionDescription` + `LpudfFunctionRegistry.ALL` + `api-spec.yaml` 三同步；`DESC FUNCTION lpudf.<fn>` 显示 usage + arguments。
- **REQ-REG-1：注册三连同步** — 父仓库测试槽 `scripts/spark-udf-lp/udf-manifest.txt` 追加 16 行（裸名 ADR-8）+ `LpudfFunctionRegistryTest.EXPECTED_NAMES` 同步 30→46。
- **REQ-DEP-1：jts-core 依赖** — pom 引入 `org.locationtech.jts:jts-core:1.19.0`（compile，shade 打入；POC 已验证可拉取可运行）。
- **REQ-DOC-1：迭代收尾文档** — Stage 3 前父仓库 `docs/spark-udf-lp/inception/20260828-feat-geo-functions-地理函数.md` 存档；Stage 4 用户指南按需。

## 验收标准

- 与 `tasks.md` 阶段勾选、`api-spec.yaml` 契约对应。
- L1 单测全绿（构建闸门，`build.sh`）→ L2 构建产出 `target/spark-udf-lp-1.1.8.jar` → L3 集群 UAT 全绿（L3.1 注册冒烟 / L3.2 功能矩阵 / L3.3 分布式空间连接 / L3.4 持久性）。
- 语义锚点：`st_distance(st_geogpoint(0,0), st_geogpoint(1,1)) ≈ 157250`（±100m 容差）；`st_contains` 边界点 FALSE vs `st_covers` 边界点 TRUE；`st_geogpoint(270, 0)` → `POINT (-90 0)`。
