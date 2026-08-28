package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

/**
 * st_y(point)：取点地理对象的纬度（y 坐标）。
 *
 * <p>对齐 MaxCompute ST_Y：入参必须为 POINT，否则报错；NULL → NULL。</p>
 */
@ExpressionDescription(
        usage = "st_y(point) - 取点地理对象的纬度（y 坐标）；入参非 POINT 报错。",
        arguments = "point - POINT 的 WKT\nstring")
public class StYUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("st_y 需要 1 个参数 (point)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaDoubleObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object ptObj = args[0].get();
        if (ptObj == null) {
            return null;
        }
        Geometry g = GeoUtils.parseWkt(String.valueOf(ptObj));
        if (!(g instanceof Point)) {
            throw new HiveException("st_y: 入参必须是 POINT，实际 " + g.getGeometryType());
        }
        return g.getCoordinate().y;
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_y(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
