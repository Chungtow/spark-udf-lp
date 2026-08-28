package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Coordinate;

/**
 * st_geogpoint(longitude, latitude)：由经度/纬度构造点地理对象，返回 WKT 文本（如 "POINT (2 4)"）。
 *
 * <p>对齐 MaxCompute ST_GEOGPOINT：lon ∈ [-180,180]，超出按 360° 归一化（如 270 → -90）；
 * lat ∈ [-90,90]，超出报错。任一入参 NULL → NULL。</p>
 */
@ExpressionDescription(
        usage = "st_geogpoint(longitude, latitude) - 由经度/纬度构造点地理对象（返回 WKT）。lon 超出 [-180,180] 按 360° 归一化；lat 超出 [-90,90] 报错。",
        arguments = "longitude - 经度\ndouble\nlatitude - 纬度\ndouble")
public class StGeogpointUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2) {
            throw new UDFArgumentException("st_geogpoint 需要 2 个参数 (longitude, latitude)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object lonObj = args[0].get();
        Object latObj = args[1].get();
        if (lonObj == null || latObj == null) {
            return null;
        }
        double lon = GeoUtils.toDouble(lonObj);
        double lat = GeoUtils.toDouble(latObj);
        if (lat < -90.0 || lat > 90.0) {
            throw new HiveException("st_geogpoint: 纬度 " + lat + " 超出 [-90, 90] 范围");
        }
        double normLon = GeoUtils.normalizeLon(lon);
        // 经 JTS 构造并序列化，保证输出格式与 st_geogfromtext 等一致（如 "POINT (2 4)"）
        org.locationtech.jts.geom.Point pt = GeoUtils.factory().createPoint(new Coordinate(normLon, lat));
        return GeoUtils.toWkt(pt);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_geogpoint(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
