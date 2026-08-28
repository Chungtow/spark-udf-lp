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
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.io.Serializable;

/**
 * UDAF：任选组内一个非 NULL 值返回（对标 MaxCompute any_value / Spark 3.4+ any_value）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT deptno, lpudf.any_value(ename) FROM lpudf.emp GROUP BY deptno;
 * </pre>
 *
 * <p>边界语义：组内全部为 NULL 或空组时返回 NULL；取值为非确定性
 * （不保证返回组内哪一行），仅保证非 NULL。仅支持基础类型入参。</p>
 *
 * <p>实现要点：iterate 取第一个非 NULL 值即停止（seen 标记），
 * partial 分片用 Java 序列化经 {@link UdafSerialization} 传输。</p>
 */
@ExpressionDescription(
        usage = "any_value(col) - 任选组内一个非 NULL 值返回；全 NULL 或空组返回 NULL。",
        arguments = "col - 基础类型的输入列，NULL 值被忽略")
public class AnyValueUDAF extends AbstractGenericUDAFResolver {

    /** Spark 3.3.1 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 1) {
            throw new UDFArgumentTypeException(0,
                    "any_value 需要 1 个参数，实际 " + (params == null ? 0 : params.length));
        }
        if (params[0].getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(0,
                    "any_value 仅支持基础类型参数，不支持 " + params[0].getTypeName());
        }
        return new AnyValueEvaluator((PrimitiveTypeInfo) params[0]);
    }

    public static class AnyValueEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo inputType;
        private transient PrimitiveObjectInspector inputOI;
        private transient ObjectInspector outputOI;

        public AnyValueEvaluator(PrimitiveTypeInfo inputType) {
            this.inputType = inputType;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (m == Mode.PARTIAL1 || m == Mode.COMPLETE) {
                inputOI = (PrimitiveObjectInspector) parameters[0];
            }
            outputOI = PrimitiveObjectInspectorFactory
                    .getPrimitiveJavaObjectInspector(inputType.getPrimitiveCategory());
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return outputOI;
        }

        /** 聚合缓冲：首个非 NULL 值。 */
        public static class AnyValueBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            Object value;
            boolean seen;
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new AnyValueBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            AnyValueBuffer buf = (AnyValueBuffer) agg;
            buf.value = null;
            buf.seen = false;
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            AnyValueBuffer buf = (AnyValueBuffer) agg;
            if (buf.seen || parameters == null || parameters.length == 0) {
                return;
            }
            Object raw = inputOI.getPrimitiveJavaObject(parameters[0]);
            if (raw != null) {
                buf.value = raw;
                buf.seen = true;
            }
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            AnyValueBuffer other = (AnyValueBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            AnyValueBuffer buf = (AnyValueBuffer) agg;
            if (!buf.seen && other.seen) {
                buf.value = other.value;
                buf.seen = true;
            }
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            AnyValueBuffer buf = (AnyValueBuffer) agg;
            return buf.seen ? buf.value : null;
        }
    }
}
