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
 * JsonPrettyUdf 单元测试：美化输出（对齐 MC JSON_PRETTY：4 空格缩进、冒号后无空格）。
 */
public class JsonPrettyUdfTest {

    private JsonPrettyUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonPrettyUdf();
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
        String expected = "{\n    \"a\":1,\n    \"b\":2\n}";
        assertEquals(expected, udf.evaluate(args("{\"a\":1,\"b\":2}")));
    }

    @Test
    public void testNested() throws Exception {
        String expected = "{\n    \"a\":{\n        \"b\":[\n            1,\n            2\n        ]\n    }\n}";
        assertEquals(expected, udf.evaluate(args("{\"a\":{\"b\":[1,2]}}")));
    }

    @Test
    public void testEmptyObject() throws Exception {
        assertEquals("{}", udf.evaluate(args("{}")));
    }

    @Test
    public void testEmptyArray() throws Exception {
        assertEquals("[]", udf.evaluate(args("[]")));
    }

    @Test
    public void testArray() throws Exception {
        String expected = "[\n    1,\n    2\n]";
        assertEquals(expected, udf.evaluate(args("[1,2]")));
    }

    @Test
    public void testStringValue() throws Exception {
        String expected = "{\n    \"a\":\"x y\"\n}";
        assertEquals(expected, udf.evaluate(args("{\"a\":\"x y\"}")));
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
        JsonPrettyUdf bad = new JsonPrettyUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
