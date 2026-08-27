package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** MultimapAggUDAF 单测：Map<k, Array<v>>（NULL key 忽略 / NULL value 保留 / merge 链）。 */
public class MultimapAggUDAFTest extends UdafTestBase {

    private MultimapAggUDAF.MultimapAggEvaluator evaluator(GenericUDAFEvaluator.Mode mode)
            throws Exception {
        MultimapAggUDAF.MultimapAggEvaluator eval = (MultimapAggUDAF.MultimapAggEvaluator)
                new MultimapAggUDAF().getEvaluator(new TestParameterInfo(
                        TypeInfoFactory.stringTypeInfo, TypeInfoFactory.stringTypeInfo));
        ObjectInspector[] ois =
                (mode == GenericUDAFEvaluator.Mode.PARTIAL1 || mode == GenericUDAFEvaluator.Mode.COMPLETE)
                        ? new ObjectInspector[]{
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector}
                        : new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableBinaryObjectInspector};
        eval.init(mode, ois);
        return eval;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, List<Object>> result(MultimapAggUDAF.MultimapAggEvaluator eval,
            GenericUDAFEvaluator.AggregationBuffer buf) throws Exception {
        return (Map<Object, List<Object>>) eval.terminate(buf);
    }

    @Test
    public void testBasic() throws Exception {
        MultimapAggUDAF.MultimapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, List<Object>> m = result(eval, agg(eval,
                new Object[]{"a", "x"}, new Object[]{"a", "y"}, new Object[]{"b", "z"}));
        assertEquals(2, m.size());
        assertEquals(Arrays.<Object>asList("x", "y"), m.get("a"));
        assertEquals(Arrays.<Object>asList("z"), m.get("b"));
    }

    @Test
    public void testNullKeyIgnored() throws Exception {
        MultimapAggUDAF.MultimapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, List<Object>> m = result(eval, agg(eval,
                new Object[]{(Object) null, "x"}, new Object[]{"b", "z"}));
        assertEquals(1, m.size());
        assertTrue(m.containsKey("b"));
        assertTrue(!m.containsKey(null));
    }

    @Test
    public void testNullValueKept() throws Exception {
        MultimapAggUDAF.MultimapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, List<Object>> m = result(eval, agg(eval,
                new Object[]{"a", null}, new Object[]{"a", "y"}));
        List<Object> list = m.get("a");
        assertEquals(2, list.size());
        assertNull(list.get(0)); // NULL value 保留进数组
        assertEquals("y", list.get(1));
    }

    @Test
    public void testEmptyGroupReturnsEmptyMap() throws Exception {
        MultimapAggUDAF.MultimapAggEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, List<Object>> m = result(eval, eval.getNewAggregationBuffer());
        assertNotNull(m);
        assertTrue(m.isEmpty());
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: {a:[x]}；分片2: {a:[y]} -> {a:[x,y]}
        MultimapAggUDAF.MultimapAggEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        MultimapAggUDAF.MultimapAggEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{"a", "x"});
        MultimapAggUDAF.MultimapAggEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{"a", "y"})));
        Map<Object, List<Object>> m = result(e2, b2);
        assertEquals(Arrays.<Object>asList("x", "y"), m.get("a"));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new MultimapAggUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.stringTypeInfo));
    }
}
