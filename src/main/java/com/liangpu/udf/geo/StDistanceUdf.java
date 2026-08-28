package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_distance(geog1, geog2)：两地理对象间最短距离（球面大圆近似，单位米）。
 *
 * <p>对齐 MaxCompute ST_DISTANCE：任一输入 NULL/空 → NULL；非法 WKT 报错。
 * 实现：JTS 求最近点对（平面）→ haversine 球面距离（锚点 (0,0)→(1,1) ≈ 157250m，偏差 ~0.01%）。</p>
 */
@ExpressionDescription(
        usage = "st_distance(geog1, geog2) - 两地理对象间最短距离（球面大圆近似，单位米）；任一输入 NULL/空返回 NULL。",
        arguments = "geog1 - WKT 地理对象 1\ngeog2 - WKT 地理对象 2")
public class StDistanceUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2) {
            throw new UDFArgumentException("st_distance 需要 2 个参数 (geog1, geog2)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaDoubleObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object g1Obj = args[0].get();
        Object g2Obj = args[1].get();
        if (g1Obj == null || g2Obj == null) {
            return null;
        }
        Geometry g1 = GeoUtils.parseWkt(String.valueOf(g1Obj));
        Geometry g2 = GeoUtils.parseWkt(String.valueOf(g2Obj));
        if (g1.isEmpty() || g2.isEmpty()) {
            return null;
        }
        return GeoUtils.distanceMeters(g1, g2);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_distance(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
