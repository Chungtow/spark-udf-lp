package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_asbinary(geog)：地理对象转 WKB 二进制。
 *
 * <p>对齐 MaxCompute ST_ASBINARY：输入 WKT 解析后输出 WKB（2D）；非法 WKT 报错；NULL → NULL。</p>
 */
@ExpressionDescription(
        usage = "st_asbinary(geog) - 地理对象转 WKB 二进制。",
        arguments = "geog - WKT 地理对象\nstring")
public class StAsbinaryUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("st_asbinary 需要 1 个参数 (geog)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaByteArrayObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object geogObj = args[0].get();
        if (geogObj == null) {
            return null;
        }
        Geometry g = GeoUtils.parseWkt(String.valueOf(geogObj));
        return GeoUtils.toWkb(g);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_asbinary(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
