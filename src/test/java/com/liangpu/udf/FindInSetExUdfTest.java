package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * FindInSetExUdf 单元测试：分隔符列表位置查找（对齐 MC FIND_IN_SET 增强）。
 */
public class FindInSetExUdfTest {

    private FindInSetExUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new FindInSetExUdf();
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
    public void testPosition() throws Exception {
        assertEquals(2, udf.evaluate(args("b", "a,b,c")));
    }

    @Test
    public void testFirstPosition() throws Exception {
        assertEquals(1, udf.evaluate(args("a", "a,b,c")));
    }

    @Test
    public void testLastPosition() throws Exception {
        assertEquals(3, udf.evaluate(args("c", "a,b,c")));
    }

    @Test
    public void testNotFound() throws Exception {
        assertEquals(0, udf.evaluate(args("z", "a,b,c")));
    }

    @Test
    public void testCustomDelimiter() throws Exception {
        assertEquals(2, udf.evaluate(args("b", "a;b;c", ";")));
    }

    @Test
    public void testNullStrReturnsZero() throws Exception {
        assertEquals(0, udf.evaluate(args(null, "a,b,c")));
    }

    @Test
    public void testNullListReturnsZero() throws Exception {
        assertEquals(0, udf.evaluate(args("b", null)));
    }

    @Test
    public void testNullDelimiterUsesDefault() throws Exception {
        assertEquals(2, udf.evaluate(args("b", "a,b,c", null)));
    }

    @Test
    public void testEmptyDelimiterUsesDefault() throws Exception {
        assertEquals(2, udf.evaluate(args("b", "a,b,c", "")));
    }

    @Test
    public void testEmptyElementMatches() throws Exception {
        assertEquals(2, udf.evaluate(args("", "a,,b")));
    }

    @Test
    public void testDuplicateTakesFirstOccurrence() throws Exception {
        assertEquals(2, udf.evaluate(args("b", "a,b,b")));
    }

    @Test
    public void testNoTrim() throws Exception {
        // 精确匹配不 trim：' b ' 与 'b' 不同
        assertEquals(0, udf.evaluate(args(" b ", "a,b")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        FindInSetExUdf bad = new FindInSetExUdf();
        bad.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
