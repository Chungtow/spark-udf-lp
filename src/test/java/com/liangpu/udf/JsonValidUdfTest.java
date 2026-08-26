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
 * JsonValidUdf 单元测试：合法 JSON 判定（对齐 MC JSON_VALID）。
 */
public class JsonValidUdfTest {

    private JsonValidUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonValidUdf();
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
    public void testValidObject() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":1}")));
    }

    @Test
    public void testValidArray() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("[1,2]")));
    }

    @Test
    public void testValidStringLiteral() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("\"abc\"")));
    }

    @Test
    public void testValidNumber() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("123")));
    }

    @Test
    public void testValidNullLiteral() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("null")));
    }

    @Test
    public void testValidBoolean() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("true")));
    }

    @Test
    public void testBareWord() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("abc")));
    }

    @Test
    public void testBareKeyObject() throws Exception {
        // 键未加引号 → 非法 JSON
        assertEquals(Boolean.FALSE, udf.evaluate(args("{a:1}")));
    }

    @Test
    public void testEmptyString() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("")));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonValidUdf bad = new JsonValidUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
