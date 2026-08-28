package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * JsonTypeUdf 单元测试：JSON 类型名（对齐 MC JSON_TYPE，小写枚举）。
 */
public class JsonTypeUdfTest {

    private JsonTypeUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonTypeUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }

    private GenericUDF.DeferredObject[] args(Object... values) {
        GenericUDF.DeferredObject[] result = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object v = values[i];
            result[i] = new GenericUDF.DeferredJavaObject(v);
        }
        return result;
    }

    @Test
    public void testObject() throws Exception {
        assertEquals("object", udf.evaluate(args("{\"a\":1}")));
    }

    @Test
    public void testArray() throws Exception {
        assertEquals("array", udf.evaluate(args("[{\"a\":1},23]")));
    }

    @Test
    public void testNumber() throws Exception {
        assertEquals("number", udf.evaluate(args("123")));
    }

    @Test
    public void testString() throws Exception {
        assertEquals("string", udf.evaluate(args("\"123\"")));
    }

    @Test
    public void testBoolean() throws Exception {
        assertEquals("boolean", udf.evaluate(args("true")));
    }

    @Test
    public void testNullLiteral() throws Exception {
        assertEquals("null", udf.evaluate(args("null")));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testInvalidJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("abc")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonTypeUdf bad = new JsonTypeUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
