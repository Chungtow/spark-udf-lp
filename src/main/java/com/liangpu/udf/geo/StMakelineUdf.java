package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.List;

/**
 * st_makeline(p1, p2) 或 st_makeline(array&lt;geog&gt;)：由两个点或点数组构造线，返回 WKT。
 *
 * <p>对齐 MaxCompute ST_MAKELINE：任一输入 NULL（含数组元素 NULL）→ NULL；两点相同/数组单元素
 * → 退化为 Point；数组至少 1 元素（空数组 → NULL）。</p>
 */
@ExpressionDescription(
        usage = "st_makeline(p1, p2) 或 st_makeline(array) - 由两个点或点数组构造线；输入含 NULL 返回 NULL，两点相同/单元素退化为 Point。",
        arguments = "p1/p2 - POINT 的 WKT，或 POINT WKT 数组\narray")
public class StMakelineUdf extends GenericUDF {

    private boolean arrayMode;

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length == 1) {
            if (!(args[0] instanceof ListObjectInspector)) {
                throw new UDFArgumentException("st_makeline 单参形式需要 array<geog>，实际非数组");
            }
            arrayMode = true;
        } else if (args.length == 2) {
            arrayMode = false;
        } else {
            throw new UDFArgumentException("st_makeline 需要 2 个参数 (p1, p2) 或 1 个数组参数，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Coordinate[] coords;
        if (arrayMode) {
            Object listObj = args[0].get();
            if (listObj == null) {
                return null;
            }
            List<?> list = ((List<?>) listObj);
            if (list.isEmpty()) {
                return null;
            }
            coords = GeoUtils.coordsOf(list);
        } else {
            Object p1 = args[0].get();
            Object p2 = args[1].get();
            if (p1 == null || p2 == null) {
                return null;
            }
            Geometry g1 = GeoUtils.parseWkt(String.valueOf(p1));
            Geometry g2 = GeoUtils.parseWkt(String.valueOf(p2));
            if (!(g1 instanceof Point) || !(g2 instanceof Point)) {
                throw new HiveException("st_makeline: 入参必须是 POINT，实际 " + g1.getGeometryType() + "/" + g2.getGeometryType());
            }
            coords = new Coordinate[]{g1.getCoordinate(), g2.getCoordinate()};
        }
        if (coords == null) {
            return null; // 数组含 NULL 元素
        }
        if (GeoUtils.distinctVertices(coords) <= 1) {
            // 两点相同/单元素 → 退化为 Point（MC 语义），经 toWkt 保证输出格式一致
            return GeoUtils.toWkt(GeoUtils.factory().createPoint(coords[0]));
        }
        Geometry line = GeoUtils.factory().createLineString(coords);
        return GeoUtils.toWkt(line);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_makeline(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
