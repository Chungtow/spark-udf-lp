package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** ArgMaxUDAF 单测：参数序为比较列在前返回列在后（v_max, v_ret）/ NULL 忽略 / tie 取先见者 / merge 链。 */
public class ArgMaxUDAFTest extends UdafTestBase {

    private ArgMaxUDAF.ArgMaxEvaluator evaluator(GenericUDAFEvaluator.Mode mode) throws Exception {
        ArgMaxUDAF.ArgMaxEvaluator eval = (ArgMaxUDAF.ArgMaxEvaluator)
                new ArgMaxUDAF().getEvaluator(new TestParameterInfo(
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
        ArgMaxUDAF.ArgMaxEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{3L, "c"}, new Object[]{5L, "e"}, new Object[]{1L, "a"}));
        assertEquals("e", out);
    }

    @Test
    public void testNullMaxIgnored() throws Exception {
        ArgMaxUDAF.ArgMaxEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{(Object) null, "x"}, new Object[]{5L, "e"}));
        assertEquals("e", out);
    }

    @Test
    public void testNullRetKept() throws Exception {
        // v_ret 允许为 NULL（保留）
        ArgMaxUDAF.ArgMaxEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval, new Object[]{3L, null}));
        assertNull(out);
    }

    @Test
    public void testTieFirstSeen() throws Exception {
        // 并列时保留先见者（非确定性，实现为不更新）
        ArgMaxUDAF.ArgMaxEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{5L, "a"}, new Object[]{5L, "b"}));
        assertEquals("a", out);
    }

    @Test
    public void testAllNullReturnsNull() throws Exception {
        ArgMaxUDAF.ArgMaxEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(agg(eval,
                new Object[]{(Object) null, "x"}, new Object[]{(Object) null, "y"})));
    }

    @Test
    public void testEmptyGroupReturnsNull() throws Exception {
        ArgMaxUDAF.ArgMaxEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(eval.getNewAggregationBuffer()));
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: max=3 -> "c"; 分片2: max=5 -> "e"; 合并后应为 "e"
        ArgMaxUDAF.ArgMaxEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        ArgMaxUDAF.ArgMaxEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{3L, "c"});
        ArgMaxUDAF.ArgMaxEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{5L, "e"})));
        assertEquals("e", e2.terminate(b2));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new ArgMaxUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.longTypeInfo));
    }
}
