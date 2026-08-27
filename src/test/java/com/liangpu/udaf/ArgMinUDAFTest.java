package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** ArgMinUDAF 单测：arg_max 的镜像（取最小值对应值 / tie 取先见者 / merge 链）。 */
public class ArgMinUDAFTest extends UdafTestBase {

    private ArgMinUDAF.ArgMinEvaluator evaluator(GenericUDAFEvaluator.Mode mode) throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = (ArgMinUDAF.ArgMinEvaluator)
                new ArgMinUDAF().getEvaluator(new TestParameterInfo(
                        TypeInfoFactory.longTypeInfo, TypeInfoFactory.stringTypeInfo));
        ObjectInspector[] ois =
                (mode == GenericUDAFEvaluator.Mode.PARTIAL1 || mode == GenericUDAFEvaluator.Mode.COMPLETE)
                        ? new ObjectInspector[]{
                        PrimitiveObjectInspectorFactory.javaLongObjectInspector,
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector}
                        : new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableBinaryObjectInspector};
        eval.init(mode, ois);
        return eval;
    }

    @Test
    public void testBasic() throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{3L, "c"}, new Object[]{5L, "e"}, new Object[]{1L, "a"}));
        assertEquals("a", out);
    }

    @Test
    public void testNullMinIgnored() throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{(Object) null, "x"}, new Object[]{1L, "a"}));
        assertEquals("a", out);
    }

    @Test
    public void testNullRetKept() throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(agg(eval, new Object[]{3L, null})));
    }

    @Test
    public void testTieFirstSeen() throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{1L, "a"}, new Object[]{1L, "b"}));
        assertEquals("a", out);
    }

    @Test
    public void testAllNullReturnsNull() throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(agg(eval,
                new Object[]{(Object) null, "x"}, new Object[]{(Object) null, "y"})));
    }

    @Test
    public void testEmptyGroupReturnsNull() throws Exception {
        ArgMinUDAF.ArgMinEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(eval.getNewAggregationBuffer()));
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: min=3 -> "c"; 分片2: min=1 -> "a"; 合并后应为 "a"
        ArgMinUDAF.ArgMinEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        ArgMinUDAF.ArgMinEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{3L, "c"});
        ArgMinUDAF.ArgMinEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{1L, "a"})));
        assertEquals("a", e2.terminate(b2));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new ArgMinUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.longTypeInfo));
    }
}
