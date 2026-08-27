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
 * UrlEncodeUdf 单元测试：URL 百分号编码（对齐 MC URL_ENCODE）。
 */
public class UrlEncodeUdfTest {

    private UrlEncodeUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new UrlEncodeUdf();
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
    public void testSpaceBecomesPlus() throws Exception {
        assertEquals("a+b", udf.evaluate(args("a b")));
    }

    @Test
    public void testReservedCharsUnencoded() throws Exception {
        // 字母数字与 -_.* 保留
        assertEquals("a-b_c.d*e", udf.evaluate(args("a-b_c.d*e")));
    }

    @Test
    public void testTildeEncoded() throws Exception {
        assertEquals("%7E", udf.evaluate(args("~")));
    }

    @Test
    public void testChineseUtf8() throws Exception {
        assertEquals("%E4%B8%AD", udf.evaluate(args("中")));
    }

    @Test
    public void testUrlWithQuery() throws Exception {
        assertEquals("a%3D1%26b%3D2", udf.evaluate(args("a=1&b=2")));
    }

    @Test
    public void testNullReturnsNull() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testEmptyString() throws Exception {
        assertEquals("", udf.evaluate(args("")));
    }

    @Test
    public void testChineseMixed() throws Exception {
        assertEquals("%E4%B8%AD%E6%96%87", udf.evaluate(args("中文")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        UrlEncodeUdf bad = new UrlEncodeUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
