package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * RegexpSubstrUdf 单元测试：正则子串提取（对齐 MC REGEXP_SUBSTR）。
 */
public class RegexpSubstrUdfTest {

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
        RegexpSubstrUdf u = new RegexpSubstrUdf();
        u.initialize(ois);
        GenericUDF.DeferredObject[] d = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object v = values[i];
            d[i] = new GenericUDF.DeferredJavaObject(v);
        }
        return u.evaluate(d);
    }

    @Test
    public void testDefault() throws Exception {
        assertEquals("123", eval("abc123def456", "[0-9]+"));
    }

    @Test
    public void testSecondOccurrence() throws Exception {
        assertEquals("456", eval("abc123def456", "[0-9]+", 1, 2));
    }

    @Test
    public void testFromPosMid() throws Exception {
        // fromPos=4 从第 4 字符 '2' 起 → 匹配 "2"
        assertEquals("2", eval("a1b2c3", "[0-9]", 4));
    }

    @Test
    public void testFromPosSkipsFirst() throws Exception {
        // fromPos=5 从第 5 字符 'c' 起 → 跳过 1、2，匹配 "3"
        assertEquals("3", eval("a1b2c3", "[0-9]", 5));
    }

    @Test
    public void testNoMatchReturnsNull() throws Exception {
        assertNull(eval("abc", "[0-9]+"));
    }

    @Test
    public void testFromPosOutOfRangeReturnsNull() throws Exception {
        assertNull(eval("a1b2c3", "[0-9]", 100));
    }

    @Test
    public void testFromPosZeroReturnsNull() throws Exception {
        assertNull(eval("a1b2c3", "[0-9]", 0));
    }

    @Test
    public void testOccurrenceZeroReturnsNull() throws Exception {
        assertNull(eval("a1b2c3", "[0-9]", 1, 0));
    }

    @Test
    public void testOccurrenceExceededReturnsNull() throws Exception {
        assertNull(eval("a1b2c3", "[0-9]", 1, 5));
    }

    @Test
    public void testNullStr() throws Exception {
        assertNull(eval((Object) null, "[0-9]+"));
    }

    @Test
    public void testNullPattern() throws Exception {
        assertNull(eval("abc123", null));
    }

    @Test
    public void testNullFromPos() throws Exception {
        assertNull(eval("abc123", "[0-9]+", null));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        RegexpSubstrUdf bad = new RegexpSubstrUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
