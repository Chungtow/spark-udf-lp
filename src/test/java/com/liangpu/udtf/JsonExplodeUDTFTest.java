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
 * JsonExplodeUDTF 单元测试：JSON 数组/对象展开（对齐 MC JSON_EXPLODE）。
 */
public class JsonExplodeUDTFTest {

    private List<Object[]> run(String json) throws Exception {
        JsonExplodeUDTF udtf = new JsonExplodeUDTF();
        ObjectInspector argOI = PrimitiveObjectInspectorFactory.javaStringObjectInspector;
        udtf.initialize(ObjectInspectorFactory.getStandardStructObjectInspector(
                Arrays.asList("json"), Arrays.asList(argOI)));

        final List<Object[]> rows = new ArrayList<Object[]>();
        udtf.setCollector(new Collector() {
            @Override
            public void collect(Object o) throws HiveException {
                rows.add((Object[]) o);
            }
        });
        udtf.process(new Object[]{json});
        udtf.close();
        return rows;
    }

    @Test
    public void testArrayExplode() throws Exception {
        List<Object[]> rows = run("[1,true,2,{\"a\":456}]");
        assertEquals(4, rows.size());
        assertNull(rows.get(0)[0]);
        assertEquals("1", rows.get(0)[1]);
        assertEquals("true", rows.get(1)[1]);
        assertEquals("2", rows.get(2)[1]);
        assertEquals("{\"a\":456}", rows.get(3)[1]);
    }

    @Test
    public void testArrayOrderPreserved() throws Exception {
        List<Object[]> rows = run("[3,1,2]");
        assertEquals("3", rows.get(0)[1]);
        assertEquals("1", rows.get(1)[1]);
        assertEquals("2", rows.get(2)[1]);
    }

    @Test
    public void testObjectExplode() throws Exception {
        List<Object[]> rows = run("{\"a\":123,\"b\":\"hello\"}");
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0)[0]);
        assertEquals("123", rows.get(0)[1]);
        assertEquals("b", rows.get(1)[0]);
        assertEquals("\"hello\"", rows.get(1)[1]);
    }

    @Test
    public void testObjectValueIsNestedJson() throws Exception {
        // 嵌套数组/对象不递归，作为整体 value 输出
        List<Object[]> rows = run("{\"a\":[1,2]}");
        assertEquals(1, rows.size());
        assertEquals("a", rows.get(0)[0]);
        assertEquals("[1,2]", rows.get(0)[1]);
    }

    @Test
    public void testEmptyArray() throws Exception {
        assertEquals(0, run("[]").size());
    }

    @Test
    public void testEmptyObject() throws Exception {
        assertEquals(0, run("{}").size());
    }

    @Test
    public void testNullInputNoOutput() throws Exception {
        assertEquals(0, run(null).size());
    }

    @Test
    public void testInvalidJsonNoOutput() throws Exception {
        assertEquals(0, run("abc").size());
    }

    @Test
    public void testScalarInputNoOutput() throws Exception {
        // 合法但非数组/对象 → 0 行（数据宽容，与 MC 报错有偏差）
        assertEquals(0, run("123").size());
    }

    @Test
    public void testJsonNullLiteralNoOutput() throws Exception {
        assertEquals(0, run("null").size());
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        JsonExplodeUDTF udtf = new JsonExplodeUDTF();
        ObjectInspector oi = PrimitiveObjectInspectorFactory.javaStringObjectInspector;
        udtf.initialize(ObjectInspectorFactory.getStandardStructObjectInspector(
                Arrays.asList("a", "b"), Arrays.asList(oi, oi)));
    }
}
