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
 * UrlDecodeUdf 单元测试：URL 百分号解码（对齐 MC URL_DECODE）。
 */
public class UrlDecodeUdfTest {

    private UrlDecodeUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new UrlDecodeUdf();
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
    public void testPlusBecomesSpace() throws Exception {
        assertEquals("a b", udf.evaluate(args("a+b")));
    }

    @Test
    public void testChineseUtf8() throws Exception {
        assertEquals("中", udf.evaluate(args("%E4%B8%AD")));
    }

    @Test
    public void testRoundTrip() throws Exception {
        String encoded = (String) new UrlEncodeUdf().evaluate(args("a b 中文 ~x"));
        assertEquals("a b 中文 ~x", udf.evaluate(args(encoded)));
    }

    @Test
    public void testPlainText() throws Exception {
        assertEquals("abc", udf.evaluate(args("abc")));
    }

    @Test
    public void testInvalidHexReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("a%zz")));
    }

    @Test
    public void testTruncatedPercentReturnsNull() throws Exception {
        assertNull(udf.evaluate(args("a%")));
    }

    @Test
    public void testNullReturnsNull() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testEmptyString() throws Exception {
        assertEquals("", udf.evaluate(args("")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        UrlDecodeUdf bad = new UrlDecodeUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
