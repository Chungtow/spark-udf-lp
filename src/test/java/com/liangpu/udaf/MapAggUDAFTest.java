package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** MapAggUDAF 单测：key 重复后者覆盖 / NULL key 忽略 / NULL value 保留 / merge 链。 */
public class MapAggUDAFTest extends UdafTestBase {

    private MapAggUDAF.MapAggEvaluator evaluator(GenericUDAFEvaluator.Mode mode) throws Exception {
        MapAggUDAF.MapAggEvaluator eval = (MapAggUDAF.MapAggEvaluator)
                new MapAggUDAF().getEvaluator(new TestParameterInfo(
                        TypeInfoFactory.stringTypeInfo, TypeInfoFactory.longTypeInfo));
        ObjectInspector[] ois =
                (mode == GenericUDAFEvaluator.Mode.PARTIAL1 || mode == GenericUDAFEvaluator.Mode.COMPLETE)
                        ? new ObjectInspector[]{
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                        PrimitiveObjectInspectorFactory.javaLongObjectInspector}
                        : new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableBinaryObjectInspector};
        eval.init(mode, ois);
        return eval;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> result(MapAggUDAF.MapAggEvaluator eval,
            GenericUDAFEvaluator.AggregationBuffer buf) throws Exception {
        return (Map<Object, Object>) eval.terminate(buf);
    }

    @Test
    public void testBasic() throws Exception {
        MapAggUDAF.MapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Object> m = result(eval, agg(eval,
                new Object[]{"a", 1L}, new Object[]{"b", 2L}));
        assertEquals(2, m.size());
        assertEquals(1L, m.get("a"));
        assertEquals(2L, m.get("b"));
    }

    @Test
    public void testDuplicateKeyLastWins() throws Exception {
        MapAggUDAF.MapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Object> m = result(eval, agg(eval,
                new Object[]{"a", 1L}, new Object[]{"a", 3L}));
        assertEquals(1, m.size());
        assertEquals(3L, m.get("a")); // 后出现的值覆盖先出现的值
    }

    @Test
    public void testNullKeyIgnored() throws Exception {
        MapAggUDAF.MapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Object> m = result(eval, agg(eval,
                new Object[]{(Object) null, 1L}, new Object[]{"b", 2L}));
        assertEquals(1, m.size());
        assertTrue(m.containsKey("b"));
        assertFalse(m.containsKey(null));
    }

    @Test
    public void testNullValueKept() throws Exception {
        MapAggUDAF.MapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Object> m = result(eval, agg(eval,
                new Object[]{"a", null}, new Object[]{"b", 2L}));
        assertEquals(2, m.size());
        assertTrue(m.containsKey("a"));
        assertNull(m.get("a")); // NULL value 保留
    }

    @Test
    public void testEmptyGroupReturnsEmptyMap() throws Exception {
        MapAggUDAF.MapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Object> m = result(eval, eval.getNewAggregationBuffer());
        assertNotNull(m);
        assertTrue(m.isEmpty());
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: {"a":1}
        MapAggUDAF.MapAggEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        MapAggUDAF.MapAggEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{"a", 1L});
        // 分片2: {"a":5, "b":2} —— 合并后 a 被后者覆盖
        MapAggUDAF.MapAggEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3,
                new Object[]{"a", 5L}, new Object[]{"b", 2L})));
        Map<Object, Object> m = result(e2, b2);
        assertEquals(2, m.size());
        assertEquals(5L, m.get("a"));
        assertEquals(2L, m.get("b"));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new MapAggUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.stringTypeInfo));
    }
}
