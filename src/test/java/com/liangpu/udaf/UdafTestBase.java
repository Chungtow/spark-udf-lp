package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;

/**
 * UDAF 单测公共驱动（对齐 §9.2/§10.2）：
 * <ul>
 *   <li>直接驱动 {@code iterate} / {@code merge}（Spark 对 Hive UDAF 的实际调用路径，
 *       而非 aggregate(DeferredObject[])——hive 2.3.9 默认不自动解包）</li>
 *   <li>多参数行 iterate（每行 = 一个参数数组）</li>
 *   <li>PARTIAL1 → terminatePartial → PARTIAL2 merge 分片合并链（模拟 shuffle 后 reduce 合并）</li>
 *   <li>TestParameterInfo 参数桩（直接指定 TypeInfo 列表）</li>
 * </ul>
 */
abstract class UdafTestBase {

    /** 逐行 iterate（每行是参数数组）。 */
    static GenericUDAFEvaluator.AggregationBuffer agg(GenericUDAFEvaluator eval, Object[]... rows)
            throws Exception {
        GenericUDAFEvaluator.AggregationBuffer buf = eval.getNewAggregationBuffer();
        for (Object[] row : rows) {
            eval.iterate(buf, row);
        }
        return buf;
    }

    /**
     * PARTIAL1 收集 → terminatePartial → PARTIAL2 merge 的完整分片合并链
     * （partial 产物为 ByteBuffer，直接传入 merge 模拟分片合并）。
     */
    static GenericUDAFEvaluator.AggregationBuffer partialMerge(GenericUDAFEvaluator e1,
            GenericUDAFEvaluator e2, Object[]... rows) throws Exception {
        GenericUDAFEvaluator.AggregationBuffer b1 = agg(e1, rows);
        Object partial = e1.terminatePartial(b1);
        GenericUDAFEvaluator.AggregationBuffer b2 = e2.getNewAggregationBuffer();
        e2.merge(b2, partial);
        return b2;
    }

    /** 参数桩：直接指定 TypeInfo 列表（用于 getEvaluator 校验测试）。 */
    static class TestParameterInfo implements GenericUDAFParameterInfo {
        private final TypeInfo[] tis;

        TestParameterInfo(TypeInfo... tis) {
            this.tis = tis;
        }

        @Override
        public TypeInfo[] getParameters() {
            return tis;
        }

        @Override
        public ObjectInspector[] getParameterObjectInspectors() {
            return new ObjectInspector[0];
        }

        @Override
        public boolean isAllColumns() {
            return false;
        }

        @Override
        public boolean isDistinct() {
            return false;
        }

        @Override
        public boolean isWindowing() {
            return false;
        }
    }
}
