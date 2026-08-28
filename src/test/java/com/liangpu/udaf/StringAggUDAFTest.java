package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.Text;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * StringAggUDAF 单元测试：直接驱动 GenericUDAFEvaluator 的完整阶段链，
 * 覆盖 COMPLETE（单机聚合）与 PARTIAL1 → PARTIAL2（shuffle 分片合并）两条路径。
 */
public class StringAggUDAFTest {

    /**
     * 驱动输入。注意: 直接调 iterate/merge（Spark 对 Hive UDAF 的实际调用路径），
     * 而非 aggregate(DeferredObject[])——hive 2.3.9 的 aggregate 默认实现不自动解包。
     */
    private GenericUDAFEvaluator.AggregationBuffer agg(GenericUDAFEvaluator eval, Object... values)
            throws Exception {
        GenericUDAFEvaluator.AggregationBuffer buf = eval.getNewAggregationBuffer();
        for (Object v : values) {
            eval.iterate(buf, new Object[]{v});
        }
        return buf;
    }

    @Test
    public void testCompleteMode() throws Exception {
        StringAggUDAF.StringAggEvaluator eval = new StringAggUDAF.StringAggEvaluator();
        eval.init(GenericUDAFEvaluator.Mode.COMPLETE,
                new ObjectInspector[]{PrimitiveObjectInspectorFactory.javaStringObjectInspector});

        // 重复值去重 + NULL 忽略；输出按字典序（分布式聚合输入顺序不确定）
        GenericUDAFEvaluator.AggregationBuffer buf =
                agg(eval, "b", "a", "b", null, "c");
        Text out = (Text) eval.terminate(buf);
        assertEquals("a,b,c", out.toString());
    }

    @Test
    public void testAllNullNoOutput() throws Exception {
        StringAggUDAF.StringAggEvaluator eval = new StringAggUDAF.StringAggEvaluator();
        eval.init(GenericUDAFEvaluator.Mode.COMPLETE,
                new ObjectInspector[]{PrimitiveObjectInspectorFactory.javaStringObjectInspector});

        GenericUDAFEvaluator.AggregationBuffer buf = agg(eval, (Object) null, null);
        Text out = (Text) eval.terminate(buf);
        assertEquals("", out.toString());
    }

    @Test
    public void testPartialMergeChain() throws Exception {
        // PARTIAL1: 单分片收集
        StringAggUDAF.StringAggEvaluator e1 = new StringAggUDAF.StringAggEvaluator();
        e1.init(GenericUDAFEvaluator.Mode.PARTIAL1,
                new ObjectInspector[]{PrimitiveObjectInspectorFactory.javaStringObjectInspector});
        GenericUDAFEvaluator.AggregationBuffer b1 = agg(e1, "b", "a", "b");
        Text partial = (Text) e1.terminatePartial(b1);

        // PARTIAL2: 合并另一分片后终值（模拟 shuffle 后 merge）
        StringAggUDAF.StringAggEvaluator e2 = new StringAggUDAF.StringAggEvaluator();
        e2.init(GenericUDAFEvaluator.Mode.PARTIAL2,
                new ObjectInspector[]{PrimitiveObjectInspectorFactory.writableStringObjectInspector});
        GenericUDAFEvaluator.AggregationBuffer b2 = e2.getNewAggregationBuffer();
        e2.merge(b2, partial);
        e2.merge(b2, new Text("c"));
        e2.merge(b2, new Text("b")); // 分片间也去重

        Text out = (Text) e2.terminate(b2);
        assertEquals("a,b,c", out.toString());
    }

    @Test(expected = Exception.class)
    public void testWrongArgCount() throws Exception {
        // 2 个参数应在 getEvaluator 阶段被拒
        new StringAggUDAF().getEvaluator(new TestParameterInfo(2));
    }

    /** 简化测试桩：仅用于模拟参数个数错误。 */
    private static class TestParameterInfo implements org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo {
        private final org.apache.hadoop.hive.serde2.typeinfo.TypeInfo[] tis;

        TestParameterInfo(int argc) {
            tis = new org.apache.hadoop.hive.serde2.typeinfo.TypeInfo[argc];
            for (int i = 0; i < argc; i++) {
                tis[i] = org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory.stringTypeInfo;
            }
        }

        public org.apache.hadoop.hive.serde2.typeinfo.TypeInfo[] getParameters() { return tis; }
        public ObjectInspector[] getParameterObjectInspectors() { return new ObjectInspector[0]; }
        public ObjectInspector[] getRawParameters() { return new ObjectInspector[0]; }
        public boolean isAllColumns() { return false; }
        public boolean isDistinct() { return false; }
        public boolean isStar() { return false; }
        public boolean isWindowing() { return false; }
    }
}
