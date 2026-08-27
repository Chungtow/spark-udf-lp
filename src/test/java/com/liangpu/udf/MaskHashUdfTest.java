package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * MaskHashUdf 单元测试：脱敏哈希（SHA-256 hex，64 字符小写）。
 */
public class MaskHashUdfTest {

    private MaskHashUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new MaskHashUdf();
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
    public void testKnownSha256OfAbc() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                udf.evaluate(args("abc")));
    }

    @Test
    public void testFixedLengthAndHexCharset() throws Exception {
        String h = (String) udf.evaluate(args("hello world"));
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testSameInputSameOutput() throws Exception {
        assertEquals(udf.evaluate(args("foo")), udf.evaluate(args("foo")));
    }

    @Test
    public void testDifferentInputsDiffer() throws Exception {
        assertNotEquals(udf.evaluate(args("foo")), udf.evaluate(args("fooo")));
    }

    @Test
    public void testUnicode() throws Exception {
        String h = (String) udf.evaluate(args("中文"));
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testNullReturnsNull() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testNonStringReturnsNull() throws Exception {
        // 非字符串类型入参 → NULL（契约 ADR-13）；按引擎解析用 int OI 初始化
        MaskHashUdf u = new MaskHashUdf();
        u.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaIntObjectInspector
        });
        assertNull(u.evaluate(args(Integer.valueOf(123))));
    }

    @Test
    public void testTextWritableValue() throws Exception {
        // 引擎中 string 列为 Text（Writable），应正常哈希而非误判为非字符串
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                udf.evaluate(args(new org.apache.hadoop.io.Text("abc"))));
    }

    @Test
    public void testEmptyStringFixedHash() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                udf.evaluate(args("")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        MaskHashUdf bad = new MaskHashUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
