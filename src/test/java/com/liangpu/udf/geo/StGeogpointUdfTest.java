package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_geogpoint 单元测试：由经纬度构造点，lon 归一化、lat 越界报错、NULL 传播。
 */
public class StGeogpointUdfTest {

    private StGeogpointUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StGeogpointUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaDoubleObjectInspector,
                PrimitiveObjectInspectorFactory.javaDoubleObjectInspector
        });
    }

    private GenericUDF.DeferredObject[] args(Object... values) {
        GenericUDF.DeferredObject[] result = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new GenericUDF.DeferredJavaObject(values[i]);
        }
        return result;
    }

    @Test
    public void testNormalPoint() throws Exception {
        assertEquals("POINT (2 4)", udf.evaluate(args(2.0, 4.0)));
    }

    @Test
    public void testNegativeCoords() throws Exception {
        assertEquals("POINT (-180 -90)", udf.evaluate(args(-180.0, -90.0)));
    }

    @Test
    public void testDecimalCoords() throws Exception {
        // 用可精确表示的十进制坐标（避免 double 二进制精度尾部噪声）
        assertEquals("POINT (116.5 39.5)", udf.evaluate(args(116.5, 39.5)));
    }

    @Test
    public void testLonNormalize270() throws Exception {
        // 270 → -90（360° 归一化）
        assertEquals("POINT (-90 4)", udf.evaluate(args(270.0, 4.0)));
    }

    @Test
    public void testLonNormalize180() throws Exception {
        // 180 → -180（半开区间 [-180,180)）
        assertEquals("POINT (-180 0)", udf.evaluate(args(180.0, 0.0)));
    }

    @Test
    public void testLonNormalizeNegative() throws Exception {
        // -190 → 170
        assertEquals("POINT (170 0)", udf.evaluate(args(-190.0, 0.0)));
    }

    @Test
    public void testLatBoundary() throws Exception {
        assertEquals("POINT (0 90)", udf.evaluate(args(0.0, 90.0)));
        assertEquals("POINT (0 -90)", udf.evaluate(args(0.0, -90.0)));
    }

    @Test(expected = HiveException.class)
    public void testLatOverflowPositive() throws Exception {
        udf.evaluate(args(0.0, 91.0));
    }

    @Test(expected = HiveException.class)
    public void testLatOverflowNegative() throws Exception {
        udf.evaluate(args(0.0, -91.0));
    }

    @Test
    public void testHiveDecimalArgs() throws Exception {
        // Spark VALUES 表字面量列常量折叠路径：参数为非 Writable 的 HiveDecimal（集群实测）
        assertEquals("POINT (3 4)", udf.evaluate(args(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("3.0"),
                org.apache.hadoop.hive.common.type.HiveDecimal.create("4.0"))));
        assertEquals("POINT (-80 80)", udf.evaluate(args(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("1000"),
                org.apache.hadoop.hive.common.type.HiveDecimal.create("80"))));
        assertEquals("POINT (80 -80)", udf.evaluate(args(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("-1000"),
                org.apache.hadoop.hive.common.type.HiveDecimal.create("-80"))));
        assertEquals("POINT (-180 -90)", udf.evaluate(args(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("-180"),
                org.apache.hadoop.hive.common.type.HiveDecimal.create("-90"))));
        assertEquals("POINT (-180 90)", udf.evaluate(args(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("180"),
                org.apache.hadoop.hive.common.type.HiveDecimal.create("90"))));
    }

    @Test
    public void testNullLon() throws Exception {
        assertNull(udf.evaluate(args(null, 4.0)));
    }

    @Test
    public void testNullLat() throws Exception {
        assertNull(udf.evaluate(args(2.0, null)));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StGeogpointUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaDoubleObjectInspector
        });
    }
}
