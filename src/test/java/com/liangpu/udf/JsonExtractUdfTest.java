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
 * JsonExtractUdf 单元测试：JSONPath 提取（对齐 MC JSON_EXTRACT）。
 */
public class JsonExtractUdfTest {

    private JsonExtractUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonExtractUdf();
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
    public void testSimpleKey() throws Exception {
        assertEquals("1", udf.evaluate(args("{\"a\":1,\"b\":2}", "$.a")));
    }

    @Test
    public void testNestedKey() throws Exception {
        assertEquals("3", udf.evaluate(args("{\"a\":{\"b\":3}}", "$.a.b")));
    }

    @Test
    public void testArrayIndex() throws Exception {
        assertEquals("34", udf.evaluate(args("[1,2,{\"a\":34}]", "$[2].a")));
    }

    @Test
    public void testRoot() throws Exception {
        assertEquals("{\"a\":1}", udf.evaluate(args("{\"a\":1}", "$")));
    }

    @Test
    public void testArrayWholeIndex() throws Exception {
        // 提取数组下标元素
        assertEquals("1", udf.evaluate(args("[1,2]", "$[0]")));
    }

    @Test
    public void testMissingKeyReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1,\"b\":2}", "$.c")));
    }

    @Test
    public void testIndexOutOfRangeReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("[1,2,3]", "$[5]")));
    }

    @Test
    public void testNullValueReturnsNullText() throws Exception {
        // 键存在但值为 JSON null → 返回 "null" 文本（与"不存在"区分）
        assertEquals("null", udf.evaluate(args("{\"a\":null}", "$.a")));
    }

    @Test
    public void testRootKeepsNullField() throws Exception {
        // 序列化保真：对象内值为 null 的字段不能丢（fastjson2 默认省略，需 WriteNulls）
        assertEquals("{\"a\":null,\"b\":1}", udf.evaluate(args("{\"a\":null,\"b\":1}", "$")));
    }

    @Test
    public void testStringValueReturnsQuotedText() throws Exception {
        assertEquals("\"x\"", udf.evaluate(args("{\"a\":\"x\"}", "$.a")));
    }

    @Test
    public void testSpecialKeyWithDot() throws Exception {
        assertEquals("1", udf.evaluate(args("{\"a.b\":1}", "$['a.b']")));
    }

    @Test
    public void testChineseKey() throws Exception {
        assertEquals("\"测试\"", udf.evaluate(args("{\"名称\":\"测试\"}", "$.名称")));
    }

    @Test(expected = JsonPathException.class)
    public void testInvalidPathThrows() throws Exception {
        // '$a' 缺少点/括号 → path 语法非法（写法严格，抛错对齐 MC）
        udf.evaluate(args("{\"a\":1}", "$a"));
    }

    @Test(expected = JsonPathException.class)
    public void testWildcardPathThrows() throws Exception {
        udf.evaluate(args("{\"a\":{\"b\":1}}", "$.a.*"));
    }

    @Test
    public void testInvalidJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("abc", "$.a")));
    }

    @Test
    public void testNullJsonInput() throws Exception {
        assertNull(udf.evaluate(args(null, "$.a")));
    }

    @Test
    public void testNullPathInput() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", null)));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonExtractUdf bad = new JsonExtractUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
