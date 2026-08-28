package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_geogfromwkb(wkb)：解析 WKB 二进制为地理对象，返回 WKT 文本。
 *
 * <p>对齐 MaxCompute ST_GEOGFROMWKB：非法 WKB 报错；NULL → NULL。入参为 BINARY（Spark 侧 byte[]）。</p>
 */
@ExpressionDescription(
        usage = "st_geogfromwkb(wkb) - 解析 WKB 二进制为地理对象（返回 WKT）。非法 WKB 报错。",
        arguments = "wkb - WKB 二进制\nbinary")
public class StGeogfromwkbUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("st_geogfromwkb 需要 1 个参数 (wkb)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object wkbObj = args[0].get();
        if (wkbObj == null) {
            return null;
        }
        byte[] wkb = toBytes(wkbObj);
        Geometry g = GeoUtils.parseWkb(wkb);
        return GeoUtils.toWkt(g);
    }

    private static byte[] toBytes(Object value) throws HiveException {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof org.apache.hadoop.io.BytesWritable) {
            org.apache.hadoop.io.BytesWritable bw = (org.apache.hadoop.io.BytesWritable) value;
            return java.util.Arrays.copyOf(bw.getBytes(), bw.getLength());
        }
        throw new HiveException("st_geogfromwkb: 不支持的 BINARY 入参类型 " + value.getClass().getName());
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_geogfromwkb(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
