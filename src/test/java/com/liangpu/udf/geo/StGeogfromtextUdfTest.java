package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_geogfromtext 单元测试：WKT → 地理对象（WKT），EMPTY 统一 GEOMETRYCOLLECTION EMPTY。
 */
public class StGeogfromtextUdfTest {

    private StGeogfromtextUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StGeogfromtextUdf();
        udf.initialize(new ObjectInspector[]{
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
    public void testPoint() throws Exception {
        assertEquals("POINT (2 4)", udf.evaluate(args("POINT (2 4)")));
    }

    @Test
    public void testPointNoSpaceNormalized() throws Exception {
        assertEquals("POINT (2 4)", udf.evaluate(args("POINT(2 4)")));
    }

    @Test
    public void testLineString() throws Exception {
        assertEquals("LINESTRING (2 4, 3 5)", udf.evaluate(args("LINESTRING (2 4, 3 5)")));
    }

    @Test
    public void testPolygon() throws Exception {
        assertEquals("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))",
                udf.evaluate(args("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))")));
    }

    @Test
    public void testEmptyPoint() throws Exception {
        assertEquals("GEOMETRYCOLLECTION EMPTY", udf.evaluate(args("POINT EMPTY")));
    }

    @Test
    public void testEmptyPolygon() throws Exception {
        assertEquals("GEOMETRYCOLLECTION EMPTY", udf.evaluate(args("POLYGON EMPTY")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyString() throws Exception {
        udf.evaluate(args(""));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StGeogfromtextUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
