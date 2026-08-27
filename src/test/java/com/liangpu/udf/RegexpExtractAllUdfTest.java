package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * RegexpExtractAllUdf 单元测试：正则全量提取返回 array<string>。
 */
public class RegexpExtractAllUdfTest {

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

    @SuppressWarnings("unchecked")
    private List<String> eval(Object... values) throws Exception {
        ObjectInspector[] ois = new ObjectInspector[values.length];
        for (int i = 0; i < values.length; i++) {
            ois[i] = oiFor(values[i]);
        }
        RegexpExtractAllUdf u = new RegexpExtractAllUdf();
        u.initialize(ois);
        GenericUDF.DeferredObject[] d = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object v = values[i];
            d[i] = new GenericUDF.DeferredJavaObject(v);
        }
        return (List<String>) u.evaluate(d);
    }

    @Test
    public void testAllMatches() throws Exception {
        assertEquals(Arrays.asList("1", "2", "3"), eval("a1b2c3", "[0-9]"));
    }

    @Test
    public void testGreedySegments() throws Exception {
        assertEquals(Arrays.asList("123", "456"), eval("abc123def456", "[0-9]+"));
    }

    @Test
    public void testGroupExtraction() throws Exception {
        assertEquals(Arrays.asList("1", "2"), eval("k1=1&k2=2", "k(\\d)", 1));
    }

    @Test
    public void testNoMatchReturnsEmptyList() throws Exception {
        assertEquals(Collections.emptyList(), eval("abc", "[0-9]"));
    }

    @Test
    public void testNullStr() throws Exception {
        assertNull(eval(null, "[0-9]"));
    }

    @Test
    public void testNullPattern() throws Exception {
        assertNull(eval("a1b2c3", null));
    }

    @Test
    public void testNegativeGroupReturnsNull() throws Exception {
        assertNull(eval("a1b2c3", "[0-9]", -1));
    }

    @Test
    public void testGroupOutOfRangeReturnsNull() throws Exception {
        // 只有 1 个捕获组，group=2 越界 → NULL
        assertNull(eval("a1b2c3", "([0-9])", 2));
    }

    @Test
    public void testGroupZeroIsWholeMatch() throws Exception {
        assertEquals(Arrays.asList("123", "456"), eval("abc123def456", "(\\d+)", 0));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        RegexpExtractAllUdf bad = new RegexpExtractAllUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
