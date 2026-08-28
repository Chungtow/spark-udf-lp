package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_intersects(geog1, geog2)：判断两地理对象是否有公共点（hole 内不算相交）。
 *
 * <p>对齐 MaxCompute ST_INTERSECTS：任一输入 NULL/空 → FALSE；非法 WKT 报错。</p>
 */
@ExpressionDescription(
        usage = "st_intersects(geog1, geog2) - 判断两地理对象是否有公共点（hole 内不算相交）；任一输入 NULL/空返回 false。",
        arguments = "geog1 - WKT 地理对象 1\ngeog2 - WKT 地理对象 2")
public class StIntersectsUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2) {
            throw new UDFArgumentException("st_intersects 需要 2 个参数 (geog1, geog2)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaBooleanObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object g1Obj = args[0].get();
        Object g2Obj = args[1].get();
        if (g1Obj == null || g2Obj == null) {
            return Boolean.FALSE;
        }
        Geometry g1 = GeoUtils.parseWkt(String.valueOf(g1Obj));
        Geometry g2 = GeoUtils.parseWkt(String.valueOf(g2Obj));
        if (g1.isEmpty() || g2.isEmpty()) {
            return Boolean.FALSE;
        }
        return g1.intersects(g2);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_intersects(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
