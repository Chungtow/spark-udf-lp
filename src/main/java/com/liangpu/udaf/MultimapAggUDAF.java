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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UDAF：将两列聚合为 Multimap（Map&lt;k, Array&lt;v&gt;&gt;，对标 MaxCompute multimap_agg）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT deptno, lpudf.multimap_agg(deptno, ename) FROM lpudf.emp GROUP BY deptno;
 * </pre>
 *
 * <p>边界语义：同一 key 的多个值并入其数组；key 为 NULL 的行被忽略；
 * value 为 NULL 时保留进数组；空组返回空 Map。</p>
 *
 * <p>注意：数组内元素顺序为组内处理顺序，分布式场景下不确定。</p>
 */
@ExpressionDescription(
        usage = "multimap_agg(key, value) - 将两列聚合为 Multimap（Map<k, Array<v>>）：key 为第一个参数，value 为第二个参数；NULL key 忽略，NULL value 保留进数组。",
        arguments = "key - 作为 Map key 的列（基础类型），NULL 所在行被忽略\nvalue - 作为数组元素的列（基础类型），NULL 保留进数组")
public class MultimapAggUDAF extends AbstractGenericUDAFResolver {

    /** Spark 2.4 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 2) {
            throw new UDFArgumentTypeException(0,
                    "multimap_agg 需要 2 个参数，实际 " + (params == null ? 0 : params.length));
        }
        for (int i = 0; i < 2; i++) {
            if (params[i].getCategory() != ObjectInspector.Category.PRIMITIVE) {
                throw new UDFArgumentTypeException(i,
                        "multimap_agg 仅支持基础类型参数，参数 " + i + " 的类型为 " + params[i].getTypeName());
            }
        }
        return new MultimapAggEvaluator((PrimitiveTypeInfo) params[0], (PrimitiveTypeInfo) params[1]);
    }

    public static class MultimapAggEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo keyType;
        private final PrimitiveTypeInfo valueType;
        private transient PrimitiveObjectInspector keyOI;
        private transient PrimitiveObjectInspector valueOI;
        private transient ObjectInspector outputOI;

        public MultimapAggEvaluator(PrimitiveTypeInfo keyType, PrimitiveTypeInfo valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (m == Mode.PARTIAL1 || m == Mode.COMPLETE) {
                keyOI = (PrimitiveObjectInspector) parameters[0];
                valueOI = (PrimitiveObjectInspector) parameters[1];
            }
            outputOI = ObjectInspectorFactory.getStandardMapObjectInspector(
                    PrimitiveObjectInspectorFactory
                            .getPrimitiveJavaObjectInspector(keyType.getPrimitiveCategory()),
                    ObjectInspectorFactory.getStandardListObjectInspector(
                            PrimitiveObjectInspectorFactory
                                    .getPrimitiveJavaObjectInspector(valueType.getPrimitiveCategory())));
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return outputOI;
        }

        /** 聚合缓冲：key 到值数组的映射。 */
        public static class MultimapAggBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            final LinkedHashMap<Object, ArrayList<Object>> map =
                    new LinkedHashMap<Object, ArrayList<Object>>();
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new MultimapAggBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            ((MultimapAggBuffer) agg).map.clear();
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            if (parameters == null || parameters.length < 2) {
                return;
            }
            Object key = keyOI.getPrimitiveJavaObject(parameters[0]);
            if (key == null) {
                return; // NULL key 忽略
            }
            Object value = valueOI.getPrimitiveJavaObject(parameters[1]);
            MultimapAggBuffer buf = (MultimapAggBuffer) agg;
            ArrayList<Object> list = buf.map.get(key);
            if (list == null) {
                list = new ArrayList<Object>();
                buf.map.put(key, list);
            }
            list.add(value); // NULL value 保留进数组
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            MultimapAggBuffer other = (MultimapAggBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            MultimapAggBuffer buf = (MultimapAggBuffer) agg;
            for (Map.Entry<Object, ArrayList<Object>> e : other.map.entrySet()) {
                ArrayList<Object> list = buf.map.get(e.getKey());
                if (list == null) {
                    list = new ArrayList<Object>();
                    buf.map.put(e.getKey(), list);
                }
                list.addAll(e.getValue());
            }
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            MultimapAggBuffer buf = (MultimapAggBuffer) agg;
            LinkedHashMap<Object, ArrayList<Object>> copy =
                    new LinkedHashMap<Object, ArrayList<Object>>();
            for (Map.Entry<Object, ArrayList<Object>> e : buf.map.entrySet()) {
                copy.put(e.getKey(), new ArrayList<Object>(e.getValue()));
            }
            return copy;
        }
    }
}
