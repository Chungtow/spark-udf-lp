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
 * st_contains 单元测试：contains 不含边界（区别于 covers）。
 */
public class StContainsUdfTest {

    private static final String POLY = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))";

    private StContainsUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StContainsUdf();
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
    public void testPointOnBoundaryFalse() throws Exception {
        // contains 不含边界
        assertFalse((Boolean) udf.evaluate(args(POLY, "POINT (0 0)")));
    }

    @Test
    public void testPointOnEdgeMidpointFalse() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(POLY, "POINT (5 0)")));
    }

    @Test
    public void testPointOutside() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(POLY, "POINT (20 20)")));
    }

    @Test
    public void testPolygonContainsItself() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(POLY, POLY)));
    }

    @Test
    public void testLineStringWithin() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(POLY, "LINESTRING (1 1, 2 2)")));
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
        new StContainsUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
