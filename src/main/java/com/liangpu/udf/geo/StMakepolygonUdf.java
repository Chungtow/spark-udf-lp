package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.StringObjectInspector;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * st_makepolygon(shell[, holes])：由外环构造多边形，返回 WKT。双签名：
 * <ul>
 *   <li>阿里云文档签名：shell 为 LINESTRING WKT（geog），holes 为 LINESTRING WKT 数组（每个 LINESTRING 一个内环，支持多内环）</li>
 *   <li>兼容签名：shell/holes 为 POINT WKT 数组（holes 所有 POINT 合并为单内环）</li>
 * </ul>
 *
 * <p>对齐 MaxCompute ST_MAKEPOLYGON：各环需 ≥3 个不同顶点，首尾不同自动补闭合边；
 * 任一输入 NULL（含数组元素 NULL）→ NULL；空 shell 报错；非法类型/非法 WKT 报错。</p>
 */
@ExpressionDescription(
        usage = "st_makepolygon(shell[, holes]) - 由外环构造多边形（返回 WKT）；shell 为 LINESTRING WKT（geog）或 POINT WKT 数组，holes 为 LINESTRING WKT 数组（每元素一个内环）或 POINT WKT 数组；各环需 ≥3 个不同顶点，首尾不同自动补闭合；输入含 NULL 返回 NULL。",
        arguments = "shell - 外环（LINESTRING WKT 或 POINT WKT 数组）\nholes - 内环 LINESTRING/PONT WKT 数组（可选）")
public class StMakepolygonUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 1 || args.length > 2) {
            throw new UDFArgumentException("st_makepolygon 需要 1 或 2 个参数 (shell[, holes])，实际 " + args.length);
        }
        for (ObjectInspector oi : args) {
            if (!(oi instanceof ListObjectInspector) && !(oi instanceof StringObjectInspector)) {
                throw new UDFArgumentException("st_makepolygon 参数必须是 geog（WKT 文本）或 array<geog>");
            }
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Coordinate[] shellCoords = shellCoordsOf(args[0].get());
        if (shellCoords == null) {
            return null; // shell 为 NULL 或含 NULL 元素
        }
        LinearRing shellRing = GeoUtils.factory().createLinearRing(shellCoords);

        List<LinearRing> holeRings = new ArrayList<>();
        if (args.length == 2) {
            Object holesObj = args[1].get();
            if (holesObj != null) {
                List<?> holesList = (List<?>) holesObj;
                if (!holesList.isEmpty()) {
                    List<Geometry> geoms = new ArrayList<>(holesList.size());
                    for (Object item : holesList) {
                        if (item == null) {
                            return null; // holes 含 NULL 元素
                        }
                        geoms.add(GeoUtils.parseWkt(String.valueOf(item)));
                    }
                    holeRings.addAll(holeRingsOf(geoms));
                }
            }
        }

        Polygon poly = GeoUtils.factory().createPolygon(shellRing,
                holeRings.isEmpty() ? null : holeRings.toArray(new LinearRing[0]));
        return GeoUtils.toWkt(poly);
    }

    /**
     * shell 坐标：LINESTRING WKT（阿里云文档签名，geog 载体为 WKT 文本）或 POINT WKT 数组（兼容签名）。
     * NULL/含 NULL 元素 → null；空 shell、非 LINESTRING、&lt;3 个不同顶点报错；自动补闭合。
     */
    private Coordinate[] shellCoordsOf(Object shellObj) throws HiveException {
        if (shellObj == null) {
            return null;
        }
        if (shellObj instanceof List) {
            List<?> shellList = (List<?>) shellObj;
            if (shellList.isEmpty()) {
                throw new HiveException("st_makepolygon: shell 为空（空对象不支持）");
            }
            Coordinate[] coords = GeoUtils.coordsOf(shellList);
            if (coords == null) {
                return null; // shell 含 NULL 元素
            }
            if (GeoUtils.distinctVertices(coords) < 3) {
                throw new HiveException("st_makepolygon: shell 至少需要 3 个不同顶点");
            }
            return GeoUtils.ensureClosed(coords);
        }
        Geometry g = GeoUtils.parseWkt(String.valueOf(shellObj));
        if (g == null || g.isEmpty()) {
            throw new HiveException("st_makepolygon: shell 为空（空对象不支持）");
        }
        if (!(g instanceof LineString)) {
            throw new HiveException("st_makepolygon: shell 必须是 LINESTRING，实际 " + g.getGeometryType());
        }
        Coordinate[] coords = ((LineString) g).getCoordinates();
        if (GeoUtils.distinctVertices(coords) < 3) {
            throw new HiveException("st_makepolygon: shell 至少需要 3 个不同顶点");
        }
        return GeoUtils.ensureClosed(coords);
    }

    /**
     * holes 坐标环：元素全为 POINT → 合并为单个内环（兼容签名）；全为 LINESTRING →
     * 每元素一个内环（阿里云文档签名，支持多内环）；混合类型报错；环 &lt;3 个不同顶点报错；自动补闭合。
     */
    private List<LinearRing> holeRingsOf(List<Geometry> geoms) throws HiveException {
        boolean allPoint = true;
        boolean allLine = true;
        for (Geometry g : geoms) {
            allPoint &= g instanceof Point;
            allLine &= g instanceof LineString;
        }
        if (!allPoint && !allLine) {
            throw new HiveException("st_makepolygon: holes 元素须统一为 POINT 或 LINESTRING");
        }
        List<LinearRing> rings = new ArrayList<>();
        if (allPoint) {
            Coordinate[] coords = new Coordinate[geoms.size()];
            for (int i = 0; i < geoms.size(); i++) {
                coords[i] = geoms.get(i).getCoordinate();
            }
            rings.add(ringOf(coords));
        } else {
            for (Geometry g : geoms) {
                rings.add(ringOf(((LineString) g).getCoordinates()));
            }
        }
        return rings;
    }

    private LinearRing ringOf(Coordinate[] coords) throws HiveException {
        if (GeoUtils.distinctVertices(coords) < 3) {
            throw new HiveException("st_makepolygon: holes 内环至少需要 3 个不同顶点");
        }
        return GeoUtils.factory().createLinearRing(GeoUtils.ensureClosed(coords));
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_makepolygon(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
