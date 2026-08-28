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
 * st_astext 单元测试：地理对象 → 规范化 WKT 文本。
 */
public class StAstextUdfTest {

    private StAstextUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StAstextUdf();
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
    public void testNormalizedOutput() throws Exception {
        // 无空格输入 → 规范化输出
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
    public void testEmpty() throws Exception {
        assertEquals("GEOMETRYCOLLECTION EMPTY", udf.evaluate(args("POINT EMPTY")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT"));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StAstextUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
