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
 * st_within 单元测试：对象 A 是否完全在 B 内（不含边界，与 contains 互为逆）。
 */
public class StWithinUdfTest {

    private static final String POLY = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))";

    private StWithinUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StWithinUdf();
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
        assertTrue((Boolean) udf.evaluate(args("POINT (5 5)", POLY)));
    }

    @Test
    public void testPointOnBoundaryFalse() throws Exception {
        // within 不含边界
        assertFalse((Boolean) udf.evaluate(args("POINT (0 0)", POLY)));
    }

    @Test
    public void testPointOutside() throws Exception {
        assertFalse((Boolean) udf.evaluate(args("POINT (20 20)", POLY)));
    }

    @Test
    public void testLineStringWithin() throws Exception {
        assertTrue((Boolean) udf.evaluate(args("LINESTRING (1 1, 2 2)", POLY)));
    }

    @Test
    public void testPolygonWithinItself() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(POLY, POLY)));
    }

    @Test
    public void testContainsInverse() throws Exception {
        // within(a, b) 与 contains(b, a) 一致
        boolean within = (Boolean) udf.evaluate(args("POINT (5 5)", POLY));
        StContainsUdf containsUdf = new StContainsUdf();
        containsUdf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
        boolean contains = (Boolean) containsUdf.evaluate(
                new GenericUDF.DeferredObject[]{new GenericUDF.DeferredJavaObject(POLY),
                        new GenericUDF.DeferredJavaObject("POINT (5 5)")});
        assertTrue(within == contains);
    }

    @Test
    public void testEmptyGeometryFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args("POINT EMPTY", POLY)));
    }

    @Test
    public void testNullInputFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(null, POLY)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT", POLY));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StWithinUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
