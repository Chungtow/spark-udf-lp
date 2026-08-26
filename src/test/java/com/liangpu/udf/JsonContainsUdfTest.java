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
 * JsonContainsUdf 单元测试：JSON 包含判断（对齐 MC JSON_CONTAINS）。
 */
public class JsonContainsUdfTest {

    private JsonContainsUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonContainsUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
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
    public void testArrayHit() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("[1,2,3,4,5,6,7,8]", "4")));
    }

    @Test
    public void testArrayMiss() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("[10,20,30]", "25")));
    }

    @Test
    public void testPathValueEqual() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":1,\"b\":2,\"c\":{\"d\":4}}", "1", "$.a")));
    }

    @Test
    public void testPathValueNotEqual() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("{\"a\":1,\"b\":2,\"c\":{\"d\":4}}", "2", "$.a")));
    }

    @Test
    public void testPathMissingReturnsFalse() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("{\"a\":1}", "2", "$.b")));
    }

    @Test
    public void testPathInvalidReturnsFalse() throws Exception {
        // MC JSON_CONTAINS 特例：path 非法 → false（不抛错）
        assertEquals(Boolean.FALSE, udf.evaluate(args("{\"a\":1}", "2", "b")));
    }

    @Test
    public void testStringCandidate() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":\"abc\"}", "\"abc\"", "$.a")));
    }

    @Test
    public void testDeepSearchObjectValue() throws Exception {
        // 无 path：递归全量查找
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":1,\"b\":{\"c\":2}}", "2")));
    }

    @Test
    public void testObjectCandidateInArray() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("[{\"x\":1}]", "{\"x\":1}")));
    }

    @Test
    public void testNumericEquality() throws Exception {
        // JSON 数字按数值相等（4 与 4.0 相等）
        assertEquals(Boolean.TRUE, udf.evaluate(args("[4]", "4.0")));
    }

    @Test
    public void testNullJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args(null, "1")));
    }

    @Test
    public void testNullCandidateReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", null)));
    }

    @Test
    public void testInvalidJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("abc", "1")));
    }

    @Test
    public void testInvalidCandidateReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("[1,2]", "abc")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonContainsUdf bad = new JsonContainsUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
