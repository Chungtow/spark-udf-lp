package com.liangpu.udtf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.Collector;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDTF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * SplitRowsUDTF 单元测试：直接驱动 GenericUDTF 的 initialize / process / close，
 * 通过 Collector 收集 forward 输出，验证拆分语义与边界行为。
 */
public class SplitRowsUDTFTest {

    private List<Object[]> run(String str, String delim) throws Exception {
        SplitRowsUDTF udtf = new SplitRowsUDTF();
        ObjectInspector[] argOIs = {
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        };
        udtf.initialize(ObjectInspectorFactory.getStandardStructObjectInspector(
                Arrays.asList("s", "d"), Arrays.asList(argOIs)));

        final List<Object[]> rows = new ArrayList<Object[]>();
        udtf.setCollector(new Collector() {
            @Override
            public void collect(Object o) throws HiveException {
                rows.add((Object[]) o);
            }
        });
        udtf.process(new Object[]{str, delim});
        udtf.close();
        return rows;
    }

    @Test
    public void testBasicSplit() throws Exception {
        List<Object[]> rows = run("a,b,c", ",");
        assertEquals(3, rows.size());
        assertEquals("a", rows.get(0)[0]);
        assertEquals("b", rows.get(1)[0]);
        assertEquals("c", rows.get(2)[0]);
    }

    @Test
    public void testDelimiterIsRegex() throws Exception {
        // 分隔符含正则元字符时应按字面量拆分
        List<Object[]> rows = run("x.y.z", ".");
        assertEquals(3, rows.size());
        assertEquals("x", rows.get(0)[0]);
        assertEquals("z", rows.get(2)[0]);
    }

    @Test
    public void testNullInputNoOutput() throws Exception {
        assertEquals(0, run(null, ",").size());
    }

    @Test
    public void testEmptyStringNoOutput() throws Exception {
        assertEquals(0, run("", ",").size());
    }

    @Test
    public void testNullDelimiterDefaultsComma() throws Exception {
        List<Object[]> rows = run("a,b", null);
        assertEquals(2, rows.size());
        assertEquals("b", rows.get(1)[0]);
    }

    @Test
    public void testTrailingEmptyKept() throws Exception {
        // split(x, sep, -1) 保留尾部空项
        List<Object[]> rows = run("a,", ",");
        assertEquals(2, rows.size());
        assertEquals("", rows.get(1)[0]);
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        SplitRowsUDTF udtf = new SplitRowsUDTF();
        ObjectInspector oi = PrimitiveObjectInspectorFactory.javaStringObjectInspector;
        udtf.initialize(ObjectInspectorFactory.getStandardStructObjectInspector(
                Arrays.asList("s"), Arrays.asList(oi)));
    }
}
