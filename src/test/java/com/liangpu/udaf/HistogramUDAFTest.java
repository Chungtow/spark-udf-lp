package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** HistogramUDAF 单测：精确频次 Map（NULL 不计入 / 空组空 Map / merge 链）。 */
public class HistogramUDAFTest extends UdafTestBase {

    private HistogramUDAF.HistogramEvaluator evaluator(GenericUDAFEvaluator.Mode mode) throws Exception {
        HistogramUDAF.HistogramEvaluator eval = (HistogramUDAF.HistogramEvaluator)
                new HistogramUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.stringTypeInfo));
        ObjectInspector[] ois =
                (mode == GenericUDAFEvaluator.Mode.PARTIAL1 || mode == GenericUDAFEvaluator.Mode.COMPLETE)
                        ? new ObjectInspector[]{PrimitiveObjectInspectorFactory.javaStringObjectInspector}
                        : new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableBinaryObjectInspector};
        eval.init(mode, ois);
        return eval;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Long> result(HistogramUDAF.HistogramEvaluator eval,
            GenericUDAFEvaluator.AggregationBuffer buf) throws Exception {
        return (Map<Object, Long>) eval.terminate(buf);
    }

    @Test
    public void testBasic() throws Exception {
        HistogramUDAF.HistogramEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Long> m = result(eval, agg(eval,
                new Object[]{"a"}, new Object[]{"b"}, new Object[]{"a"}, new Object[]{"a"}));
        assertEquals(2, m.size());
        assertEquals(3L, (long) m.get("a"));
        assertEquals(1L, (long) m.get("b"));
    }

    @Test
    public void testNullIgnored() throws Exception {
        HistogramUDAF.HistogramEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Long> m = result(eval, agg(eval,
                new Object[]{"a"}, new Object[]{(Object) null}, new Object[]{"a"}));
        assertEquals(1, m.size());
        assertEquals(2L, (long) m.get("a"));
        assertTrue(!m.containsKey(null));
    }

    @Test
    public void testAllNullReturnsEmptyMap() throws Exception {
        HistogramUDAF.HistogramEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Long> m = result(eval, agg(eval,
                new Object[]{(Object) null}, new Object[]{(Object) null}));
        assertNotNull(m);
        assertTrue(m.isEmpty());
    }

    @Test
    public void testEmptyGroupReturnsEmptyMap() throws Exception {
        HistogramUDAF.HistogramEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Map<Object, Long> m = result(eval, eval.getNewAggregationBuffer());
        assertNotNull(m);
        assertTrue(m.isEmpty());
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: a,a；分片2: a,b -> {a:3, b:1}
        HistogramUDAF.HistogramEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        HistogramUDAF.HistogramEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{"a"}, new Object[]{"a"});
        HistogramUDAF.HistogramEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{"a"}, new Object[]{"b"})));
        Map<Object, Long> m = result(e2, b2);
        assertEquals(3L, (long) m.get("a"));
        assertEquals(1L, (long) m.get("b"));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new HistogramUDAF().getEvaluator(new TestParameterInfo(
                TypeInfoFactory.stringTypeInfo, TypeInfoFactory.stringTypeInfo));
    }
}
