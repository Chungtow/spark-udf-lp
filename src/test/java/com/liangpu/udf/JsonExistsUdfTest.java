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
 * JsonExistsUdf 单元测试：JSONPath 存在性判断（对齐 MC JSON_EXISTS）。
 */
public class JsonExistsUdfTest {

    private JsonExistsUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonExistsUdf();
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
    public void testExists() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":1,\"b\":2}", "$.a")));
    }

    @Test
    public void testMissing() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("{\"a\":1,\"b\":2}", "$.c")));
    }

    @Test
    public void testNullValueKeyExists() throws Exception {
        // 键存在但值为 JSON null → true
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":null}", "$.a")));
    }

    @Test
    public void testNestedArray() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("[1,2,{\"a\":34}]", "$[2].a")));
    }

    @Test
    public void testIndexOutOfRange() throws Exception {
        assertEquals(Boolean.FALSE, udf.evaluate(args("[1,2,3]", "$[5]")));
    }

    @Test
    public void testRootExists() throws Exception {
        assertEquals(Boolean.TRUE, udf.evaluate(args("{\"a\":1}", "$")));
    }

    @Test(expected = JsonPathException.class)
    public void testInvalidPathThrows() throws Exception {
        udf.evaluate(args("{\"a\":1}", "$a"));
    }

    @Test
    public void testNullJsonInput() throws Exception {
        assertNull(udf.evaluate(args(null, "$.a")));
    }

    @Test
    public void testNullPathInput() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", null)));
    }

    @Test
    public void testInvalidJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("abc", "$.a")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonExistsUdf bad = new JsonExistsUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
