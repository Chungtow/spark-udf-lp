package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** MedianUDAF 单测：精确中位数（奇数取中 / 偶数取均值 / BIGINT 与 DOUBLE / NULL / merge 链）。 */
public class MedianUDAFTest extends UdafTestBase {

    private MedianUDAF.MedianEvaluator evaluator(GenericUDAFEvaluator.Mode mode, boolean useDouble)
            throws Exception {
        MedianUDAF.MedianEvaluator eval = (MedianUDAF.MedianEvaluator)
                new MedianUDAF().getEvaluator(new TestParameterInfo(
                        useDouble ? TypeInfoFactory.doubleTypeInfo : TypeInfoFactory.longTypeInfo));
        ObjectInspector[] ois =
                (mode == GenericUDAFEvaluator.Mode.PARTIAL1 || mode == GenericUDAFEvaluator.Mode.COMPLETE)
                        ? new ObjectInspector[]{useDouble
                        ? PrimitiveObjectInspectorFactory.javaDoubleObjectInspector
                        : PrimitiveObjectInspectorFactory.javaLongObjectInspector}
                        : new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableBinaryObjectInspector};
        eval.init(mode, ois);
        return eval;
    }

    private double median(MedianUDAF.MedianEvaluator eval, Object[]... rows) throws Exception {
        Object out = eval.terminate(agg(eval, rows));
        return ((Number) out).doubleValue();
    }

    @Test
    public void testOddCount() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, false);
        assertEquals(2.0, median(eval, new Object[]{1L}, new Object[]{3L}, new Object[]{2L}), 0.0);
    }

    @Test
    public void testEvenCountAverage() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, false);
        assertEquals(2.5, median(eval, new Object[]{1L}, new Object[]{2L},
                new Object[]{3L}, new Object[]{4L}), 0.0);
    }

    @Test
    public void testSingleValue() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, false);
        assertEquals(7.0, median(eval, new Object[]{7L}), 0.0);
    }

    @Test
    public void testNullIgnored() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, false);
        assertEquals(3.0, median(eval, new Object[]{(Object) null},
                new Object[]{5L}, new Object[]{1L}), 0.0); // 1,5 -> (1+5)/2
    }

    @Test
    public void testDoubleInput() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, true);
        assertEquals(2.0, median(eval, new Object[]{1.5d}, new Object[]{2.5d}), 0.0);
    }

    @Test
    public void testAllNullReturnsNull() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, false);
        assertNull(eval.terminate(agg(eval, new Object[]{(Object) null}, new Object[]{(Object) null})));
    }

    @Test
    public void testEmptyGroupReturnsNull() throws Exception {
        MedianUDAF.MedianEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE, false);
        assertNull(eval.terminate(eval.getNewAggregationBuffer()));
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: 1,2；分片2: 3,4 -> (2+3)/2 = 2.5
        MedianUDAF.MedianEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1, false);
        MedianUDAF.MedianEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2, false);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{1L}, new Object[]{2L});
        MedianUDAF.MedianEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1, false);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{3L}, new Object[]{4L})));
        assertEquals(2.5, ((Number) e2.terminate(b2)).doubleValue(), 0.0);
    }

    @Test(expected = Exception.class)
    public void testWrongTypeRejected() throws Exception {
        // 仅支持 BIGINT/DOUBLE，STRING 应被拒
        new MedianUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.stringTypeInfo));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new MedianUDAF().getEvaluator(new TestParameterInfo(
                TypeInfoFactory.longTypeInfo, TypeInfoFactory.longTypeInfo));
    }
}
