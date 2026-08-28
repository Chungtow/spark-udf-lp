package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;

/**
 * st_geogfromtext(wkt)：解析 WKT 文本为地理对象，返回规范化 WKT。
 *
 * <p>对齐 MaxCompute ST_GEOGFROMTEXT：支持 POINT / LINESTRING / POLYGON / EMPTY，不支持 Z/M；
 * {@code POINT EMPTY} 输出 {@code GEOMETRYCOLLECTION EMPTY}；非法 WKT 报错；NULL → NULL。</p>
 */
@ExpressionDescription(
        usage = "st_geogfromtext(wkt) - 解析 WKT 文本为地理对象（返回规范化 WKT）。支持 POINT/LINESTRING/POLYGON/EMPTY；非法 WKT 报错。",
        arguments = "wkt - WKT 文本\nstring")
public class StGeogfromtextUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("st_geogfromtext 需要 1 个参数 (wkt)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object wktObj = args[0].get();
        if (wktObj == null) {
            return null;
        }
        Geometry g = GeoUtils.parseWkt(String.valueOf(wktObj));
        return GeoUtils.toWkt(g);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_geogfromtext(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
