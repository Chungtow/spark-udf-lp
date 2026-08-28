package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.AbstractGenericUDAFResolver;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UDAF：数值列精确中位数（对标 MaxCompute median，支持 double）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT deptno, lpudf.median(sal) FROM lpudf.emp GROUP BY deptno;
 * </pre>
 *
 * <p>边界语义：按值排序后取中间值，偶数个值时取中间两个值的平均值；NULL 忽略；
 * 全 NULL 或空组返回 NULL。支持 BIGINT/DOUBLE 输入，输出 DOUBLE。</p>
 *
 * <p>实现要点：精确实现为全量缓冲，组内数据量极大时内存占用高
 * （大表场景可改用内置 percentile_approx 近似）。</p>
 */
@ExpressionDescription(
        usage = "median(col) - 返回数值列的中位数（精确）：排序后取中间值，偶数个取中间两值均值，NULL 忽略，全 NULL 或空组返回 NULL。",
        arguments = "col - 数值列（BIGINT/DOUBLE），NULL 被忽略")
public class MedianUDAF extends AbstractGenericUDAFResolver {

    /** Spark 3.3.1 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 1) {
            throw new UDFArgumentTypeException(0,
                    "median 需要 1 个参数，实际 " + (params == null ? 0 : params.length));
        }
        TypeInfo ti = params[0];
        if (ti.getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(0, "median 仅支持 BIGINT/DOUBLE 参数，不支持 " + ti.getTypeName());
        }
        PrimitiveTypeInfo pti = (PrimitiveTypeInfo) ti;
        PrimitiveObjectInspector.PrimitiveCategory cat = pti.getPrimitiveCategory();
        if (cat != PrimitiveObjectInspector.PrimitiveCategory.LONG
                && cat != PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
            throw new UDFArgumentTypeException(0, "median 仅支持 BIGINT/DOUBLE 参数，不支持 " + ti.getTypeName());
        }
        return new MedianEvaluator(pti);
    }

    public static class MedianEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo inputType;
        private transient PrimitiveObjectInspector inputOI;

        public MedianEvaluator(PrimitiveTypeInfo inputType) {
            this.inputType = inputType;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (m == Mode.PARTIAL1 || m == Mode.COMPLETE) {
                inputOI = (PrimitiveObjectInspector) parameters[0];
            }
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return PrimitiveObjectInspectorFactory.javaDoubleObjectInspector;
        }

        /** 聚合缓冲：全部非 NULL 值（全量缓冲，精确中位数）。 */
        public static class MedianBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            final List<Double> values = new ArrayList<Double>();
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new MedianBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            ((MedianBuffer) agg).values.clear();
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            if (parameters == null || parameters.length == 0) {
                return;
            }
            Object raw = inputOI.getPrimitiveJavaObject(parameters[0]);
            if (raw != null) {
                ((MedianBuffer) agg).values.add(((Number) raw).doubleValue());
            }
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            MedianBuffer other = (MedianBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            ((MedianBuffer) agg).values.addAll(other.values);
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            MedianBuffer buf = (MedianBuffer) agg;
            List<Double> sorted = new ArrayList<Double>(buf.values);
            if (sorted.isEmpty()) {
                return null; // 全 NULL 或空组
            }
            Collections.sort(sorted);
            int n = sorted.size();
            int mid = n / 2;
            if (n % 2 == 1) {
                return sorted.get(mid);
            }
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
    }
}
