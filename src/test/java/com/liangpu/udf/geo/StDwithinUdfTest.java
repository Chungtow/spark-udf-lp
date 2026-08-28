package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * st_dwithin 单元测试：两地理对象距离 ≤ 阈值判定；NULL/空 → false。
 */
public class StDwithinUdfTest {

    private StDwithinUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StDwithinUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
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
    public void testWithinTrue() throws Exception {
        // 0.001° 纬度 ≈ 111m < 150m
        assertTrue((Boolean) udf.evaluate(args("POINT (0 0)", "POINT (0 0.001)", 150.0)));
    }

    @Test
    public void testWithinFalse() throws Exception {
        // (0,0)→(1,1) ≈ 157km > 100
        assertFalse((Boolean) udf.evaluate(args("POINT (0 0)", "POINT (1 1)", 100.0)));
    }

    @Test
    public void testExactBoundaryTrue() throws Exception {
        // 阈值恰好等于距离 → true（≤ 语义）
        assertTrue((Boolean) udf.evaluate(args("POINT (0 0)", "POINT (0 0.001)", 111.2)));
    }

    @Test
    public void testPolygonContainsPoint() throws Exception {
        // 点在多边形内部 → 距离 0 ≤ dist → true
        assertTrue((Boolean) udf.evaluate(args(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))", "POINT (5 5)", 0.0)));
    }

    @Test
    public void testNegativeDistanceFalse() throws Exception {
        // dist < 0 → false
        assertFalse((Boolean) udf.evaluate(args("POINT (0 0)", "POINT (0 0.001)", -1.0)));
    }

    @Test
    public void testEmptyGeometryFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args("POINT EMPTY", "POINT (0 0)", 150.0)));
    }

    @Test
    public void testNullInputFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(null, "POINT (0 0)", 150.0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT", "POINT (0 0)", 150.0));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StDwithinUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
