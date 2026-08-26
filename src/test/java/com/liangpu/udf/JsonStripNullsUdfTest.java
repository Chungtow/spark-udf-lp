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
 * JsonStripNullsUdf 单元测试：递归移除 null（对齐 MC JSON_STRIP_NULLS，1~4 参数组合）。
 */
public class JsonStripNullsUdfTest {

    private JsonStripNullsUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new JsonStripNullsUdf();
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
    public void testArrayDefaultRemoveNull() throws Exception {
        assertEquals("[1,2]", udf.evaluate(args("[1,null,2,null]")));
    }

    @Test
    public void testArrayKeepNull() throws Exception {
        assertEquals("[1,null,2,null]", udf.evaluate(args("[1,null,2,null]", false)));
    }

    @Test
    public void testObjectNullFieldsRemoved() throws Exception {
        assertEquals("{\"a\":1}", udf.evaluate(args("{\"a\":1,\"b\":null}")));
    }

    @Test
    public void testNestedObject() throws Exception {
        // remove_empty=false：空对象保留
        assertEquals("{\"a\":{},\"b\":1}", udf.evaluate(args("{\"a\":{\"c\":null},\"b\":1}")));
    }

    @Test
    public void testRemoveEmptyObject() throws Exception {
        assertEquals("{\"b\":1}", udf.evaluate(args("{\"a\":{\"c\":null},\"b\":1}", true, true)));
    }

    @Test
    public void testRemoveEmptyArraysSkippedWhenIncludeArraysFalse() throws Exception {
        // include_arrays=false：数组整体跳过（不删 null、不递归、不删空数组）
        String json = "{\"a\":[null,{\"x\":null}],\"b\":[]}";
        assertEquals("{\"a\":[null,{\"x\":null}],\"b\":[]}", udf.evaluate(args(json, false, true)));
    }

    @Test
    public void testContractRemoveEmptyFalseTrue() throws Exception {
        // MC 文档契约示例 1（api-spec 276-277 行）
        String json = "{\"a\":{\"b\":{\"c\":null}},\"d\":[null],\"e\":[],\"f\":1}";
        assertEquals("{\"d\":[null],\"e\":[],\"f\":1}", udf.evaluate(args(json, false, true)));
    }

    @Test
    public void testContractRemoveEmptyTrueTrue() throws Exception {
        // MC 文档契约示例 2（api-spec 278-279 行）
        String json = "{\"a\":{\"b\":{\"c\":null}},\"d\":[null],\"e\":[],\"f\":1}";
        assertEquals("{\"f\":1}", udf.evaluate(args(json, true, true)));
    }

    @Test
    public void testArrayNullInsideObject() throws Exception {
        assertEquals("{\"a\":[1]}", udf.evaluate(args("{\"a\":[null,1],\"b\":null}")));
    }

    @Test
    public void testJsonNullLiteralInput() throws Exception {
        assertEquals("null", udf.evaluate(args("null")));
    }

    @Test
    public void testRootEmptyAfterStrip() throws Exception {
        // 根对象全部 null 字段删除后为空，remove_empty=true → JSON null
        assertEquals("null", udf.evaluate(args("{\"a\":null}", true, true)));
    }

    @Test
    public void testPathLimited() throws Exception {
        // path 限定：仅处理 $.a 下内容，节点本身保留
        String json = "{\"a\":{\"x\":null},\"b\":{\"y\":null}}";
        assertEquals("{\"a\":{},\"b\":{\"y\":null}}", udf.evaluate(args(json, true, false, "$.a")));
    }

    @Test
    public void testPathInvalidReturnsOriginal() throws Exception {
        // path 无效 → 原样返回
        String json = "{\"a\":1}";
        assertEquals(json, udf.evaluate(args(json, true, false, "b")));
    }

    @Test
    public void testPathNullReturnsOriginal() throws Exception {
        String json = "{\"a\":1}";
        assertEquals(json, udf.evaluate(args(json, true, false, null)));
    }

    @Test
    public void testNullJsonInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testNullIncludeArraysReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", null)));
    }

    @Test
    public void testNullRemoveEmptyReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("{\"a\":1}", true, null)));
    }

    @Test
    public void testInvalidJsonReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("abc")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonStripNullsUdf bad = new JsonStripNullsUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
