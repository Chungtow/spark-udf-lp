package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.AbstractGenericUDAFResolver;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UDAF：统计列值频次分布，返回 Map&lt;key, bigint&gt;（对标 MaxCompute histogram）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT lpudf.histogram(job) FROM lpudf.emp;  -- 各职位出现次数
 * </pre>
 *
 * <p>区别于 Spark 内置 histogram_numeric（数值分箱直方图）：本函数输出精确频次计数。</p>
 *
 * <p>边界语义：NULL 不计入；空组或全 NULL 返回空 Map。</p>
 */
@ExpressionDescription(
        usage = "histogram(col) - 统计列值频次，返回 Map（key 为输入值，value 为出现次数 bigint）；NULL 不计入，空组返回空 Map。",
        arguments = "col - 基础类型的输入列，NULL 不计入")
public class HistogramUDAF extends AbstractGenericUDAFResolver {

    /** Spark 3.3.1 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 1) {
            throw new UDFArgumentTypeException(0,
                    "histogram 需要 1 个参数，实际 " + (params == null ? 0 : params.length));
        }
        if (params[0].getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(0,
                    "histogram 仅支持基础类型参数，不支持 " + params[0].getTypeName());
        }
        return new HistogramEvaluator((PrimitiveTypeInfo) params[0]);
    }

    public static class HistogramEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo inputType;
        private transient PrimitiveObjectInspector inputOI;
        private transient ObjectInspector outputOI;

        public HistogramEvaluator(PrimitiveTypeInfo inputType) {
            this.inputType = inputType;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (m == Mode.PARTIAL1 || m == Mode.COMPLETE) {
                inputOI = (PrimitiveObjectInspector) parameters[0];
            }
            outputOI = ObjectInspectorFactory.getStandardMapObjectInspector(
                    PrimitiveObjectInspectorFactory
                            .getPrimitiveJavaObjectInspector(inputType.getPrimitiveCategory()),
                    PrimitiveObjectInspectorFactory.javaLongObjectInspector);
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return outputOI;
        }

        /** 聚合缓冲：值到出现次数的映射。 */
        public static class HistogramBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            final LinkedHashMap<Object, Long> counts = new LinkedHashMap<Object, Long>();
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new HistogramBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            ((HistogramBuffer) agg).counts.clear();
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            if (parameters == null || parameters.length == 0) {
                return;
            }
            Object raw = inputOI.getPrimitiveJavaObject(parameters[0]);
            if (raw == null) {
                return; // NULL 不计入
            }
            HistogramBuffer buf = (HistogramBuffer) agg;
            Long prev = buf.counts.get(raw);
            buf.counts.put(raw, prev == null ? 1L : prev + 1L);
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            HistogramBuffer other = (HistogramBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            HistogramBuffer buf = (HistogramBuffer) agg;
            for (Map.Entry<Object, Long> e : other.counts.entrySet()) {
                Long prev = buf.counts.get(e.getKey());
                buf.counts.put(e.getKey(), prev == null ? e.getValue() : prev + e.getValue());
            }
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            HistogramBuffer buf = (HistogramBuffer) agg;
            return new LinkedHashMap<Object, Long>(buf.counts);
        }
    }
}
