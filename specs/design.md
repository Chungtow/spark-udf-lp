# Design: spark-udf-lp — 地理函数族（迭代 6，目标 1.1.8）

> 状态: 已定稿（Approved）
> 关联: 基于 `proposal.md` 展开，为 SDD design 阶段产物。
> 生命周期: 与当前分支开发周期绑定；跨周期稳定决策（Java8 编译/运行、hive-exec 2.3.9 provided、lpudf 唯一注册地址、注册名裸名 ADR-8 等）见既有 design 的 ADR。

## 1. 架构总览

本期**新增 1 个地理引擎依赖（JTS）+ 1 个工具类 + 16 个 UDF 实现类**，不改构建/部署链路：

```
[com.liangpu.udf.geo/GeoUtils + 16 个 *Udf] --build.sh 1.1.8(builder 容器, JTS shade 打入)--> [spark-udf-lp-1.1.8.jar]
  --> deploy_spark_udf_lp.sh --> [HDFS /udf/] --DROP+CREATE--> [lpudf 库(裸名注册)]
  --> LpudfExtensions 会话级注入帮助信息 --> [STS 任意库 lpudf.<fn> 调用]
```

## 2. 模块划分

### 2.1 `src/main/java/com/liangpu/udf/geo/` — 新包：16 个 UDF + 工具类

**`GeoUtils`（静态工具）**：

| 方法 | 职责 |
|---|---|
| `Geometry parseWkt(String)` / `parseWkb(byte[])` | WKT/WKB → JTS Geometry，非法抛异常 |
| `String toWkt(Geometry)` | JTS → WKT；**空几何统一输出 `GEOMETRYCOLLECTION EMPTY`**（MC 语义） |
| `double normalizeLon(double)` | `((lon+180)%360+360)%360-180` 归一化（270→-90） |
| `double haversineMeters(lon1,lat1,lon2,lat2)` | 球面大圆距离（R=6371000），锚点 0,0→1,1 ≈ 157250 |
| `Coordinate[] ensureClosed(Coordinate[])` | 首尾不同自动补闭合点（MC st_makepolygon 语义） |
| `byte[] hexToBytes(String)` / `String bytesToHex(byte[])` | WKB 二进制 ↔ hex（st_asbinary/st_geogfromwkb） |

**16 个 UDF 映射表**（全部继承 `GenericUDF`，裸名注册 ADR-8，类名语义化）：

| 注册名 | 实现类 | 入参（OI） | 返回 |
|---|---|---|---|
| `st_geogpoint` | `StGeogpointUdf` | double, double | Text (WKT POINT) |
| `st_geogfromtext` | `StGeogfromtextUdf` | string | Text |
| `st_geogfromwkb` | `StGeogfromwkbUdf` | binary | Text |
| `st_astext` | `StAstextUdf` | string | Text |
| `st_asbinary` | `StAsbinaryUdf` | string | BytesWritable |
| `st_x` / `st_y` | `StXUdf` / `StYUdf` | string (POINT) | DoubleWritable |
| `st_boundingbox` | `StBoundingboxUdf` | string | List + StandardStructObjectInspector |
| `st_distance` | `StDistanceUdf` | string, string | DoubleWritable |
| `st_dwithin` | `StDwithinUdf` | string, string, double | BooleanWritable |
| `st_contains` / `st_covers` / `st_intersects` / `st_within` | `StContainsUdf` / `StCoversUdf` / `StIntersectsUdf` / `StWithinUdf` | string, string | BooleanWritable |
| `st_makeline` | `StMakelineUdf` | (point, point) 或 array | Text (WKT) |
| `st_makepolygon` | `StMakepolygonUdf` | array shell[, array holes] | Text (WKT) |

**关键语义实现表**（契约 → 实现）：

| 契约点 | 实现 |
|---|---|
| contains 不含边界 / covers 含边界 | JTS `contains()` / `covers()`（DE-9IM 同 MC） |
| POINT EMPTY → GEOMETRYCOLLECTION EMPTY | `GeoUtils.toWkt` 空几何特判 |
| 经度 270 → -90 | `GeoUtils.normalizeLon` |
| st_distance 米制 | haversine（平面 JTS 距离不可用）；任一空/ NULL → NULL |
| st_dwithin dist=0 | 距离 ≤0 即 intersect/contain 语义 |
| st_makeline 退化 Point | 两点相同/数组 1 元素 → 输出 POINT WKT |
| st_makepolygon 自动闭合 | `GeoUtils.ensureClosed` + 校验 ≥3 个不同顶点，否则报错 |
| st_x/st_y 非 POINT | JTS `getX/getY` 前校验 `instanceof Point`，否则报错 |
| WKB 参数（Spark BINARY） | `BytesWritable` → byte[] → WKBReader |
| NULL 传播 | 任一入参 NULL → 按契约返回 NULL/FALSE（各函数按 api-spec） |

### 2.2 `pom.xml` — 新依赖

- `org.locationtech.jts:jts-core:1.19.0`（compile 作用域，shade 打入；与 fastjson2 同模式，POC 已验证）。

### 2.3 发布注册（脚本位于父仓库 `scripts/spark-udf-lp/`）

- 父仓库 `scripts/spark-udf-lp/udf-manifest.txt` 追加 16 行 `<裸名>|com.liangpu.udf.geo.<类名>`；部署/UAT 脚本（deploy_spark_udf_lp.sh / spark_udf_uat.sh / release_spark_udf_lp.sh）位于父仓库 `scripts/spark-udf-lp/`，无改动。

## 3. 构建设计

- `bash build.sh 1.1.8`（容器化，禁止宿主机 mvn，§13.1）：L1 单测闸门 → `target/spark-udf-lp-1.1.8.jar`。
- shade 含 jts-core（`org.locationtech.jts` 不重定位——集群无 JTS 类冲突）；构建后 `jar tf` 抽查含 jts 且无 spark/hive 类。

## 4. 发布与注册设计

- HDFS 版本化路径 `/udf/spark-udf-lp-1.1.8.jar`（新版本号，不可覆盖旧 URI，§10.4 坑 C）。
- `deploy_spark_udf_lp.sh 1.1.8`：manifest 驱动 DROP+CREATE（§10.4 坑 A）注册 16 个新函数到 lpudf。
- 帮助文本：`LpudfFunctionRegistry.ALL` 追加 16 条（§9.4 双轨）→ `DESC FUNCTION lpudf.<fn>` 可读。
- jar 升级后**必须重启 STS**（§10.4 坑 B：classloader 缓存旧类）。

## 5. 验证设计

- **L1 单测**（构建闸门，每个 UDF ≥6 用例）：正常值 / 空输入 / NULL 传播 / 边界语义（contains vs covers 边界点）/ 归一化（270→-90）/ 锚点距离（±100m）/ empty 语义 / 报错（非法 WKT、纬度越界、非 POINT）；直接驱动 `evaluate`（§10.2）；含 `GeoUtilsTest` 工具类专项。
- **L2 构建检查**：build.sh 全绿 + jar tf 抽查（jts 存在、无 spark/hive 类）。
- **L3 集群 UAT**（`spark_udf_uat.sh 1.1.8`）：L3.1 注册冒烟（`SHOW FUNCTIONS IN lpudf` 16 个新函数 + DESC 抽查）；L3.2 功能矩阵（16 函数 × 正常/边界 SQL，锚点对齐 api-spec；测试数据建 `lpudf.geo_poi` 小表）；L3.3 分布式（≥500 万行 POINT 对 `st_dwithin` 空间连接 + `spark.sql.adaptive.coalescePartitions.enabled=false`，§10.8）；L3.4 持久性（STS 重启后仍可用）。
