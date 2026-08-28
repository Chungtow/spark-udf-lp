package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * RegexpReplaceNthUdf 单元测试：只替换第 nth 次匹配（MC regexp_replace occurrence 增强）。
 */
public class RegexpReplaceNthUdfTest {

    /** 按参数值类型构建 OI（整数→long OI、NULL→VOID OI、其余→string OI）。 */
    private static ObjectInspector oiFor(Object v) {
        if (v instanceof Integer) {
            return PrimitiveObjectInspectorFactory.javaIntObjectInspector;
        }
        if (v instanceof Long) {
            return PrimitiveObjectInspectorFactory.javaLongObjectInspector;
        }
        if (v == null) {
            return PrimitiveObjectInspectorFactory.javaVoidObjectInspector;
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    private Object eval(Object... values) throws Exception {
        ObjectInspector[] ois = new ObjectInspector[values.length];
        for (int i = 0; i < values.length; i++) {
            ois[i] = oiFor(values[i]);
        }
        RegexpReplaceNthUdf u = new RegexpReplaceNthUdf();
        u.initialize(ois);
        GenericUDF.DeferredObject[] d = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object v = values[i];
            d[i] = new GenericUDF.DeferredJavaObject(v);
        }
        return u.evaluate(d);
    }

    @Test
    public void testNthDefaultFirst() throws Exception {
        assertEquals("abcXdef456", eval("abc123def456", "[0-9]+", "X"));
    }

    @Test
    public void testNthSecond() throws Exception {
        assertEquals("abc123defX", eval("abc123def456", "[0-9]+", "X", 2));
    }

    @Test
    public void testBackreferenceWithBackslash() throws Exception {
        assertEquals("a[12]b34", eval("a12b34", "(\\d+)", "[\\1]"));
    }

    @Test
    public void testBackreferenceWithDollar() throws Exception {
        assertEquals("a[12]b34", eval("a12b34", "(\\d+)", "[$1]"));
    }

    @Test
    public void testDollarInSourceUnparsed() throws Exception {
        // 非命中段含 $ 不被误解析
        assertEquals("a$bX", eval("a$b123", "[0-9]+", "X"));
    }

    @Test
    public void testOccurrenceExceededReturnsOriginal() throws Exception {
        assertEquals("abc123", eval("abc123", "[0-9]+", "X", 5));
    }

    @Test
    public void testOccurrenceZeroReturnsOriginal() throws Exception {
        assertEquals("abc123", eval("abc123", "[0-9]+", "X", 0));
    }

    @Test
    public void testNoMatchReturnsOriginal() throws Exception {
        assertEquals("abc", eval("abc", "[0-9]+", "X"));
    }

    @Test
    public void testNullStr() throws Exception {
        assertNull(eval((Object) null, "[0-9]+", "X"));
    }

    @Test
    public void testNullPattern() throws Exception {
        assertNull(eval("abc123", null, "X"));
    }

    @Test
    public void testNullRepl() throws Exception {
        assertNull(eval("abc123", "[0-9]+", null));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        RegexpReplaceNthUdf bad = new RegexpReplaceNthUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
