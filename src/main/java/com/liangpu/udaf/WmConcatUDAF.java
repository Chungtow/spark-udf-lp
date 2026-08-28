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
 * UDAF：按分隔符连接组内字符串，不去重（对标 MaxCompute wm_concat(separator, str)）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT deptno, lpudf.wm_concat(',', ename) FROM lpudf.emp GROUP BY deptno;
 * </pre>
 *
 * <p>边界语义：NULL 被忽略；全 NULL 或空组返回 NULL；连接不去重、不排序
 * （与 uda_string_agg 的去重+字典序+逗号语义不同，两者并存）。</p>
 *
 * <p>实现要点（ADR-14）：merge 时用后分片的 separator 连接两分片内容
 * （A.buf + B.sep + B.buf）；separator 建议为常量，若为列值则分布式分片间
 * 拼接使用后分片的分隔符，结果对列值分隔符不作保证。</p>
 */
@ExpressionDescription(
        usage = "wm_concat(sep, col) - 按分隔符 sep 连接组内字符串（不去重、不排序），NULL 忽略，全 NULL 或空组返回 NULL；sep 建议为常量。",
        arguments = "sep - 连接分隔符（建议常量）\ncol - 待连接的字符串列（基础类型），NULL 被忽略")
public class WmConcatUDAF extends AbstractGenericUDAFResolver {

    /** Spark 3.3.1 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 2) {
            throw new UDFArgumentTypeException(0,
                    "wm_concat 需要 2 个参数，实际 " + (params == null ? 0 : params.length));
        }
        for (int i = 0; i < 2; i++) {
            if (params[i].getCategory() != ObjectInspector.Category.PRIMITIVE) {
                throw new UDFArgumentTypeException(i,
                        "wm_concat 仅支持基础类型参数，参数 " + i + " 的类型为 " + params[i].getTypeName());
            }
        }
        return new WmConcatEvaluator((PrimitiveTypeInfo) params[0], (PrimitiveTypeInfo) params[1]);
    }

    public static class WmConcatEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo sepType;
        private final PrimitiveTypeInfo colType;
        private transient PrimitiveObjectInspector sepOI;
        private transient PrimitiveObjectInspector colOI;

        public WmConcatEvaluator(PrimitiveTypeInfo sepType, PrimitiveTypeInfo colType) {
            this.sepType = sepType;
            this.colType = colType;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (m == Mode.PARTIAL1 || m == Mode.COMPLETE) {
                sepOI = (PrimitiveObjectInspector) parameters[0];
                colOI = (PrimitiveObjectInspector) parameters[1];
            }
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
        }

        /** 聚合缓冲：当前拼接文本 + 最近一次使用的分隔符（merge 时用后分片分隔符）。 */
        public static class WmConcatBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            String text;
            String sep = "";
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new WmConcatBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            WmConcatBuffer buf = (WmConcatBuffer) agg;
            buf.text = null;
            buf.sep = "";
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            if (parameters == null || parameters.length < 2) {
                return;
            }
            WmConcatBuffer buf = (WmConcatBuffer) agg;
            Object sepRaw = sepOI.getPrimitiveJavaObject(parameters[0]);
            String sep = sepRaw == null ? "" : String.valueOf(sepRaw);
            buf.sep = sep; // 记录最近一次分隔符（列值分隔符场景下用后出现的）
            Object colRaw = colOI.getPrimitiveJavaObject(parameters[1]);
            if (colRaw == null) {
                return; // NULL 忽略
            }
            String v = String.valueOf(colRaw);
            if (buf.text == null) {
                buf.text = v;
            } else {
                buf.text = buf.text + sep + v;
            }
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            WmConcatBuffer other = (WmConcatBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            WmConcatBuffer buf = (WmConcatBuffer) agg;
            if (buf.text == null) {
                buf.text = other.text;
                buf.sep = other.sep;
            } else if (other.text != null) {
                // ADR-14：A.buf + B.sep + B.buf
                buf.text = buf.text + other.sep + other.text;
                buf.sep = other.sep;
            }
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            WmConcatBuffer buf = (WmConcatBuffer) agg;
            return buf.text; // 全 NULL 或空组时为 null
        }
    }
}
