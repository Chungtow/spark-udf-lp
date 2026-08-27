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
 * UDAF：将两列聚合为 Map（对标 MaxCompute map_agg）。
 *
 * <p>调用：</p>
 * <pre>
 * SELECT lpudf.map_agg(deptno, ename) FROM lpudf.emp;
 * </pre>
 *
 * <p>边界语义：同一 key 出现多次时后出现的值覆盖先出现的值；key 为 NULL 的行被忽略；
 * value 为 NULL 时保留 NULL 作为该 key 的值；空组返回空 Map。</p>
 */
@ExpressionDescription(
        usage = "map_agg(key, value) - 将两列聚合为 Map：key 为第一个参数，value 为第二个参数；重复 key 后者覆盖，NULL key 忽略，NULL value 保留。",
        arguments = "key - 作为 Map key 的列（基础类型），NULL 所在行被忽略\nvalue - 作为 Map value 的列（基础类型），NULL 保留")
public class MapAggUDAF extends AbstractGenericUDAFResolver {

    /** Spark 2.4 HiveUDAFFunction 走 AbstractGenericUDAFResolver.getEvaluator(TypeInfo[]) 路径。 */
    @Override
    public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters) throws SemanticException {
        return buildEvaluator(parameters);
    }

    private GenericUDAFEvaluator buildEvaluator(TypeInfo[] params) throws SemanticException {
        if (params == null || params.length != 2) {
            throw new UDFArgumentTypeException(0,
                    "map_agg 需要 2 个参数，实际 " + (params == null ? 0 : params.length));
        }
        for (int i = 0; i < 2; i++) {
            if (params[i].getCategory() != ObjectInspector.Category.PRIMITIVE) {
                throw new UDFArgumentTypeException(i,
                        "map_agg 仅支持基础类型参数，参数 " + i + " 的类型为 " + params[i].getTypeName());
            }
        }
        return new MapAggEvaluator((PrimitiveTypeInfo) params[0], (PrimitiveTypeInfo) params[1]);
    }

    public static class MapAggEvaluator extends GenericUDAFEvaluator {

        private final PrimitiveTypeInfo keyType;
        private final PrimitiveTypeInfo valueType;
        private transient PrimitiveObjectInspector keyOI;
        private transient PrimitiveObjectInspector valueOI;
        private transient ObjectInspector outputOI;

        public MapAggEvaluator(PrimitiveTypeInfo keyType, PrimitiveTypeInfo valueType) {
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
                    PrimitiveObjectInspectorFactory
                            .getPrimitiveJavaObjectInspector(valueType.getPrimitiveCategory()));
            if (m == Mode.PARTIAL1 || m == Mode.PARTIAL2) {
                return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
            }
            return outputOI;
        }

        /** 聚合缓冲：key 到 value 的映射（重复 key 后者覆盖）。 */
        public static class MapAggBuffer implements AggregationBuffer, Serializable {
            private static final long serialVersionUID = 1L;
            final LinkedHashMap<Object, Object> map = new LinkedHashMap<Object, Object>();
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() {
            return new MapAggBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) {
            ((MapAggBuffer) agg).map.clear();
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
            ((MapAggBuffer) agg).map.put(key, value);
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            return UdafSerialization.partialOfText(agg);
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            MapAggBuffer other = (MapAggBuffer) UdafSerialization.fromTextPartial(partial);
            if (other == null) {
                return;
            }
            ((MapAggBuffer) agg).map.putAll(other.map);
        }

        @Override
        public Object terminate(AggregationBuffer agg) {
            MapAggBuffer buf = (MapAggBuffer) agg;
            return new LinkedHashMap<Object, Object>(buf.map);
        }
    }
}
