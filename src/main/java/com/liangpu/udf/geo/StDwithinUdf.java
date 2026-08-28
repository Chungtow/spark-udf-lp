package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_dwithin(geog1, geog2, dist)：判断两地理对象最短距离是否 ≤ dist 米。
 *
 * <p>对齐 MaxCompute ST_DWITHIN：任一输入 NULL/空 → FALSE；非法 WKT 报错；
 * dist 可为 0（等价判断点在多边形内）；dist 负数按 FALSE 处理。</p>
 */
@ExpressionDescription(
        usage = "st_dwithin(geog1, geog2, dist) - 判断两地理对象最短距离是否 ≤ dist（米）；任一输入 NULL/空返回 false；dist 为 0 可判断点在多边形内。",
        arguments = "geog1 - WKT 地理对象 1\ngeog2 - WKT 地理对象 2\ndist - 距离阈值（米），double")
public class StDwithinUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 3) {
            throw new UDFArgumentException("st_dwithin 需要 3 个参数 (geog1, geog2, dist)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaBooleanObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object g1Obj = args[0].get();
        Object g2Obj = args[1].get();
        Object distObj = args[2].get();
        if (g1Obj == null || g2Obj == null || distObj == null) {
            return Boolean.FALSE;
        }
        Geometry g1 = GeoUtils.parseWkt(String.valueOf(g1Obj));
        Geometry g2 = GeoUtils.parseWkt(String.valueOf(g2Obj));
        if (g1.isEmpty() || g2.isEmpty()) {
            return Boolean.FALSE;
        }
        double dist = GeoUtils.toDouble(distObj);
        if (dist < 0) {
            return Boolean.FALSE;
        }
        return GeoUtils.distanceMeters(g1, g2) <= dist;
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_dwithin(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
