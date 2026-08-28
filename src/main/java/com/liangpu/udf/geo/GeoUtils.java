package com.liangpu.udf.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.distance.DistanceOp;

import java.util.Arrays;

/**
 * 地理函数（st_* 系列）共享工具：WKT/WKB 解析与输出、经度归一化、球面距离（haversine）、自动闭合。
 *
 * <p>语义对齐 MaxCompute 地理函数（inception §2.2）：地理对象以 WKT 文本为外部载体；
 * 空几何统一输出 {@code GEOMETRYCOLLECTION EMPTY}；经度按 360° 归一化；
 * 距离为球面大圆近似（R=6371000m，锚点 (0,0)→(1,1) ≈ 157250m，文档 157249.63，偏差 ~0.01%）。</p>
 */
public final class GeoUtils {

    public static final String EMPTY_TEXT = "GEOMETRYCOLLECTION EMPTY";
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final GeometryFactory GF = new GeometryFactory();

    private GeoUtils() {
    }

    /** WKT 文本 → JTS Geometry；非法 WKT 抛 IllegalArgumentException（由 UDF 包装为 HiveException）。 */
    public static Geometry parseWkt(String wkt) {
        if (wkt == null) {
            return null;
        }
        try {
            return new WKTReader().read(wkt);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 WKT 文本: " + wkt + "（" + e.getMessage() + "）");
        }
    }

    /** WKB 字节 → JTS Geometry；非法 WKB 抛 IllegalArgumentException。 */
    public static Geometry parseWkb(byte[] wkb) {
        try {
            return new WKBReader().read(wkb);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 WKB 字节（" + e.getMessage() + "）");
        }
    }

    /** JTS Geometry → WKT 文本；空几何统一输出 GEOMETRYCOLLECTION EMPTY（MC 语义）。 */
    public static String toWkt(Geometry g) {
        if (g == null) {
            return null;
        }
        if (g.isEmpty()) {
            return EMPTY_TEXT;
        }
        return new WKTWriter().write(g);
    }

    /** JTS Geometry → WKB 字节（2D，无 SRID）。 */
    public static byte[] toWkb(Geometry g) {
        if (g == null) {
            return null;
        }
        return new WKBWriter(2).write(g);
    }

    /** 经度归一化到 [-180,180)：((lon+180)%360+360)%360-180，如 270 → -90（MC st_geogpoint 语义）。 */
    public static double normalizeLon(double lon) {
        return ((lon + 180) % 360 + 360) % 360 - 180;
    }

    /**
     * 通用数值 → double：兼容 java.lang.Number（本地/单测传入 Integer/Double）
     * 与 Hive Writable（Spark 运行时经 HiveGenericUDF 传入 IntWritable/DoubleWritable 等）。
     */
    public static double toDouble(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof org.apache.hadoop.io.IntWritable) {
            return ((org.apache.hadoop.io.IntWritable) o).get();
        }
        if (o instanceof org.apache.hadoop.io.LongWritable) {
            return ((org.apache.hadoop.io.LongWritable) o).get();
        }
        if (o instanceof org.apache.hadoop.io.DoubleWritable) {
            return ((org.apache.hadoop.io.DoubleWritable) o).get();
        }
        if (o instanceof org.apache.hadoop.io.FloatWritable) {
            return ((org.apache.hadoop.io.FloatWritable) o).get();
        }
        if (o instanceof org.apache.hadoop.io.ShortWritable) {
            return ((org.apache.hadoop.io.ShortWritable) o).get();
        }
        if (o instanceof org.apache.hadoop.io.ByteWritable) {
            return ((org.apache.hadoop.io.ByteWritable) o).get();
        }
        if (o instanceof org.apache.hadoop.io.Text) {
            return Double.parseDouble(o.toString());
        }
        // Spark 3.3 HiveGenericUDF 常量折叠（foldable）路径下，嵌套 UDF 调用（如
        // st_dwithin(st_geogpoint(...), st_geogpoint(...), 8000)）的数值字面量经 Hive 类型系统
        // 包装为 HiveDecimalWritable 传入，需显式兼容（集群实测 IllegalArgumentException）
        if (o instanceof org.apache.hadoop.hive.serde2.io.HiveDecimalWritable) {
            return ((org.apache.hadoop.hive.serde2.io.HiveDecimalWritable) o).getHiveDecimal().doubleValue();
        }
        // VALUES 表字面量列（如 SELECT st_geogpoint(lng,lat) FROM VALUES (3.0,4.0) tmp(lng,lat)）
        // 走另一条常量折叠路径，数值包装为非 Writable 的 HiveDecimal 传入，需显式兼容（集群实测）
        if (o instanceof org.apache.hadoop.hive.common.type.HiveDecimal) {
            return ((org.apache.hadoop.hive.common.type.HiveDecimal) o).doubleValue();
        }
        throw new IllegalArgumentException("无法转换为 double: " + (o == null ? "null" : o.getClass().getName()));
    }

    /** 球面大圆距离（米），haversine 公式，R=6371000m。 */
    public static double haversineMeters(double lon1, double lat1, double lon2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 两几何间最短距离（米）：JTS 求最近点对（平面）→ haversine 换算球面距离。 */
    public static double distanceMeters(Geometry g1, Geometry g2) {
        Coordinate[] nearest = DistanceOp.nearestPoints(g1, g2);
        return haversineMeters(nearest[0].x, nearest[0].y, nearest[1].x, nearest[1].y);
    }

    /** 首尾不同时自动补闭合点（MC st_makepolygon 自动闭合语义）；空/已闭合原样返回。 */
    public static Coordinate[] ensureClosed(Coordinate[] coords) {
        if (coords.length == 0 || coords[0].equals2D(coords[coords.length - 1])) {
            return coords;
        }
        Coordinate[] c = Arrays.copyOf(coords, coords.length + 1);
        c[coords.length] = coords[0].copy();
        return c;
    }

    /** 计算 shell 中不同顶点的个数（去重后）。 */
    public static int distinctVertices(Coordinate[] coords) {
        int n = 0;
        Coordinate prev = null;
        for (Coordinate c : coords) {
            if (prev == null || !prev.equals2D(c)) {
                n++;
                prev = c;
            }
        }
        return n;
    }

    public static GeometryFactory factory() {
        return GF;
    }

    /**
     * POINT WKT 列表 → Coordinate[]；任一元素为 NULL 或非法 POINT 时返回 null
     * （调用方按"含 NULL → NULL"契约处理）。
     */
    public static Coordinate[] coordsOf(java.util.List<?> pointWkts) {
        if (pointWkts == null) {
            return null;
        }
        Coordinate[] coords = new Coordinate[pointWkts.size()];
        for (int i = 0; i < pointWkts.size(); i++) {
            Object item = pointWkts.get(i);
            if (item == null) {
                return null;
            }
            Geometry g = parseWkt(String.valueOf(item));
            if (!(g instanceof org.locationtech.jts.geom.Point)) {
                throw new IllegalArgumentException("st_makeline/st_makepolygon: 数组元素必须是 POINT，实际 " + g.getGeometryType());
            }
            coords[i] = g.getCoordinate();
        }
        return coords;
    }
}
