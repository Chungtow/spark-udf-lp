package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * st_boundingbox(geog)：返回地理对象外接矩形 {@code STRUCT<xmin,ymin,xmax,ymax>}。
 *
 * <p>对齐 MaxCompute ST_BOUNDINGBOX：输入 NULL/空 → NULL；非法 WKT 报错。
 * 平面外接矩形 vs S2 球面存在微小精度差异（MC 文档亦声明）。</p>
 */
@ExpressionDescription(
        usage = "st_boundingbox(geog) - 返回地理对象外接矩形 STRUCT<xmin,ymin,xmax,ymax>；输入 NULL/空返回 NULL。",
        arguments = "geog - WKT 地理对象\nstring")
public class StBoundingboxUdf extends GenericUDF {

    private static final List<String> FIELD_NAMES = Arrays.asList("xmin", "ymin", "xmax", "ymax");

    private StructObjectInspector resultOi;

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("st_boundingbox 需要 1 个参数 (geog)，实际 " + args.length);
        }
        List<ObjectInspector> fields = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // struct 返回必须用 writable OI + DoubleWritable 值：Spark/Thrift 按 writable 读 struct 字段，
            // 用 java OI + java Double 会报 ClassCastException: Double cannot be cast to DoubleWritable
            fields.add(PrimitiveObjectInspectorFactory.writableDoubleObjectInspector);
        }
        resultOi = ObjectInspectorFactory.getStandardStructObjectInspector(FIELD_NAMES, fields);
        return resultOi;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object geogObj = args[0].get();
        if (geogObj == null) {
            return null;
        }
        Geometry g = GeoUtils.parseWkt(String.valueOf(geogObj));
        if (g.isEmpty()) {
            return null;
        }
        Envelope env = g.getEnvelopeInternal();
        List<Object> row = new ArrayList<>(4);
        row.add(new org.apache.hadoop.io.DoubleWritable(env.getMinX()));
        row.add(new org.apache.hadoop.io.DoubleWritable(env.getMinY()));
        row.add(new org.apache.hadoop.io.DoubleWritable(env.getMaxX()));
        row.add(new org.apache.hadoop.io.DoubleWritable(env.getMaxY()));
        return row;
    }

    @Override
    public String getDisplayString(String[] children) {
        return "st_boundingbox(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
