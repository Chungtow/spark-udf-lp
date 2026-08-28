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
 * st_covers 单元测试：covers 含边界（区别于 contains）。
 */
public class StCoversUdfTest {

    private static final String POLY = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))";

    private StCoversUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StCoversUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
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
    public void testPointInside() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(POLY, "POINT (5 5)")));
    }

    @Test
    public void testPointOnBoundaryTrue() throws Exception {
        // covers 含边界（与 contains 的关键区别）
        assertTrue((Boolean) udf.evaluate(args(POLY, "POINT (0 0)")));
    }

    @Test
    public void testPointOnEdgeMidpointTrue() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(POLY, "POINT (5 0)")));
    }

    @Test
    public void testPointOutside() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(POLY, "POINT (20 20)")));
    }

    @Test
    public void testPolygonCoversItself() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(POLY, POLY)));
    }

    @Test
    public void testEmptyGeometryFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(POLY, "POINT EMPTY")));
    }

    @Test
    public void testNullInputFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(null, "POINT (5 5)")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args(POLY, "NOT A WKT"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StCoversUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
