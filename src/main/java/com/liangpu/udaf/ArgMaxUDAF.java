package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.AbstractGenericUDAFResolver;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.io.Serializable;

/**
 * UDAF：返回 v_max 取到最大值时对应的 v_ret（对标 MaxCompute arg_max(v_max, v_ret)）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT lpudf.arg_max(sal, ename) FROM lpudf.emp;  -- 薪水最高员工的姓名
 * </pre>
 *
 * <p>注意：参数顺序为“先比较列、后返回列”，与 Spark 内置 max_by(v_ret, v_max) 相反。</p>
 *
 * <p>边界语义：v_max 为 NULL 的行被忽略；并列（tie）时返回其中一行的 v_ret，
 * 结果非确定性；全组 v_max 均为 NULL 或空组返回 NULL。</p>
 */
@ExpressionDescription(
        usage = "arg_max(v_max, v_ret) - 返回 v_max 取到最大值时对应的 v_ret；参数序为比较列在前、返回列在后，NULL 忽略，并列结果非确定，全 NULL 返回 NULL。",
        arguments = "v_max - 用于比较取最大值的列（基础类型），NULL 所在行被忽略\nv_ret - v_max 最大时返回的关联列值（基础类型）")
public class ArgMaxUDAF extends AbstractGenericUDAFResolver {

    /** Spark 2.4 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 2) {
            throw new UDFArgumentTypeException(0,
                    "arg_max 需要 2 个参数，实际 " + (params == null ? 0 : params.length));
        }
        for (int i = 0; i < 2; i++) {
            if (params[i].getCategory() != ObjectInspector.Category.PRIMITIVE) {
                throw new UDFArgumentTypeException(i,
                        "arg_max 仅支持基础类型参数，参数 " + i + " 的类型为 " + params[i].getTypeName());
            }
        }
        return new ArgMaxEvaluator((PrimitiveTypeInfo) params[0], (PrimitiveTypeInfo) params[1]);
    }

    public static class ArgMaxEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo maxType;
        private final PrimitiveTypeInfo retType;
        private transient PrimitiveObjectInspector maxOI;
        private transient PrimitiveObjectInspector retOI;
        private transient ObjectInspector outputOI;

        public ArgMaxEvaluator(PrimitiveTypeInfo maxType, PrimitiveTypeInfo retType) {
            this.maxType = maxType;
            this.retType = retType;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            // 比较 OI 与返回 OI 均从类型推导（不依赖 init 参数），PARTIAL2/FINAL 的 merge 也能用
            maxOI = (PrimitiveObjectInspector) PrimitiveObjectInspectorFactory
                    .getPrimitiveJavaObjectInspector(maxType.getPrimitiveCategory());
            retOI = (PrimitiveObjectInspector) PrimitiveObjectInspectorFactory
                    .getPrimitiveJavaObjectInspector(retType.getPrimitiveCategory());
            outputOI = PrimitiveObjectInspectorFactory
                    .getPrimitiveJavaObjectInspector(retType.getPrimitiveCategory());
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return outputOI;
        }

        /** 聚合缓冲：当前最大值及其对应的返回列值。 */
        public static class ArgMaxBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            Object bestMax;
            Object bestRet;
            boolean seen;
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new ArgMaxBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            ArgMaxBuffer buf = (ArgMaxBuffer) agg;
            buf.bestMax = null;
            buf.bestRet = null;
            buf.seen = false;
        }

        /** 与当前最优值比较，若 newMax 更优则更新（供 iterate / merge 共用）。 */
        private void update(ArgMaxBuffer buf, Object newMax, Object newRet) throws HiveException {
            if (newMax == null) {
                return; // NULL 忽略
            }
            if (!buf.seen || ObjectInspectorUtils.compare(newMax, maxOI, buf.bestMax, maxOI) > 0) {
                buf.bestMax = newMax;
                buf.bestRet = newRet;
                buf.seen = true;
            }
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            if (parameters == null || parameters.length < 2) {
                return;
            }
            Object newMax = maxOI.getPrimitiveJavaObject(parameters[0]);
            if (newMax == null) {
                return;
            }
            Object newRet = retOI.getPrimitiveJavaObject(parameters[1]);
            update((ArgMaxBuffer) agg, newMax, newRet);
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            ArgMaxBuffer other = (ArgMaxBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            update((ArgMaxBuffer) agg, other.bestMax, other.bestRet);
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            ArgMaxBuffer buf = (ArgMaxBuffer) agg;
            return buf.seen ? buf.bestRet : null;
        }
    }
}
