package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * RegexpCountUdf 单元测试：正则匹配计数（对齐 MC REGEXP_COUNT）。
 */
public class RegexpCountUdfTest {

    /** 按参数值类型构建 OI（整数→long OI、NULL→VOID OI、其余→string OI），贴近引擎解析。 */
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
        RegexpCountUdf u = new RegexpCountUdf();
        u.initialize(ois);
        GenericUDF.DeferredObject[] d = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object v = values[i];
            d[i] = new GenericUDF.DeferredJavaObject(v);
        }
        return u.evaluate(d);
    }

    @Test
    public void testFullCount() throws Exception {
        assertEquals(3, eval("a1b2c3", "[0-9]"));
    }

    @Test
    public void testFromPos() throws Exception {
        // POC 实证：位置 3 起计数 = 2
        assertEquals(2, eval("a1b2c3", "[0-9]", 3));
    }

    @Test
    public void testWordCount() throws Exception {
        assertEquals(2, eval("hello world hello", "hello"));
    }

    @Test
    public void testNoMatch() throws Exception {
        assertEquals(0, eval("abc", "[0-9]"));
    }

    @Test
    public void testFromPosOutOfRange() throws Exception {
        assertEquals(0, eval("a1b2c3", "[0-9]", 100));
    }

    @Test
    public void testFromPosZero() throws Exception {
        assertEquals(0, eval("a1b2c3", "[0-9]", 0));
    }

    @Test
    public void testFromPosNegative() throws Exception {
        assertEquals(0, eval("a1b2c3", "[0-9]", -1));
    }

    @Test
    public void testLongTypeFromPos() throws Exception {
        assertEquals(2, eval("a1b2c3", "[0-9]", Long.valueOf(3)));
    }

    @Test
    public void testNullStr() throws Exception {
        assertNull(eval((Object) null, "[0-9]"));
    }

    @Test
    public void testNullPattern() throws Exception {
        assertNull(eval("a1b2c3", null));
    }

    @Test
    public void testNullFromPos() throws Exception {
        assertNull(eval("a1b2c3", "[0-9]", null));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        RegexpCountUdf bad = new RegexpCountUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
