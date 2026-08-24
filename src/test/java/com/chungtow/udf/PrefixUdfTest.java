package com.chungtow.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * PrefixUdf 单元测试：GenericUDF 可直接构造调用，无需启动 Spark。
 *
 * <p>覆盖矩阵：正常值 / null 透传 / 空串 / 短值 / 等于边界 / 中文与特殊字符 / 入参个数错误。</p>
 */
public class PrefixUdfTest {

    private PrefixUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new PrefixUdf();
        udf.initialize(new ObjectInspector[] {
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
    public void testNormal() throws Exception {
        assertEquals("hell", udf.evaluate(args("helloworld")));
    }

    @Test
    public void testNullPassthrough() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testEmptyString() throws Exception {
        assertEquals("", udf.evaluate(args("")));
    }

    @Test
    public void testShortString() throws Exception {
        assertEquals("abc", udf.evaluate(args("abc")));
    }

    @Test
    public void testExactlyBoundary() throws Exception {
        assertEquals("abcd", udf.evaluate(args("abcd")));
    }

    @Test
    public void testChinese() throws Exception {
        assertEquals("中文字符", udf.evaluate(args("中文字符串测试")));
    }

    @Test
    public void testSpecialChars() throws Exception {
        assertEquals("a-b[", udf.evaluate(args("a-b[c-def")));
    }

    @Test
    public void testNonStringInput() throws Exception {
        // 数字等非字符串入参按 String.valueOf 处理
        assertEquals("1234", udf.evaluate(args(123456L)));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        PrefixUdf bad = new PrefixUdf();
        bad.initialize(new ObjectInspector[] {
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
