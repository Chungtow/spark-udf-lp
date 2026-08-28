package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_astext(geog)：地理对象转 WKT 文本。
 *
 * <p>对齐 MaxCompute ST_ASTEXT：输入 WKT 解析后规范化输出；空几何输出
 * {@code GEOMETRYCOLLECTION EMPTY}；非法 WKT 报错；NULL → NULL。</p>
 */
@ExpressionDescription(
        usage = "st_astext(geog) - 地理对象转 WKT 文本；空几何输出 GEOMETRYCOLLECTION EMPTY。",
        arguments = "geog - WKT 地理对象\nstring")
public class StAstextUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("st_astext 需要 1 个参数 (geog)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object geogObj = args[0].get();
        if (geogObj == null) {
            return null;
        }
        Geometry g = GeoUtils.parseWkt(String.valueOf(geogObj));
        return GeoUtils.toWkt(g);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_astext(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
