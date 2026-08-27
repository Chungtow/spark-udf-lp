package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** WmConcatUDAF 单测：不去重连接 / NULL 忽略 / 全 NULL 返回 null / merge 链（ADR-14 后分片分隔符）。 */
public class WmConcatUDAFTest extends UdafTestBase {

    private WmConcatUDAF.WmConcatEvaluator evaluator(GenericUDAFEvaluator.Mode mode) throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = (WmConcatUDAF.WmConcatEvaluator)
                new WmConcatUDAF().getEvaluator(new TestParameterInfo(
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

    @Test
    public void testBasic() throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{",", "a"}, new Object[]{",", "b"}, new Object[]{",", "c"}));
        assertEquals("a,b,c", out);
    }

    @Test
    public void testNoDedup() throws Exception {
        // 不去重（区别于 uda_string_agg）
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{",", "a"}, new Object[]{",", "a"}));
        assertEquals("a,a", out);
    }

    @Test
    public void testCustomSep() throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{";", "a"}, new Object[]{";", "b"}));
        assertEquals("a;b", out);
    }

    @Test
    public void testNullColIgnored() throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval,
                new Object[]{",", "a"}, new Object[]{",", null}, new Object[]{",", "b"}));
        assertEquals("a,b", out);
    }

    @Test
    public void testSingleValue() throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval, new Object[]{",", "only"}));
        assertEquals("only", out);
    }

    @Test
    public void testAllNullReturnsNull() throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(agg(eval,
                new Object[]{",", null}, new Object[]{",", null})));
    }

    @Test
    public void testEmptyGroupReturnsNull() throws Exception {
        WmConcatUDAF.WmConcatEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(eval.getNewAggregationBuffer()));
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // 分片1: "a,b"；分片2: "c" -> 合并 = A + B.sep(",") + B = "a,b,c"（ADR-14 后分片分隔符）
        WmConcatUDAF.WmConcatEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        WmConcatUDAF.WmConcatEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{",", "a"}, new Object[]{",", "b"});
        WmConcatUDAF.WmConcatEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{",", "c"})));
        assertEquals("a,b,c", e2.terminate(b2));
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        new WmConcatUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.stringTypeInfo));
    }
}
