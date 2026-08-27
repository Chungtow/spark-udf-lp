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
 * KeyvalueUdf 单元测试：半结构化 kv 串提取（对齐 MC KEYVALUE）。
 */
public class KeyvalueUdfTest {

    private KeyvalueUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new KeyvalueUdf();
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
    public void testDefaultTwoArgs() throws Exception {
        assertEquals("v2", udf.evaluate(args("k1=v1&k2=v2", "k2")));
    }

    @Test
    public void testDefaultTwoArgsFirstKey() throws Exception {
        assertEquals("v1", udf.evaluate(args("k1=v1&k2=v2", "k1")));
    }

    @Test
    public void testCustomFourArgs() throws Exception {
        assertEquals("v2", udf.evaluate(args("k1:v1;k2:v2", ";", ":", "k2")));
    }

    @Test
    public void testSinglePair() throws Exception {
        assertEquals("v1", udf.evaluate(args("k1=v1", "k1")));
    }

    @Test
    public void testKeyMissingReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("k1=v1&k2=v2", "k9")));
    }

    @Test
    public void testEmptyPairSegments() throws Exception {
        // 首尾空段跳过，不受影响
        assertEquals("v1", udf.evaluate(args("&k1=v1&", "k1")));
    }

    @Test
    public void testEmptyValue() throws Exception {
        assertEquals("", udf.evaluate(args("k1=&k2=v2", "k1")));
    }

    @Test
    public void testSegmentWithoutSeparator() throws Exception {
        // "k2" 段无 '=' → 跳过 → key k2 不存在
        assertNull(udf.evaluate(args("k1=v1;k2", "k2")));
    }

    @Test
    public void testDuplicateKeyTakesFirst() throws Exception {
        assertEquals("v1", udf.evaluate(args("k1=v1&k1=v9", "k1")));
    }

    @Test
    public void testNullStrReturnsNull() throws Exception {
        assertNull(udf.evaluate(args(null, "k1")));
    }

    @Test
    public void testNullKeyReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("k1=v1", null)));
    }

    @Test
    public void testNullSplitterReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("k1=v1", null, "=", "k1")));
    }

    @Test
    public void testEmptySplitterReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("k1=v1", "", "=", "k1")));
    }

    @Test
    public void testValueContainingSeparator() throws Exception {
        // 值内含 '=' 取第一个分隔后的后半段（split 2）
        assertEquals("v1=x", udf.evaluate(args("k1=v1=x", "k1")));
    }

    @Test
    public void testNullLiteralVoidOiInitializes() throws Exception {
        // 引擎中 NULL 字面量为 VOID 类型 OI，应放行（数据宽容）
        KeyvalueUdf ok = new KeyvalueUdf();
        ok.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaVoidObjectInspector
        });
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCountThree() throws Exception {
        KeyvalueUdf bad = new KeyvalueUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
