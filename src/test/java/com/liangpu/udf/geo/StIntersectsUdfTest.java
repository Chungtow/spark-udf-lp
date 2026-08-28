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
 * st_intersects 单元测试：两地理对象是否相交（含边界接触）。
 */
public class StIntersectsUdfTest {

    private static final String POLY = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))";

    private StIntersectsUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StIntersectsUdf();
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
        // 边界接触也算相交
        assertTrue((Boolean) udf.evaluate(args(POLY, "POINT (0 0)")));
    }

    @Test
    public void testPointOutside() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(POLY, "POINT (20 20)")));
    }

    @Test
    public void testLineCrossing() throws Exception {
        // 两线段十字交叉
        assertTrue((Boolean) udf.evaluate(args(
                "LINESTRING (0 0, 2 2)", "LINESTRING (0 2, 2 0)")));
    }

    @Test
    public void testDisjointLines() throws Exception {
        assertFalse((Boolean) udf.evaluate(args(
                "LINESTRING (0 0, 1 1)", "LINESTRING (5 5, 6 6)")));
    }

    @Test
    public void testPolygonsOverlap() throws Exception {
        assertTrue((Boolean) udf.evaluate(args(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))",
                "POLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))")));
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
        new StIntersectsUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
