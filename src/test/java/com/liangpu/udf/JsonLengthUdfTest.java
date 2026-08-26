package com.liangpu.udf;

import com.liangpu.json.JsonPathException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * JsonLengthUdf 单元测试：数组/对象/标量通用长度（对齐 MC JSON_LENGTH）。
 */
public class JsonLengthUdfTest {

    private JsonLengthUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonLengthUdf();
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
    public void testArrayLength() throws Exception {
        assertEquals(6L, udf.evaluate(args("[1,2,3,4,5,6]")));
    }

    @Test
    public void testObjectLength() throws Exception {
        assertEquals(2L, udf.evaluate(args("{\"k1\":\"v31\",\"k2\":300}")));
    }

    @Test
    public void testScalarNumber() throws Exception {
        assertEquals(1L, udf.evaluate(args("123")));
    }

    @Test
    public void testScalarBoolean() throws Exception {
        assertEquals(1L, udf.evaluate(args("true")));
    }

    @Test
    public void testScalarString() throws Exception {
        assertEquals(1L, udf.evaluate(args("\"abc\"")));
    }

    @Test
    public void testJsonNullLiteral() throws Exception {
        // JSON null 字面量按标量计 1（对齐 MC 文档示例）
        assertEquals(1L, udf.evaluate(args("null")));
    }

    @Test
    public void testNestedNotRecursive() throws Exception {
        // 只计最外层数组长度，不递归
        assertEquals(2L, udf.evaluate(args("[[1,2],[3]]")));
    }

    @Test
    public void testWithPath() throws Exception {
        assertEquals(2L, udf.evaluate(args("{\"x\":1,\"y\":[1,2]}", "$.y")));
    }

    @Test
    public void testPathTargetIsScalar() throws Exception {
        assertEquals(1L, udf.evaluate(args("{\"x\":1}", "$.x")));
    }

    @Test
    public void testPathMissingReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", "$.b")));
    }

    @Test
    public void testNullPathInput() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", null)));
    }

    @Test
    public void testNullJsonInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testInvalidJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("abc")));
    }

    @Test(expected = JsonPathException.class)
    public void testInvalidPathThrows() throws Exception {
        udf.evaluate(args("{\"a\":1}", "$a"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonLengthUdf bad = new JsonLengthUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
