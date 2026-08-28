package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** AnyValueUDAF 单测：任取非 NULL 值（null 忽略 / 全 NULL 返回 null / 分片 merge 链）。 */
public class AnyValueUDAFTest extends UdafTestBase {

    private AnyValueUDAF.AnyValueEvaluator evaluator(GenericUDAFEvaluator.Mode mode) throws Exception {
        AnyValueUDAF.AnyValueEvaluator eval = (AnyValueUDAF.AnyValueEvaluator)
                new AnyValueUDAF().getEvaluator(new TestParameterInfo(TypeInfoFactory.stringTypeInfo));
        ObjectInspector[] ois =
                (mode == GenericUDAFEvaluator.Mode.PARTIAL1 || mode == GenericUDAFEvaluator.Mode.COMPLETE)
                        ? new ObjectInspector[]{PrimitiveObjectInspectorFactory.javaStringObjectInspector}
                        : new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableBinaryObjectInspector};
        eval.init(mode, ois);
        return eval;
    }

    @Test
    public void testCompleteModeAnyOfInput() throws Exception {
        AnyValueUDAF.AnyValueEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval, new Object[]{"a"}, new Object[]{"b"},
                new Object[]{(Object) null}, new Object[]{"c"}));
        Set<String> allowed = new HashSet<String>(Arrays.asList("a", "b", "c"));
        assertNotNull(out);
        assertTrue("返回值必须是输入中的非 NULL 值之一: " + out, allowed.contains(out));
    }

    @Test
    public void testSingleValue() throws Exception {
        AnyValueUDAF.AnyValueEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval, new Object[]{"x"}));
        assertEquals("x", out);
    }

    @Test
    public void testFirstNonNullKept() throws Exception {
        // 首个非 NULL 值即定值（seen 标记）
        AnyValueUDAF.AnyValueEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval, new Object[]{(Object) null},
                new Object[]{"z"}, new Object[]{"y"}));
        assertEquals("z", out);
    }

    @Test
    public void testAllNullReturnsNull() throws Exception {
        AnyValueUDAF.AnyValueEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        Object out = eval.terminate(agg(eval, new Object[]{(Object) null},
                new Object[]{(Object) null}));
        assertNull(out);
    }

    @Test
    public void testEmptyGroupReturnsNull() throws Exception {
        AnyValueUDAF.AnyValueEvaluator eval = evaluator(GenericUDAFEvaluator.Mode.COMPLETE);
        assertNull(eval.terminate(eval.getNewAggregationBuffer()));
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // PARTIAL1 分片1: 只有 NULL
        AnyValueUDAF.AnyValueEvaluator e1 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        // PARTIAL2 合并分片2: "b"
        AnyValueUDAF.AnyValueEvaluator e2 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL2);
        GenericUDAFEvaluator.AggregationBuffer b2 =
                partialMerge(e1, e2, new Object[]{(Object) null});
        // 再合并分片3: "b"
        AnyValueUDAF.AnyValueEvaluator e3 = evaluator(GenericUDAFEvaluator.Mode.PARTIAL1);
        e2.merge(b2, e3.terminatePartial(agg(e3, new Object[]{"b"})));
        Object out = e2.terminate(b2);
        assertEquals("b", out);
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        // 2 个参数应在 getEvaluator 阶段被拒
        new AnyValueUDAF().getEvaluator(
                new TestParameterInfo(TypeInfoFactory.stringTypeInfo, TypeInfoFactory.stringTypeInfo));
    }
}
