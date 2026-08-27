package com.liangpu.udtf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.Collector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * KeyvalueTupleUDTF 单元测试：kv 多键一次提取（对齐 MC KEYVALUE_TUPLE）。
 */
public class KeyvalueTupleUDTFTest {

    private List<Object[]> run(Object... values) throws Exception {
        KeyvalueTupleUDTF udtf = new KeyvalueTupleUDTF();
        List<String> names = new ArrayList<String>();
        List<ObjectInspector> ois = new ArrayList<ObjectInspector>();
        for (int i = 0; i < values.length; i++) {
            names.add("arg" + i);
            ois.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        }
        udtf.initialize(ObjectInspectorFactory.getStandardStructObjectInspector(names, ois));

        final List<Object[]> rows = new ArrayList<Object[]>();
        udtf.setCollector(new Collector() {
            @Override
            public void collect(Object o) throws HiveException {
                rows.add((Object[]) o);
            }
        });
        udtf.process(values);
        udtf.close();
        return rows;
    }

    @Test
    public void testTwoKeys() throws Exception {
        List<Object[]> rows = run("k1=v1&k2=v2", "&", "=", "k1", "k2");
        assertEquals(1, rows.size());
        assertEquals("v1", rows.get(0)[0]);
        assertEquals("v2", rows.get(0)[1]);
    }

    @Test
    public void testKeyOrderFollowsArgs() throws Exception {
        List<Object[]> rows = run("k1=v1&k2=v2", "&", "=", "k2", "k1");
        assertEquals(1, rows.size());
        assertEquals("v2", rows.get(0)[0]);
        assertEquals("v1", rows.get(0)[1]);
    }

    @Test
    public void testMissingKeyIsNull() throws Exception {
        List<Object[]> rows = run("k1=v1", "&", "=", "k1", "k9");
        assertEquals(1, rows.size());
        assertEquals("v1", rows.get(0)[0]);
        assertNull(rows.get(0)[1]);
    }

    @Test
    public void testCustomDelimiter() throws Exception {
        List<Object[]> rows = run("k1:v1;k2:v2", ";", ":", "k2", "k1");
        assertEquals(1, rows.size());
        assertEquals("v2", rows.get(0)[0]);
        assertEquals("v1", rows.get(0)[1]);
    }

    @Test
    public void testNullStrNoOutput() throws Exception {
        assertEquals(0, run(null, "&", "=", "k1").size());
    }

    @Test
    public void testNullSplitterNoOutput() throws Exception {
        assertEquals(0, run("k1=v1", null, "=", "k1").size());
    }

    @Test
    public void testEmptySplitterNoOutput() throws Exception {
        assertEquals(0, run("k1=v1", "", "=", "k1").size());
    }

    @Test
    public void testNonKvStructureAllNull() throws Exception {
        // 段内无 split2 → 全部跳过 → 输出 1 行全 NULL
        List<Object[]> rows = run("abc", "&", "=", "k1");
        assertEquals(1, rows.size());
        assertNull(rows.get(0)[0]);
    }

    @Test
    public void testDuplicateKeyTakesFirst() throws Exception {
        List<Object[]> rows = run("k1=v1&k1=v9", "&", "=", "k1");
        assertEquals(1, rows.size());
        assertEquals("v1", rows.get(0)[0]);
    }

    @Test
    public void testValueContainingSeparator() throws Exception {
        List<Object[]> rows = run("k1=v1=x", "&", "=", "k1");
        assertEquals(1, rows.size());
        assertEquals("v1=x", rows.get(0)[0]);
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        KeyvalueTupleUDTF udtf = new KeyvalueTupleUDTF();
        ObjectInspector oi = PrimitiveObjectInspectorFactory.javaStringObjectInspector;
        udtf.initialize(ObjectInspectorFactory.getStandardStructObjectInspector(
                Arrays.asList("a", "b", "c"), Arrays.asList(oi, oi, oi)));
    }
}
